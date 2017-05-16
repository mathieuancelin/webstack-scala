package org.reactivecouchbase.webstack.result.serialize

import akka.util.ByteString
import org.reactivecouchbase.webstack.result.{Html, Xml, Text, MediaType, EmptyContent}
import play.api.libs.json.{JsValue, Json}
import play.twirl.api._

import scala.annotation.implicitNotFound
import scala.xml.{Elem, PrettyPrinter}


@implicitNotFound("Cannot write an instance of ${A} to ByteString. Try to define a CanSerialize[${A}]")
trait CanSerialize[-A] {
  def serialize(a: A): ByteString
  def contentType: String
}

object CanSerialize {
  def apply[A: CanSerialize]: CanSerialize[A] = implicitly
}

object Serialize {
  def transformToByteString[A](value: A)(implicit evidence: CanSerialize[A]): ByteString = evidence.serialize(value)
}

object Implicits {

  implicit val canSerializeJsValue = new CanSerialize[JsValue] {
    override def serialize(a: JsValue): ByteString = ByteString(Json.stringify(a))
    override def contentType: String = MediaType.APPLICATION_JSON_UTF8_VALUE
  }

  implicit val canSerializeByteString = new CanSerialize[ByteString] {
    override def serialize(a: ByteString): ByteString = a
    override def contentType: String = MediaType.APPLICATION_OCTET_STREAM_VALUE
  }

  implicit val canSerializeByteArray = new CanSerialize[Array[Byte]] {
    override def serialize(a: Array[Byte]): ByteString = ByteString(a)
    override def contentType: String = MediaType.APPLICATION_OCTET_STREAM_VALUE
  }

  implicit val canSerializeString = new CanSerialize[String] {
    override def serialize(a: String): ByteString = ByteString(a)
    override def contentType: String = MediaType.TEXT_PLAIN_VALUE
  }

  implicit val canSerializeEmptyContent = new CanSerialize[EmptyContent] {
    override def serialize(a: EmptyContent): ByteString = ByteString.empty
    override def contentType: String = MediaType.TEXT_PLAIN_VALUE
  }

  implicit val canSerializeElem = new CanSerialize[Elem] {
    override def serialize(a: Elem): ByteString = ByteString(new PrettyPrinter(80, 2).format(a))
    override def contentType: String = MediaType.TEXT_XML_VALUE
  }

  implicit val canSerializeHtml = new CanSerialize[Html] {
    override def serialize(a: Html): ByteString = ByteString(a.value)
    override def contentType: String = MediaType.TEXT_HTML_VALUE
  }

  implicit val canSerializeXml = new CanSerialize[Xml] {
    override def serialize(a: Xml): ByteString = ByteString(a.value)
    override def contentType: String = MediaType.TEXT_XML_VALUE
  }

  implicit val canSerializeText = new CanSerialize[Text] {
    override def serialize(a: Text): ByteString = ByteString(a.value)
    override def contentType: String = MediaType.TEXT_PLAIN_VALUE
  }

  implicit val canSerializeTwirlHtml = new CanSerialize[HtmlFormat.Appendable] {
    override def serialize(a: HtmlFormat.Appendable): ByteString = ByteString(a.body)
    override def contentType: String = MimeTypes.HTML
  }

  implicit val canSerializeTwirlJs = new CanSerialize[JavaScriptFormat.Appendable] {
    override def serialize(a: JavaScriptFormat.Appendable): ByteString = ByteString(a.body)
    override def contentType: String = MimeTypes.JAVASCRIPT
  }

  implicit val canSerializeTwirlText = new CanSerialize[TxtFormat.Appendable] {
    override def serialize(a: TxtFormat.Appendable): ByteString = ByteString(a.body)
    override def contentType: String = MimeTypes.TEXT
  }

  implicit val canSerializeTwirlXml = new CanSerialize[XmlFormat.Appendable] {
    override def serialize(a: XmlFormat.Appendable): ByteString = ByteString(a.body)
    override def contentType: String = MimeTypes.XML
  }
}
