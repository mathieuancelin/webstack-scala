package org.reactivecouchbase.webstack.actions

import java.net.InetSocketAddress

import akka.http.scaladsl.coding.Gzip
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.Cookie
import org.reactivecouchbase.webstack.env.Env
import org.reactivestreams.Publisher
import org.w3c.dom.Node
import play.api.libs.json.{JsValue, Json}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try
import scala.xml.Elem

case class RequestQueryParams(private val request: HttpServerExchange) {
  private val queryParams = Option(request.getQueryParameters).map(_.toMap.mapValues(e => Seq.empty ++ e)).getOrElse(Map.empty[String, Seq[String]])
  def raw: Map[String, Seq[String]] = queryParams
  def simpleParams: Map[String, String] = queryParams.mapValues(_.head)
  def paramsNames: Seq[String] = queryParams.keys.toSeq
  def params(name: String): Seq[String] = queryParams.getOrElse(name, Seq.empty)
  def param(name: String): Option[String] = queryParams.get(name).flatMap(_.headOption)
}

case class RequestPathParams(private val request: HttpServerExchange) {
  private val pathParams = Option(request.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY))
    .map(m => m.getParameters.toMap)
    .getOrElse(Map.empty[String, String])
  def raw: Map[String, String] = pathParams
  def paramNames: Seq[String] = pathParams.keys.toSeq
  def param(name: String): Option[String] = pathParams.get(name)
}

case class RequestHeaders(private val request: HttpServerExchange) {
  private val headers = Option(request.getRequestHeaders)
      .map(hds => hds.getHeaderNames.map(_.toString).map(n => (n, hds.get(n).toIndexedSeq)).toMap)
      .getOrElse(Map.empty[String, Seq[String]])
  def header(name: String): Option[String] = headers.get(name).flatMap(_.headOption)
  def simpleHeaders: Map[String, String] = headers.mapValues(_.head)
  def headerNames: Seq[String] = headers.keys.toSeq
  def raw: Map[String, Seq[String]] = headers
}

class RequestCookies private[actions](val request: HttpServerExchange) {
  private val cookies: Map[String, Cookie] = Option.apply(request.getRequestCookies).map(_.toMap).getOrElse(Map.empty[String, Cookie])
  def raw: Map[String, Cookie] = cookies
  def cookieNames: Seq[String] = cookies.keys.toSeq
  def cookie(name: String): Option[Cookie] = cookies.get(name)
}

case class RequestContext(private val state: Map[String, AnyRef], private val underlying: HttpServerExchange, private val ec: ExecutionContext) {

  val headers = new RequestHeaders(underlying)
  val queryParams = new RequestQueryParams(underlying)
  val cookies = new RequestCookies(underlying)
  val pathParams = new RequestPathParams(underlying)
  val configuration = Env.configuration

  def currentExecutor: ExecutionContext = ec
  def getValue(key: String): Option[AnyRef] = state.get(key)
  def getValue[T](key: String)(implicit ct: ClassTag[T]): Option[T] = state.get(key).flatMap(ct.unapply)

  def setValue(key: String, value: AnyRef): RequestContext = {
    if (key == null || value == null) this
    else RequestContext(state + (key -> value), underlying, ec)
  }

  def uri: String = exchange.getRequestURI
  def method: String = exchange.getRequestMethod.toString
  def chartset: String = exchange.getRequestCharset
  def contentLength: Long = exchange.getRequestContentLength
  def path: String = exchange.getRequestPath
  def scheme: String = exchange.getRequestScheme
  def startTime: Long = exchange.getRequestStartTime
  def url: String = exchange.getRequestURL
  def hostAndPort: String = exchange.getHostAndPort
  def hostName: String = exchange.getHostName
  def port: Int = exchange.getHostPort
  def protocol: String = exchange.getProtocol.toString
  def queryString: String = exchange.getQueryString
  def relativePath: String = exchange.getRelativePath
  def sourceAddress: InetSocketAddress = exchange.getSourceAddress
  def status: Int = exchange.getStatusCode
  def exchange: HttpServerExchange = underlying

  // Env.blockingExecutor, Env.blockingActorMaterializer
  def body(implicit ec: ExecutionContext, materializer: Materializer): Future[RequestBody] = {
    bodyAsStream.runFold(ByteString.empty)(_.concat(_)).map(RequestBody.apply)
  }

  def body[T](bodyParser: (RequestHeaders, Source[ByteString, Any]) => Future[T]): Future[T] = bodyParser.apply(headers, bodyAsStream)

  def body[T](bodyParser: (RequestHeaders, Publisher[ByteString]) => Future[T])(implicit materializer: Materializer): Future[T] = {
    bodyParser.apply(headers, bodyAsPublisher())
  }

  def bodyAsStream: Source[ByteString, Any] = {
    if (header("Content-Encoding").getOrElse("none").equalsIgnoreCase("gzip")) {
      rawBodyAsStream.via(Gzip.decoderFlow)
    } else {
      rawBodyAsStream
    }
  }

  def rawBodyAsStream: Source[ByteString, Any] = {
    // TODO : avoid blocking here
    StreamConverters.fromInputStream(() => {
      underlying.startBlocking()
      underlying.getInputStream
    })
  }

  def bodyAsPublisher(fanout: Boolean = false)(implicit materializer: Materializer): Publisher[ByteString] = {
    bodyAsStream.runWith(Sink.asPublisher(fanout))
  }

  def rawBodyAsPublisher(fanout: Boolean = false)(implicit materializer: Materializer): Publisher[ByteString] = {
    rawBodyAsStream.runWith(Sink.asPublisher(fanout))
  }

  def header(name: String): Option[String] = Option(exchange.getRequestHeaders.getFirst(name))
  def queryParam(name: String): Option[String] = queryParams.param(name)
  def cookie(name: String): Option[Cookie] = cookies.cookie(name)
  def pathParam(name: String): Option[String] = pathParams.param(name)
}

case class RequestBody(bodyAsBytes: ByteString) {
  val bodyAsString = bodyAsBytes.utf8String
  def bytes: ByteString = bodyAsBytes
  def string: String = bodyAsString
  def json: JsValue = Json.parse(string)
  def safeJson: Try[JsValue] = Try(Json.parse(string))
  def xml: Elem = scala.xml.XML.loadString(string)
  def safeXml: Try[Elem] = Try(scala.xml.XML.loadString(string))
  def safeUrlFrom: Try[Map[String, List[String]]] = Try(urlForm)
  def urlForm: Map[String, List[String]] = {
    var form = Map.empty[String, List[String]]
    val body: String = string
    val parts: Seq[String] = body.split("&").toSeq
    parts.foreach { part =>
      val key: String = part.split("=")(0)
      val value: String = part.split("=")(1)
      if (!form.containsKey(key)) {
        form = form + (key -> List.empty)
      }
      form = form + (key -> (form.get(key).get :+ value))
    }
    form
  }
}
