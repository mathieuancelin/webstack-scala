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

object HttpClient {

  def apply(protocol: String, host: String, port: Int = 80)(implicit env: EnvLike = Env): HttpClientFinal = {
    if (protocol.equalsIgnoreCase("https")) {
      val connectionFlow = env.wsHttp.outgoingConnectionHttps(host, port)
      HttpClientFinal(connectionFlow, protocol, host, port)
    } else {
      val connectionFlow = env.wsHttp.outgoingConnection(host, port)
      HttpClientFinal(connectionFlow, protocol, host, port)
    }
  }
}

// TODO : rename
case class HttpClientFinal(
  private val connectionFlow: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]],
  protocol: String,
  host: String,
  port: Int,
  path: String = "/",
  method: HttpMethod = HttpMethods.GET,
  body: Source[ByteString, _] = Source.empty[ByteString],
  contentType: ContentType = ContentTypes.`text/plain(UTF-8)`,
  headers: Map[String, Seq[String]] = Map.empty[String, Seq[String]],
  queryParams: Map[String, Seq[String]] = Map.empty[String, Seq[String]]
) {

  def withPath(path: String): HttpClientFinal = copy(path = path)
  def addPathSegment(segment: String): HttpClientFinal = copy(path = s"$path/$segment")
  def addPathSegment(path: Any): HttpClientFinal = addPathSegment(path.toString)
  def withMethod(method: HttpMethod): HttpClientFinal = copy(method = method)
  def withMethod(method: String): HttpClientFinal = copy(method = HttpMethods.getForKey(method).get)
  def withBody(body: Publisher[ByteString]): HttpClientFinal = copy(body = Source.fromPublisher(body))
  def withBody(body: Source[ByteString, _]): HttpClientFinal = copy(body = body)
  def withBody(body: Publisher[ByteString], ctype: ContentType): HttpClientFinal = copy(body = Source.fromPublisher(body), contentType = ctype)
  def withBody(body: Source[ByteString, _], ctype: ContentType): HttpClientFinal = copy(body = body, contentType = ctype)

  def withBody(body: JsValue): HttpClientFinal = {
    val source: Source[ByteString, _] = StreamUtils.stringToSource(Json.stringify(body))
    copy(body = source, contentType = ContentTypes.`application/json`)
  }

  def withBody(body: String): HttpClientFinal = {
    copy(body = StreamUtils.stringToSource(body), contentType = ContentTypes.`text/plain(UTF-8)`)
  }

  def withBody(body: String, ctype: ContentType): HttpClientFinal = {
    copy(body = StreamUtils.stringToSource(body), contentType = ctype)
  }

  def withBody(body: ByteString): HttpClientFinal = {
    copy(body = Source.single(body), contentType = ContentTypes.`application/octet-stream`)
  }

  def withBody(body: ByteString, ctype: ContentType): HttpClientFinal = {
    copy(body = Source.single(body), contentType = ctype)
  }

  def withBody(body: Array[Byte]): HttpClientFinal = {
    copy(body = StreamUtils.bytesToSource(body), contentType = ContentTypes.`text/plain(UTF-8)`)
  }

  def withBody(body: Array[Byte], ctype: ContentType): HttpClientFinal = {
    copy(body = StreamUtils.bytesToSource(body), contentType = ctype)
  }

  def withBody(body: InputStream): HttpClientFinal = {
    copy(body = StreamConverters.fromInputStream(() => body), contentType = ContentTypes.`application/octet-stream`)
  }

  def withBody(body: InputStream, ctype: ContentType): HttpClientFinal = {
    copy(body = StreamConverters.fromInputStream(() => body), contentType = ctype)
  }

  def withBody(body: Elem): HttpClientFinal = {
    val source = StreamUtils.stringToSource(new scala.xml.PrettyPrinter(80, 2).format(body))
    copy(body = source, contentType = ContentType.parse("application/xml").right.get)
  }

  def withBody(body: Elem, ctype: ContentType): HttpClientFinal = {
    val source = StreamUtils.stringToSource(new scala.xml.PrettyPrinter(80, 2).format(body))
    copy(body = source, contentType = ctype)
  }

  def withSerializableBody[A](body: A)(implicit canSerialize: CanSerialize[A]): HttpClientFinal = {
    copy(contentType = ContentType.parse(canSerialize.contentType).right.get, body = Source.single(canSerialize.serialize(body)))
  }

  def withHeaders(headers: Map[String, Seq[String]]): HttpClientFinal = copy(headers = headers)

  def withHeader(header: (String, String)): HttpClientFinal = {
    val (name, value) = header
    val values = headers.get(name) match {
      case Some(vals) => vals :+ value
      case None => Seq(value)
    }
    copy(headers = headers + (name -> values))
  }

  def withQueryParams(queryString: Map[String, Seq[String]]): HttpClientFinal = copy(queryParams = queryString)

  def withQueryParam(qparam: (String, Any)): HttpClientFinal = {
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

object WebSocketClient {
  def apply(protocol: String, host: String, port: Int = 80)(implicit env: EnvLike = Env): WebSocketClientFinal = WebSocketClientFinal(env, protocol, host, port)
}

// TODO : rename
case class WebSocketClientFinal(
  private val env: EnvLike,
  protocol: String,
  host: String,
  port: Int,
  path: String = "/",
  headers: Map[String, Seq[String]] = Map.empty[String, Seq[String]],
  queryParams: Map[String, Seq[String]] = Map.empty[String, Seq[String]]
) {

  def withPath(path: String): WebSocketClientFinal = copy(path = path)

  def withHeaders(headers: Map[String, Seq[String]]): WebSocketClientFinal = copy(headers = headers)

  def withHeader(header: (String, String)): WebSocketClientFinal = {
    val (name, value) = header
    headers.get(name) match {
      case Some(vals) => copy(headers = headers + (name -> (vals :+ value)))
      case None => copy(headers = headers + (name -> Seq(value)))
    }
  }

  def withQueryParams(queryParams: Map[String, Seq[String]]): WebSocketClientFinal = copy(queryParams = queryParams)

  def withQueryParam(qparam: (String, Any)): WebSocketClientFinal = {
    val (name, value) = qparam
    queryParams.get(name) match {
      case Some(vals) => copy(queryParams = queryParams + (name -> (vals :+ value.toString)))
      case None => copy(queryParams = queryParams + (name -> Seq(value.toString)))
    }
  }

  def addPathSegment(value: String): WebSocketClientFinal = copy(path = s"$path/$value")

  def addPathSegment(value: Any): WebSocketClientFinal = copy(path = s"$path/${value.toString}")

  def callNoMat(flow: Processor[Message, Message])(implicit executionContext: ExecutionContext, materializer: Materializer): Future[WebSocketUpgradeResponse] = {
    callNoMat(Flow.fromProcessor(() => flow))
  }

  def call[T](flow: Processor[Message, Message], materialized: T)(implicit executionContext: ExecutionContext, materializer: Materializer): WebSocketConnections[T] = {
    call(Flow.fromProcessorMat(() => (flow, materialized)))
  }

  def call[T](flow: Flow[Message, Message, T])(implicit executionContext: ExecutionContext, materializer: Materializer): WebSocketConnections[T] = {
    val _queryString = queryParams.toList.flatMap(tuple => tuple._2.map(v => tuple._1 + "=" + v)).mkString("&")
    val _headers = headers.toList.flatMap(tuple => tuple._2.map(v => RawHeader(tuple._1, v)))
    val url: String = protocol + "://" + host + ":" + port + path.replace("//", "/") + (if (queryParams.isEmpty) "" else "?" + _queryString)
    val request = _headers.foldLeft[WebSocketRequest](WebSocketRequest(url))((r, header) => r.copy(extraHeaders = r.extraHeaders :+ header))
    val (connected, closed) = env.websocketHttp.singleWebSocketRequest(request, flow)
    WebSocketConnections[T](connected, closed)
  }

  def callNoMat(flow: Flow[Message, Message, _])(implicit executionContext: ExecutionContext, materializer: Materializer): Future[WebSocketUpgradeResponse] = {
    val _queryString = queryParams.toList.flatMap(tuple => tuple._2.map(v => tuple._1 + "=" + v)).mkString("&")
    val _headers = headers.toList.flatMap(tuple => tuple._2.map(v => RawHeader(tuple._1, v)))
    val url = protocol + "://" + host + ":" + port + path.replace("//", "/") + (if (queryParams.isEmpty) "" else "?" + _queryString)
    val request = _headers.foldLeft[WebSocketRequest](WebSocketRequest(url))((r, header) => r.copy(extraHeaders = r.extraHeaders :+ header))
    val (connected, _) = env.websocketHttp.singleWebSocketRequest(request, flow)
    connected
  }
}