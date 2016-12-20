package org.reactivecouchbase.webstack.websocket

import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow

import scala.concurrent.Future

object WebSocketAction {

  def accept(handler: WebSocketContext => Flow[Message, Message, _]): WebSocketAction = {
    WebSocketAction(ctx => Future.successful(handler.apply(ctx)))
  }

  def acceptAsync(handler: WebSocketContext => Future[Flow[Message, Message, _]]): WebSocketAction = WebSocketAction(handler)
}

case class WebSocketAction(handler: WebSocketContext => Future[Flow[Message, Message, _]])