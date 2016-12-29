package org.reactivecouchbase.webstack.websocket

import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow
import io.undertow.util.Headers
import org.reactivecouchbase.webstack.env.{Env, EnvLike}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object WebSocketAction {

  def accept(handler: WebSocketContext => Flow[Message, Message, _])(implicit env: EnvLike = Env): WebSocketAction = {
    implicit val ec = env.blockingExecutionContext
    acceptAsync { ctx =>
      Future {
        Try(handler(ctx)) match {
          case Success(e) => e
          case Failure(e) => {
            env.logger.error("Sync action error", e)
            throw new RuntimeException(e)
          }
        }
      }
    }
  }

  def acceptAsync(handler: WebSocketContext => Future[Flow[Message, Message, _]]): WebSocketAction = {
    WebSocketAction(handler)
  }
}

case class WebSocketAction(handler: WebSocketContext => Future[Flow[Message, Message, _]])