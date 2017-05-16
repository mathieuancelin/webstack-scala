package org.reactivecouchbase.webstack.result

import java.io.{File, InputStream}
import java.nio.file.Path

import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.ByteString
import com.github.jknack.handlebars.Context
import org.reactivecouchbase.webstack.actions.RequestContext
import org.reactivecouchbase.webstack.env.{Env, EnvLike}
import org.reactivecouchbase.webstack.mvc.{Cookie, Session}
import org.reactivecouchbase.webstack.result.serialize.CanSerialize
import org.reactivecouchbase.webstack.{ReverseRoute, StreamUtils}
import org.reactivestreams.Publisher
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.xml.Elem

trait Results {
  val Continue = Result(HttpStatus.CONTINUE.value)
  val SwitchingProtocols = Result(HttpStatus.SWITCHING_PROTOCOLS.value)
  val Ok = Result(HttpStatus.OK.value)
  val Created = Result(HttpStatus.CREATED.value)
  val Accepted = Result(HttpStatus.ACCEPTED.value)
  val NonAuthoritativeInformation = Result(HttpStatus.NON_AUTHORITATIVE_INFORMATION.value)
  val NoContent = Result(HttpStatus.NO_CONTENT.value)
  val ResetContent = Result(HttpStatus.RESET_CONTENT.value)
  val PartialContent = Result(HttpStatus.PARTIAL_CONTENT.value)
  val MultiStatus = Result(HttpStatus.MULTI_STATUS.value)

  def MovedPermanently(url: String) = {
    Result(HttpStatus.MOVED_PERMANENTLY.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def MovedPermanently(route: ReverseRoute, pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any]) = {
    Result(HttpStatus.MOVED_PERMANENTLY.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(route.url(pathParams, queryParams)))), Seq.empty[Cookie])
  }

  def Found(url: String) = {
    Result(HttpStatus.FOUND.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def Found(route: ReverseRoute, pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any]) = {
    Result(HttpStatus.FOUND.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(route.url(pathParams, queryParams)))), Seq.empty[Cookie])
  }

  def SeeOther(url: String) = {
    Result(HttpStatus.SEE_OTHER.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def SeeOther(route: ReverseRoute, pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any]) = {
    Result(HttpStatus.SEE_OTHER.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(route.url(pathParams, queryParams)))), Seq.empty[Cookie])
  }

  val NotModified = Result(HttpStatus.NOT_MODIFIED.value)

  def TemporaryRedirect(url: String) = {
    Result(HttpStatus.TEMPORARY_REDIRECT.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def TemporaryRedirect(route: ReverseRoute, pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any]) = {
    Result(HttpStatus.TEMPORARY_REDIRECT.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(route.url(pathParams, queryParams)))), Seq.empty[Cookie])
  }

  def PermanentRedirect(url: String) = {
    Result(HttpStatus.PERMANENT_REDIRECT.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def PermanentRedirect(route: ReverseRoute, pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any]) = {
    Result(HttpStatus.PERMANENT_REDIRECT.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(route.url(pathParams, queryParams)))), Seq.empty[Cookie])
  }

  val BadRequest = Result(HttpStatus.BAD_REQUEST.value)
  val Unauthorized = Result(HttpStatus.UNAUTHORIZED.value)
  val PaymentRequired = Result(HttpStatus.PAYMENT_REQUIRED.value)
  val Forbidden = Result(HttpStatus.FORBIDDEN.value)
  val NotFound = Result(HttpStatus.NOT_FOUND.value)
  val MethodNotAllowed = Result(HttpStatus.METHOD_NOT_ALLOWED.value)
  val NotAcceptable = Result(HttpStatus.NOT_ACCEPTABLE.value)
  val RequestTimeout = Result(HttpStatus.REQUEST_TIMEOUT.value)
  val Conflict = Result(HttpStatus.CONFLICT.value)
  val Gone = Result(HttpStatus.GONE.value)
  val PreconditionFailed = Result(HttpStatus.PRECONDITION_FAILED.value)
  val EntityTooLarge = Result(HttpStatus.REQUEST_ENTITY_TOO_LARGE.value)
  val UriTooLong = Result(HttpStatus.REQUEST_URI_TOO_LONG.value)
  val UnsupportedMediaType = Result(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value)
  val ExpectationFailed = Result(HttpStatus.EXPECTATION_FAILED.value)
  val UnprocessableEntity = Result(HttpStatus.UNPROCESSABLE_ENTITY.value)
  val Locked = Result(HttpStatus.LOCKED.value)
  val FailedDependency = Result(HttpStatus.FAILED_DEPENDENCY.value)
  val TooManyRequests = Result(HttpStatus.TOO_MANY_REQUESTS.value)
  val InternalServerError = Result(HttpStatus.INTERNAL_SERVER_ERROR.value)
  val NotImplemented = Result(HttpStatus.NOT_IMPLEMENTED.value)
  val BadGateway = Result(HttpStatus.BAD_GATEWAY.value)
  val ServiceUnavailable = Result(HttpStatus.SERVICE_UNAVAILABLE.value)
  val GatewayTimeout = Result(HttpStatus.GATEWAY_TIMEOUT.value)
  val HttpVersionNotSupported = Result(HttpStatus.HTTP_VERSION_NOT_SUPPORTED.value)
  val InsufficientStorage = Result(HttpStatus.INSUFFICIENT_STORAGE.value)

  def status(code: Int) = Result(code)

  def Redirect(url: String): Result = {
    Result(HttpStatus.SEE_OTHER.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def Redirect(route: ReverseRoute, pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any]): Result = {
    Result(HttpStatus.SEE_OTHER.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(route.url(pathParams, queryParams)))), Seq.empty[Cookie])
  }
}

object Results extends Results {}

case class Result(
  status: Int,
  source: Source[ByteString, _] = Source.empty[ByteString],
  contentType: String = "text/plain",
  headers: Map[String, Seq[String]] = Map.empty[String, Seq[String]],
  cookies: Seq[Cookie] = Seq.empty[Cookie]
) {

  def apply[C](content: C)(implicit canSerialize: CanSerialize[C]): Result = {
    copy(source = Source.single(canSerialize.serialize(content)), contentType = canSerialize.contentType)
  }

  private[webstack] val materializedValue: Promise[Any] = Promise[Any]

  def as(contentType: String): Result = copy(contentType = contentType)

  def withHeader(header: (String, String)): Result = {
    val (key, value) = header
    headers.get(key) match {
      case Some(seq) => copy(headers = headers + (key -> (seq :+ value)))
      case None => copy(headers = headers + (key -> Seq(value)))
    }
  }

  def withStatus(status: Int): Result = copy(status = status)

  def withCookie(cookie: Cookie): Result = copy(cookies = cookies :+ cookie)

  def removeCookie(cookie: Cookie): Result = copy(cookies = cookies.filterNot(c => c == cookie))

  def withBody(source: Source[ByteString, _]): Result = copy(source = source)

  def withBody(source: Publisher[ByteString]): Result = copy(source = Source.fromPublisher(source))

  def withSession(session: Session): Result =  withCookie(session.asCookie)

  def withSession(values: (String, String)*): Result =  withSession(Session(values.toMap))

  def withSession(values: Map[String, String]): Result =  withSession(Session(values))

  def addToSession(values: (String, String)*)(implicit rc: RequestContext): Result = {
    val session = rc.cookies.raw.find(_._1 == rc.env.sessionConfig.cookieName).flatMap(t => Session.fromCookie(t._2)).getOrElse(Session()).add(values:_*)
    withSession(session)
  }

  def addToSession(values: Map[String, String])(implicit rc: RequestContext): Result = {
    val session = rc.cookies.raw.find(_._1 == rc.env.sessionConfig.cookieName).flatMap(t => Session.fromCookie(t._2)).getOrElse(Session()).add(values.toSeq:_*)
    withSession(session)
  }

  def removeFromSession(values: String*)(implicit rc: RequestContext): Result = {
    val session = rc.cookies.raw.find(_._1 == rc.env.sessionConfig.cookieName).flatMap(t => Session.fromCookie(t._2)).getOrElse(Session()).remove(values:_*)
    withSession(session)
  }

  def removeSession(): Result = removeCookie(Session().asCookie.copy(discard = true, maxAge = 0))

  def text(text: String): Result = copy(source = StreamUtils.stringToSource(text), contentType = MediaType.TEXT_PLAIN_VALUE)

  def json(json: String): Result = text(json).as(MediaType.APPLICATION_JSON_VALUE)

  def json(json: JsValue): Result = text(Json.stringify(json)).as(MediaType.APPLICATION_JSON_VALUE)

  def xml(xml: String): Result = text(xml).as(MediaType.APPLICATION_XML_VALUE)

  def html(html: String): Result = text(html).as(MediaType.TEXT_HTML_VALUE)

  def xml(xml: Elem): Result = {
    val xmlString = new scala.xml.PrettyPrinter(80, 2).format(xml)
    text(xmlString).as(MediaType.APPLICATION_XML_VALUE)
  }

  def sendFile(file: File): Result = copy(source = FileIO.fromPath(file.toPath))

  def sendPath(path: Path): Result = sendFile(path.toFile)

  def binary(is: InputStream): Result = {
    copy(source = StreamConverters.fromInputStream(() => is), contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  }

  def binary(bytes: Array[Byte]): Result = {
    copy(source = StreamUtils.bytesToSource(bytes), contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  }

  def binary(bytes: Publisher[ByteString]): Result = binary(Source.fromPublisher(bytes))

  def binary(source: Source[ByteString, _]): Result = {
    copy(source = source, contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  }

  def binary(bytes: ByteString): Result = {
    copy(source = Source.single(bytes), contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  }

  def template(template: play.twirl.api.HtmlFormat.Appendable): Result = {
    text(template.body).as(template.contentType)
  }

  def template(name: String, params: Map[String, _])(implicit env: EnvLike = Env): Result = {
    val p: java.util.Map[String, _] = collection.JavaConversions.mapAsJavaMap(params)
    val context: Context = Context.newBuilder(new Object()).combine(p).build
    val template: String = env.templateResolver.getTemplate(name).apply(context)
    text(template).as(MediaType.TEXT_HTML_VALUE)
  }

  def chunked(source: Publisher[ByteString]): Result = chunked(Source.fromPublisher(source))

  def chunked(source: Source[ByteString, Any]): Result = copy(source = source)

  def stream[A](source: Publisher[A])(implicit writeable: CanSerialize[A]): Result = {
    stream(Source.fromPublisher(source))(writeable)
  }

  def streamText(source: Publisher[String]): Result = {
    stream(Source.fromPublisher(source))(org.reactivecouchbase.webstack.result.serialize.Implicits.canSerializeString)
  }

  def stream[A](source: Source[A, _])(implicit canSerialize: CanSerialize[A]): Result = {
    copy(source = source.map(canSerialize.serialize), contentType = canSerialize.contentType)
  }

  def streamText(source: Source[String, _]): Result = {
    copy(source = source.map(ByteString.fromString))
  }

  def matValue[T](implicit ct: ClassTag[T], ec: ExecutionContext): Future[T] = materializedValue.future.map(e => ct.unapply(e).get)

  override def toString: String = s"Result { $status, $contentType, [ ${headers.mkString(", ")} ], $source }"
}
