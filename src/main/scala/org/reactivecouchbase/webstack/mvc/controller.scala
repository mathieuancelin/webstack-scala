package org.reactivecouchbase.webstack.mvc

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.reactivecouchbase.webstack.WebStackApp
import org.reactivecouchbase.webstack.actions.{Action, RequestContext}
import org.reactivecouchbase.webstack.config.Configuration
import org.reactivecouchbase.webstack.env.{EnvLike, Mode}
import org.reactivecouchbase.webstack.result.Results
import org.reactivecouchbase.webstack.result.serialize.{Implicits => DefaultCanHttpSerialize}
import org.reactivecouchbase.webstack.ws.WsLike
import org.slf4j.Logger

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

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
  implicit val canSerializeTwirlHtml = DefaultCanHttpSerialize.canSerializeTwirlHtml
  implicit val canSerializeTwirlJs = DefaultCanHttpSerialize.canSerializeTwirlJs
  implicit val canSerializeTwirlText = DefaultCanHttpSerialize.canSerializeTwirlText
  implicit val canSerializeTwirlXml = DefaultCanHttpSerialize.canSerializeTwirlXml
  implicit val canSerializeJsValue = DefaultCanHttpSerialize.canSerializeJsValue
  implicit val canSerializeString = DefaultCanHttpSerialize.canSerializeString
  implicit val canSerializeText = DefaultCanHttpSerialize.canSerializeText
  implicit val canSerializeXml = DefaultCanHttpSerialize.canSerializeXml

  def logger(implicit ctx: RequestContext): Logger = ctx.env.logger
  def env(implicit ctx: RequestContext): EnvLike = ctx.env
  def configuration(implicit ctx: RequestContext): Configuration = ctx.env.configuration
  def actorSystem(implicit ctx: RequestContext): ActorSystem = ctx.env.defaultActorSystem
  def executionContext(implicit ctx: RequestContext): ExecutionContext = ctx.env.defaultExecutionContext
  def materializer(implicit ctx: RequestContext): Materializer = ctx.env.defaultMaterializer
  def mode(implicit ctx: RequestContext): Mode = ctx.env.mode
  def Ws(implicit ctx: RequestContext): WsLike = ctx.env.WS
  def app[A <: WebStackApp](implicit ctx: RequestContext, ct: ClassTag[A]): A = ctx.env.app[A](ct)
}
