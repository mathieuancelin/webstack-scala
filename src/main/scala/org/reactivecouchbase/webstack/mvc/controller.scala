package org.reactivecouchbase.webstack.mvc

import akka.util.ByteString
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.result.{Results, Writeable}
import play.api.libs.json.{JsValue, Json}

import scala.xml.{Elem, PrettyPrinter}

case class EmptyContent()

trait Todo {
  val Todo = Action.sync { ctx =>
    Results.NotImplemented.text("Not Implemented Yet !!!")
  }
}

trait Controller extends Todo with Results {
 implicit val implicitJsValueWriter: Writeable[JsValue] = Writeable(value => ByteString(Json.stringify(value)))
 implicit val implicitByteStringWriter: Writeable[ByteString] = Writeable(value => value)
 implicit val implicitByteArrayWriter: Writeable[Array[Byte]] = Writeable(value => ByteString(value))
 implicit val implicitStringWriter: Writeable[String] = Writeable(value => ByteString(value))
 implicit val implicitElemWriter: Writeable[Elem] = Writeable(value => ByteString(new PrettyPrinter(80, 2).format(value)))
 implicit val implicitEmptyContentWriter: Writeable[EmptyContent] = Writeable(value => ByteString.empty)
}
