package org.reactivecouchbase.webstack.actions

import java.net.InetSocketAddress

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import io.undertow.server.HttpServerExchange
import org.reactivecouchbase.webstack.env.EnvLike
import org.reactivecouchbase.webstack.mvc.{Cookie, Session}
import org.reactivestreams.Publisher
import play.api.libs.json.{JsValue, Json}

import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try
import scala.xml.{Elem, XML}

case class RequestQueryParams(private val request: HttpServerExchange) {
  lazy val raw: Map[String, Seq[String]] = {
    Option(request.getQueryParameters).map(_.toMap.mapValues(e => Seq.empty ++ e)).getOrElse(Map.empty[String, Seq[String]])
  }
  def simpleParams: Map[String, String] = raw.mapValues(_.head)
  def paramsNames: Seq[String] = raw.keys.toSeq
  def params(name: String): Seq[String] = raw.getOrElse(name, Seq.empty)
  def param(name: String): Option[String] = raw.get(name).flatMap(_.headOption)
}

case class RequestPathParams(private val request: HttpServerExchange) {
  lazy val raw: Map[String, String] = {
    Option(request.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY))
      .map(m => m.getParameters.toMap)
      .getOrElse(Map.empty[String, String])
  }
  def paramNames: Seq[String] = raw.keys.toSeq
  def param(name: String): Option[String] = raw.get(name)
}

case class RequestHeaders(private val request: HttpServerExchange) {
  lazy val raw: Map[String, Seq[String]] = {
    Option(request.getRequestHeaders)
      .map(hds => hds.getHeaderNames.map(_.toString).map(n => (n, hds.get(n).toIndexedSeq)).toMap)
      .getOrElse(Map.empty[String, Seq[String]])
  }
  def header(name: String): Option[String] = raw.get(name).flatMap(_.headOption)
  def simpleHeaders: Map[String, String] = raw.mapValues(_.head)
  def headerNames: Seq[String] = raw.keys.toSeq
}

case class RequestCookies(private val request: HttpServerExchange) {
  lazy val raw: Map[String, Cookie] = {
    Option.apply(request.getRequestCookies).map(_.toMap.mapValues(Cookie.apply)).getOrElse(Map.empty[String, Cookie])
  }
  def cookieNames: Seq[String] = raw.keys.toSeq
  def cookie(name: String): Option[Cookie] = raw.get(name)
}

case class RequestBody(bytes: ByteString) {
  lazy val string = bytes.utf8String
  lazy val json: JsValue = safeJson.get
  lazy val safeJson: Try[JsValue] = Try(Json.parse(string))
  lazy val xml: Elem = safeXml.get
  lazy val safeXml: Try[Elem] = Try(XML.loadString(string))
  lazy val safeUrlFrom: Try[Map[String, List[String]]] = Try {
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
  lazy val urlForm: Map[String, List[String]] = safeUrlFrom.get
}

@implicitNotFound("Cannot find instance of RequestContext. Try define it as implicit, like `Action.async { implicit ctx => ??? }`")
case class RequestContext(private val state: Map[String, AnyRef], exchange: HttpServerExchange, env: EnvLike, currentExecutionContext: ExecutionContext) {

  lazy val headers = RequestHeaders(exchange)
  lazy val queryParams = RequestQueryParams(exchange)
  lazy val cookies = RequestCookies(exchange)
  lazy val pathParams = RequestPathParams(exchange)
  lazy val configuration = env.configuration
  lazy val session = cookies.cookie(env.sessionConfig.cookieName).flatMap(Session.fromCookie).getOrElse(Session())

  def uri: String = exchange.getRequestURI
  def method: String = exchange.getRequestMethod.toString
  def charset: String = exchange.getRequestCharset
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
  def header(name: String): Option[String] = Option(exchange.getRequestHeaders.getFirst(name))
  def queryParam(name: String): Option[String] = queryParams.param(name)
  def cookie(name: String): Option[Cookie] = cookies.cookie(name)
  def pathParam(name: String): Option[String] = pathParams.param(name)
  def getValue[T](key: String)(implicit ct: ClassTag[T]): Option[T] = state.get(key).flatMap(ct.unapply)
  def setValue(key: String, value: AnyRef): RequestContext = {
    if (key == null || value == null) this
    else RequestContext(state + (key -> value), exchange, env, currentExecutionContext)
  }

  def body(implicit ec: ExecutionContext, materializer: Materializer): Future[RequestBody] = {
    bodyAsStream.runFold(ByteString.empty)(_.concat(_)).map(RequestBody.apply)
  }

  def body[T](bodyParser: (RequestHeaders, Source[ByteString, Any]) => Future[T]): Future[T] = bodyParser.apply(headers, bodyAsStream)

  def body[T](bodyParser: (RequestHeaders, Publisher[ByteString]) => Future[T])(implicit materializer: Materializer): Future[T] = {
    bodyParser.apply(headers, bodyAsPublisher())
  }

  lazy val rawBodyAsStream: Source[ByteString, Any] = {
    // TODO : avoid using blocking input stream here here
    StreamConverters.fromInputStream(() => {
      exchange.startBlocking()
      exchange.getInputStream
    })
  }

  def bodyAsStream: Source[ByteString, Any] = {
    header("Content-Encoding") match {
      case Some("gzip") => rawBodyAsStream.via(akka.stream.scaladsl.Compression.gunzip())
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
