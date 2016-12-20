package org.reactivecouchbase.webstack.result

import java.io.{ByteArrayInputStream, File, InputStream, StringWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Arrays
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import javax.xml.transform.{OutputKeys, Transformer, TransformerFactory}
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.ByteString
import com.github.jknack.handlebars.{Context, Handlebars, Template}
import com.google.common.base.Throwables
import io.undertow.server.handlers.Cookie
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.Try
import scala.xml.{Elem, XML}

object Results {
  val Continue: Result = new Result(HttpStatus.CONTINUE.value)
  val SwitchingProtocols: Result = new Result(HttpStatus.SWITCHING_PROTOCOLS.value)
  val Ok: Result = new Result(HttpStatus.OK.value)
  val Created: Result = new Result(HttpStatus.CREATED.value)
  val Accepted: Result = new Result(HttpStatus.ACCEPTED.value)
  val NonAuthoritativeInformation: Result = new Result(HttpStatus.NON_AUTHORITATIVE_INFORMATION.value)
  val NoContent: Result = new Result(HttpStatus.NO_CONTENT.value)
  val ResetContent: Result = new Result(HttpStatus.RESET_CONTENT.value)
  val PartialContent: Result = new Result(HttpStatus.PARTIAL_CONTENT.value)
  val MultiStatus: Result = new Result(HttpStatus.MULTI_STATUS.value)

  def MovedPermanently(url: String): Result = {
    new Result(HttpStatus.MOVED_PERMANENTLY.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def Found(url: String): Result = {
    new Result(HttpStatus.FOUND.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def SeeOther(url: String): Result = {
    new Result(HttpStatus.SEE_OTHER.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  val NotModified: Result = new Result(HttpStatus.NOT_MODIFIED.value)

  def TemporaryRedirect(url: String): Result = {
    new Result(HttpStatus.TEMPORARY_REDIRECT.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  def PermanentRedirect(url: String): Result = {
    new Result(HttpStatus.PERMANENT_REDIRECT.value, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }

  val BadRequest: Result = new Result(HttpStatus.BAD_REQUEST.value)
  val Unauthorized: Result = new Result(HttpStatus.UNAUTHORIZED.value)
  val PaymentRequired: Result = new Result(HttpStatus.PAYMENT_REQUIRED.value)
  val Forbidden: Result = new Result(HttpStatus.FORBIDDEN.value)
  val NotFound: Result = new Result(HttpStatus.NOT_FOUND.value)
  val MethodNotAllowed: Result = new Result(HttpStatus.METHOD_NOT_ALLOWED.value)
  val NotAcceptable: Result = new Result(HttpStatus.NOT_ACCEPTABLE.value)
  val RequestTimeout: Result = new Result(HttpStatus.REQUEST_TIMEOUT.value)
  val Conflict: Result = new Result(HttpStatus.CONFLICT.value)
  val Gone: Result = new Result(HttpStatus.GONE.value)
  val PreconditionFailed: Result = new Result(HttpStatus.PRECONDITION_FAILED.value)
  val EntityTooLarge: Result = new Result(HttpStatus.REQUEST_ENTITY_TOO_LARGE.value)
  val UriTooLong: Result = new Result(HttpStatus.REQUEST_URI_TOO_LONG.value)
  val UnsupportedMediaType: Result = new Result(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value)
  val ExpectationFailed: Result = new Result(HttpStatus.EXPECTATION_FAILED.value)
  val UnprocessableEntity: Result = new Result(HttpStatus.UNPROCESSABLE_ENTITY.value)
  val Locked: Result = new Result(HttpStatus.LOCKED.value)
  val FailedDependency: Result = new Result(HttpStatus.FAILED_DEPENDENCY.value)
  val TooManyRequests: Result = new Result(HttpStatus.TOO_MANY_REQUESTS.value)
  val InternalServerError: Result = new Result(HttpStatus.INTERNAL_SERVER_ERROR.value)
  val NotImplemented: Result = new Result(HttpStatus.NOT_IMPLEMENTED.value)
  val BadGateway: Result = new Result(HttpStatus.BAD_GATEWAY.value)
  val ServiceUnavailable: Result = new Result(HttpStatus.SERVICE_UNAVAILABLE.value)
  val GatewayTimeout: Result = new Result(HttpStatus.GATEWAY_TIMEOUT.value)
  val HttpVersionNotSupported: Result = new Result(HttpStatus.HTTP_VERSION_NOT_SUPPORTED.value)
  val InsufficientStorage: Result = new Result(HttpStatus.INSUFFICIENT_STORAGE.value)

  def status(code: Int): Result = new Result(code)

  def redirect(url: String): Result = {
    new Result(200, Source.empty[ByteString], "text/plain", Map(("Location", Seq(url))), Seq.empty[Cookie])
  }
}

object Result {
  private val handlebars: Handlebars = TemplatesBoilerplate("/templates", ".html").handlebars
  private val TEMPLATES_CACHE: ConcurrentMap[String, Template] = new ConcurrentHashMap[String, Template]
  private def getTemplate(name: String): Template = {
    if (!TEMPLATES_CACHE.containsKey(name)) {
      Try {
        val template: Template = handlebars.compile(name)
        TEMPLATES_CACHE.putIfAbsent(name, template)
      } get
    }
    TEMPLATES_CACHE.get(name)
  }
}

case class Result(status: Int, source: Source[ByteString, _], contentType: String, headers: Map[String, Seq[String]], cookies: Seq[Cookie]) {

  val materializedValue: Promise[Any] = Promise[Any]

  def this(status: Int) {
    this(status, Source.empty, "text/plain", Map.empty[String, List[String]], Seq.empty[Cookie])
  }

  def this(status: Int, source: Source[ByteString, Any]) {
    this(status, source, "text/plain", Map.empty[String, List[String]], Seq.empty[Cookie])
  }

  def this(status: Int, contentType: String) {
    this(status, Source.empty, contentType, Map.empty[String, List[String]], Seq.empty[Cookie])
  }

  def as(contentType: String): Result = copy(contentType = contentType)

  def withHeader(header: (String, String)): Result = {
    val (key, value) = header
    headers.get(key) match {
      case Some(seq) => copy(headers = headers + (key -> (seq :+ value)))
      case None => copy(headers = headers + (key -> Seq(value)))
    }
  }

  // TODO : add session support

  def withStatus(status: Int): Result = copy(status = status)

  def withCookie(cookie: Cookie): Result = copy(cookies = cookies :+ cookie)

  def removeCookie(cookie: Cookie): Result = copy(cookies = cookies.filterNot(c => c == cookie))

  def withBody(source: Source[ByteString, _]): Result = copy(source = source)

  def withBody(source: Publisher[ByteString]): Result = copy(source = Source.fromPublisher(source))

  def text(text: String): Result = {
    // TODO : avoid IS
    val source: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
    copy(source = source, contentType = MediaType.TEXT_PLAIN_VALUE)
  }

  def json(json: String): Result = {
    // TODO : avoid IS
    val source: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
    copy(source = source, contentType = MediaType.APPLICATION_JSON_VALUE)
  }

  def json(json: JsValue): Result = {
    // TODO : avoid IS
    val source: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(Json.stringify(json).getBytes(StandardCharsets.UTF_8)))
    copy(source = source, contentType = MediaType.APPLICATION_JSON_VALUE)
  }

  def xml(xml: String): Result = {
    // TODO : avoid IS
    val source: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
    copy(source = source, contentType = MediaType.APPLICATION_XML_VALUE)
  }

  def html(html: String): Result = {
    // TODO : avoid IS
    val source: Source[ByteString, Any] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)))
    copy(source = source, contentType = MediaType.TEXT_HTML_VALUE)
  }

  def xml(xml: Elem): Result = {
    val xmlString = new scala.xml.PrettyPrinter(80, 2).format(xml)
    text(xmlString).as(MediaType.APPLICATION_XML_VALUE)
  }

  def sendFile(file: File): Result = copy(source = FileIO.fromPath(file.toPath))

  def sendPath(path: Path): Result = sendFile(path.toFile)

  def binary(is: InputStream): Result = {
    val source: Source[ByteString, _] = StreamConverters.fromInputStream(() => is)
    copy(source = source, contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  }

  // def binary(bytes: Array[Byte]): Result = {
  //   val source: Source[ByteString, Any] = Source(bytes).buffer(8192, OverflowStrategy.backpressure).map(ByteString.fromArray)
  //   copy(source = source, contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  // }

  def binary(bytes: Publisher[ByteString]): Result = binary(Source.fromPublisher(bytes))

  def binary(source: Source[ByteString, _]): Result = {
    copy(source = source, contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  }

  def binary(bytes: ByteString): Result = {
    copy(source = Source.single(bytes), contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  }

  def template(name: String, params: Map[String, _]): Result = {
    Try {
      val p: java.util.Map[String, _] = collection.JavaConversions.mapAsJavaMap(params)
      val context: Context = Context.newBuilder(new Object()).combine(p).build
      val template: String = Result.getTemplate(name).apply(context)
      val source: Source[ByteString, Any] = Source.single(ByteString.fromString(template))
      copy(source = source, contentType = MediaType.TEXT_HTML_VALUE)
    } get
  }

  def chunked(source: Publisher[ByteString]): Result = chunked(Source.fromPublisher(source))

  def chunked(source: Source[ByteString, Any]): Result = copy(source = source)

  def stream(source: Publisher[String]): Result = stream(Source.fromPublisher(source))

  def stream(stream: Source[String, _]): Result = copy(source = stream.map(ByteString.fromString))

  def matValue[T](implicit ct: ClassTag[T], ec: ExecutionContext): Future[T] = materializedValue.future.map(e => ct.unapply(e).get)

  override def toString: String = "Result { " + status + ", " + contentType + ", [ " + headers.mkString(", ") + " ], " + source + " }"
}
