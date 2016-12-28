package org.reactivecouchbase.webstack.mvc

import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.result.Results
import org.reactivecouchbase.webstack.result.serialize.{ Implicits => DefaultCanHttpSerialize }

trait Todo {
  val Todo = Action.sync { ctx =>
    Results.NotImplemented.text("Not Implemented Yet !!!")
  }
}

trait Controller extends Todo with Results {

  implicit val canSerializeByteArray = DefaultCanHttpSerialize.canSerializeByteArray
  implicit val canSerializeByteString = DefaultCanHttpSerialize.canSerializeByteString
  implicit val canSerializeElem = DefaultCanHttpSerialize.canSerializeElem
  implicit val canSerializeEmptyContent = DefaultCanHttpSerialize.canSerializeEmptyContent
  implicit val canSerializeHtml = DefaultCanHttpSerialize.canSerializeHtml
  implicit val canSerializeTwirl = DefaultCanHttpSerialize.canSerializeTwirl
  implicit val canSerializeJsValue = DefaultCanHttpSerialize.canSerializeJsValue
  implicit val canSerializeString = DefaultCanHttpSerialize.canSerializeString
  implicit val canSerializeText = DefaultCanHttpSerialize.canSerializeText
  implicit val canSerializeXml = DefaultCanHttpSerialize.canSerializeXml

}
