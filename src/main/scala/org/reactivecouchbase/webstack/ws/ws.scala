package org.reactivecouchbase.webstack.ws

import java.io.InputStream

import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source, StreamConverters}
import akka.util.ByteString
import org.reactivecouchbase.webstack.StreamUtils
import org.reactivecouchbase.webstack.env.{Env, EnvLike}
import org.reactivecouchbase.webstack.result.serialize.CanSerialize
import org.reactivestreams.{Processor, Publisher}
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.xml.{Elem, XML}

object WS {

  def host(host: String, _port: Int = 80)(implicit env: EnvLike = Env): WSRequest = {
    val port = Option(host).map(_.replace("http://", "").replace("https://", "")).filter(_.contains(":")).map(_.split(":")(1).toInt).getOrElse(_port)
    if (host.startsWith("https")) {
      val connectionFlow = env.wsHttp.outgoingConnectionHttps(host.replace(s":$port", "").replace("https://", ""), port)
      WSRequest(connectionFlow, host, port)
    } else {
      val connectionFlow = env.wsHttp.outgoingConnection(host.replace(s":$port", "").replace("http://", ""), port)
      WSRequest(connectionFlow, host, port)
    }
  }

  def webSocketHost(host: String)(implicit env: EnvLike = Env): WebSocketClientRequest = WebSocketClientRequest(env, host, "")
}

case class WSBody(bytes: ByteString) {
  lazy val string = bytes.utf8String
  lazy val json: JsValue = safeJson.get
  lazy val safeJson: Try[JsValue] = Try(Json.parse(string))
  lazy val safeXml: Try[Elem] = Try(XML.loadString(string))
  lazy val xml: Elem = safeXml.get
}

case class WSResponse(underlying: HttpResponse) {

  lazy val headers: Map[String, Seq[String]] = {
    var _headers = Map.empty[String, Seq[String]]
    import scala.collection.JavaConversions._
    for (header <- underlying.getHeaders) {
      if (!_headers.containsKey(header.name)) {
        _headers = _headers + (header.name -> Seq.empty[String])
      }
      _headers = _headers + (header.name -> (_headers.get(header.name).get :+ header.value))
    }
    _headers + ("Content-Type" -> Seq(underlying.entity.getContentType.mediaType.toString))
  }

  def status: Int = underlying.status.intValue
  def statusText: String = underlying.status.defaultMessage
  def header(name: String): Option[String] = headers.get(name).flatMap(_.headOption)
  def body(implicit ec: ExecutionContext, materializer: Materializer): Future[WSBody] = {
    bodyAsStream.runFold(ByteString.empty)(_.concat(_)).map(WSBody.apply)
  }

  def rawBodyAsStream: Source[ByteString, _] = underlying.entity.dataBytes

  def bodyAsStream: Source[ByteString, _] = {
    header("Content-Encoding") match {
      case Some("gzip") => rawBodyAsStream.via(Gzip.decoderFlow)
      case _ => rawBodyAsStream
    }
  }

  def bodyAsPublisher(fanout: Boolean = false)(implicit materializer: Materializer): Publisher[ByteString] = {
    bodyAsStream.runWith(Sink.asPublisher(fanout))
  }

  def rawBodyAsPublisher(fanout: Boolean = false)(implicit materializer: Materializer): Publisher[ByteString] = {
    rawBodyAsStream.runWith(Sink.asPublisher(fanout))
  }
}

case class WSRequest(
  private val connectionFlow: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]],
  host: String,
  port: Int,
  path: String = "",
  method: HttpMethod = HttpMethods.GET,
  body: Source[ByteString, _] = Source.empty[ByteString],
  contentType: ContentType = ContentTypes.`text/plain(UTF-8)`,
  headers: Map[String, Seq[String]] = Map.empty[String, Seq[String]],
  queryParams: Map[String, Seq[String]] = Map.empty[String, Seq[String]]
) {

  def withPath(path: String): WSRequest = copy(path = path)
  def addPathSegment(segment: String): WSRequest = copy(path = s"$path/$segment")
  def addPathSegment(path: Any): WSRequest = addPathSegment(path.toString)
  def withMethod(method: HttpMethod): WSRequest = copy(method = method)
  def withMethod(method: String): WSRequest = copy(method = HttpMethods.getForKey(method).get)
  def withBody(body: Publisher[ByteString]): WSRequest = copy(body = Source.fromPublisher(body))
  def withBody(body: Source[ByteString, _]): WSRequest = copy(body = body)
  def withBody(body: Publisher[ByteString], ctype: ContentType): WSRequest = copy(body = Source.fromPublisher(body), contentType = ctype)
  def withBody(body: Source[ByteString, _], ctype: ContentType): WSRequest = copy(body = body, contentType = ctype)

  def withBody(body: JsValue): WSRequest = {
    val source: Source[ByteString, _] = StreamUtils.stringToSource(Json.stringify(body))
    copy(body = source, contentType = ContentTypes.`application/json`)
  }

  def withBody(body: String): WSRequest = {
    copy(body = StreamUtils.stringToSource(body), contentType = ContentTypes.`text/plain(UTF-8)`)
  }

  def withBody(body: String, ctype: ContentType): WSRequest = {
    copy(body = StreamUtils.stringToSource(body), contentType = ctype)
  }

  def withBody(body: ByteString): WSRequest = {
    copy(body = Source.single(body), contentType = ContentTypes.`application/octet-stream`)
  }

  def withBody(body: ByteString, ctype: ContentType): WSRequest = {
    copy(body = Source.single(body), contentType = ctype)
  }

  def withBody(body: Array[Byte]): WSRequest = {
    copy(body = StreamUtils.bytesToSource(body), contentType = ContentTypes.`text/plain(UTF-8)`)
  }

  def withBody(body: Array[Byte], ctype: ContentType): WSRequest = {
    copy(body = StreamUtils.bytesToSource(body), contentType = ctype)
  }

  def withBody(body: InputStream): WSRequest = {
    copy(body = StreamConverters.fromInputStream(() => body), contentType = ContentTypes.`application/octet-stream`)
  }

  def withBody(body: InputStream, ctype: ContentType): WSRequest = {
    copy(body = StreamConverters.fromInputStream(() => body), contentType = ctype)
  }

  def withBody(body: Elem): WSRequest = {
    val source = StreamUtils.stringToSource(new scala.xml.PrettyPrinter(80, 2).format(body))
    copy(body = source, contentType = ContentType.parse("application/xml").right.get)
  }

  def withBody(body: Elem, ctype: ContentType): WSRequest = {
    val source = StreamUtils.stringToSource(new scala.xml.PrettyPrinter(80, 2).format(body))
    copy(body = source, contentType = ctype)
  }

  def withSerializableBody[A](body: A)(implicit canSerialize: CanSerialize[A]): WSRequest = {
    copy(contentType = ContentType.parse(canSerialize.contentType).right.get, body = Source.single(canSerialize.serialize(body)))
  }

  def withHeaders(headers: Map[String, Seq[String]]): WSRequest = copy(headers = headers)

  def withHeader(header: (String, String)): WSRequest = {
    val (name, value) = header
    val values = headers.get(name) match {
      case Some(vals) => vals :+ value
      case None => Seq(value)
    }
    copy(headers = headers + (name -> values))
  }

  def withQueryParams(queryString: Map[String, Seq[String]]): WSRequest = copy(queryParams = queryString)

  def withQueryParam(qparam: (String, Any)): WSRequest = {
    val (name, value) = qparam
    val values = queryParams.get(name) match {
      case Some(vals) => vals :+ value.toString
      case None => Seq(value.toString)
    }
    copy(queryParams = queryParams + (name -> values))
  }

  def call()(implicit ec: ExecutionContext, materializer: Materializer): Future[WSResponse] = {
    val _queryString = queryParams.toSeq.flatMap(tuple => tuple._2.map(v => tuple._1 + "=" + v)).mkString("&")
    val qstr = if (queryParams.isEmpty) "" else s"?${_queryString}"
    val _headers = headers.toSeq.flatMap(tuple => tuple._2.map(v => HttpHeader.parse(tuple._1, v))).collect { case Ok(h, _) => h }
    val request: HttpRequest = HttpRequest(
      method = method,
      uri = Uri(path.replace("//", "/") + qstr),
      headers = collection.immutable.Seq.concat(_headers),
      entity = HttpEntity(contentType, body)
    )
    val responseFuture = Source.single(request).via(connectionFlow).runWith(Sink.head)
    responseFuture.map(WSResponse.apply)
  }

}

case class WebSocketConnections[T](response: Future[WebSocketUpgradeResponse], materialized: T)

case class WebSocketClientRequest(
  private val env: EnvLike,
  host: String,
  path: String,
  headers: Map[String, Seq[String]] = Map.empty[String, Seq[String]],
  queryParams: Map[String, Seq[String]] = Map.empty[String, Seq[String]]
) {

  def withPath(path: String): WebSocketClientRequest = copy(path = path)

  def withHeaders(headers: Map[String, Seq[String]]): WebSocketClientRequest = copy(headers = headers)

  def withHeader(header: (String, String)): WebSocketClientRequest = {
    val (name, value) = header
    headers.get(name) match {
      case Some(vals) => copy(headers = headers + (name -> (vals :+ value)))
      case None => copy(headers = headers + (name -> Seq(value)))
    }
  }

  def withQueryParams(queryParams: Map[String, Seq[String]]): WebSocketClientRequest = copy(queryParams = queryParams)

  def withQueryParam(qparam: (String, Any)): WebSocketClientRequest = {
    val (name, value) = qparam
    queryParams.get(name) match {
      case Some(vals) => copy(queryParams = queryParams + (name -> (vals :+ value.toString)))
      case None => copy(queryParams = queryParams + (name -> Seq(value.toString)))
    }
  }

  def addPathSegment(value: String): WebSocketClientRequest = copy(path = s"$path/$value")

  def addPathSegment(value: Any): WebSocketClientRequest = copy(path = s"$path/${value.toString}")

  def callNoMat(flow: Processor[Message, Message])(implicit executionContext: ExecutionContext, materializer: Materializer): Future[WebSocketUpgradeResponse] = {
    callNoMat(Flow.fromProcessor(() => flow))
  }

  def call[T](flow: Processor[Message, Message], materialized: T)(implicit executionContext: ExecutionContext, materializer: Materializer): WebSocketConnections[T] = {
    call(Flow.fromProcessorMat(() => (flow, materialized)))
  }

  def call[T](flow: Flow[Message, Message, T])(implicit executionContext: ExecutionContext, materializer: Materializer): WebSocketConnections[T] = {
    val _queryString = queryParams.toList.flatMap(tuple => tuple._2.map(v => tuple._1 + "=" + v)).mkString("&")
    val _headers = headers.toList.flatMap(tuple => tuple._2.map(v => RawHeader(tuple._1, v)))
    val url: String = host + path.replace("//", "/") + (if (queryParams.isEmpty) "" else "?" + _queryString)
    val request = _headers.foldLeft[WebSocketRequest](WebSocketRequest(url))((r, header) => r.copy(extraHeaders = r.extraHeaders :+ header))
    val (connected, closed) = env.websocketHttp.singleWebSocketRequest(request, flow)
    WebSocketConnections[T](connected, closed)
  }

  def callNoMat(flow: Flow[Message, Message, _])(implicit executionContext: ExecutionContext, materializer: Materializer): Future[WebSocketUpgradeResponse] = {
    val _queryString = queryParams.toList.flatMap(tuple => tuple._2.map(v => tuple._1 + "=" + v)).mkString("&")
    val _headers = headers.toList.flatMap(tuple => tuple._2.map(v => RawHeader(tuple._1, v)))
    val url = host + path.replace("//", "/") + (if (queryParams.isEmpty) "" else "?" + _queryString)
    val request = _headers.foldLeft[WebSocketRequest](WebSocketRequest(url))((r, header) => r.copy(extraHeaders = r.extraHeaders :+ header))
    val (connected, _) = env.websocketHttp.singleWebSocketRequest(request, flow)
    connected
  }
}