package org.reactivecouchbase.webstack.websocket

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core._
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.reactivecouchbase.webstack.env.EnvLike
import org.xnio.ChannelListener

class ReactiveWebSocketHandler(env: EnvLike, supplier: => WebSocketAction) extends WebSocketConnectionCallback {

  val handler = supplier.handler
  val connections = new ConcurrentHashMap[String, SourceQueueWithComplete[Message]]

  def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel) {
    implicit val ec = env.websocketExecutionContext
    implicit val mat = env.websocketMaterializer
    val id = UUID.randomUUID.toString
    try {
      val queue: Source[Message, SourceQueueWithComplete[Message]] = Source.queue(50, OverflowStrategy.backpressure)
      val ctx = WebSocketContext(Map.empty[String, AnyRef], exchange, channel, env)
      handler(ctx).onSuccess {
        case f => {
          val matQueue = queue.via(f).to(Sink.foreach {
            case message if message.isText => WebSockets.sendText(message.asTextMessage.getStrictText, channel, null)
            case message if !message.isText => WebSockets.sendBinary(message.asBinaryMessage.getStrictData.asByteBuffer, channel, null)
          }).run()
          matQueue.watchCompletion().andThen {
            case _ => try {
              exchange.endExchange();
            } catch {
              case e: Exception => env.logger.error("Error while closing websocket session", e)
            }
          }
          connections.put(id, matQueue)
        }
      }
    } catch {
      case e: Exception => env.logger.error("Error after Websocket connection established", e)
    }

    val listener: ChannelListener[WebSocketChannel] = new AbstractReceiveListener() {

      override protected def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage) {
        try {
          get(id).foreach(queue => queue.offer(TextMessage(message.getData)))
        } catch {
          case e: Exception => env.logger.error("Error while handling Websocket message", e)
        }
      }

      override protected def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage) {
        try {
          val bs = message.getData.getResource.toSeq.map(ByteString.fromByteBuffer).foldLeft(ByteString.empty)((a, b) => a.concat(b))
          get(id).foreach(queue => queue.offer(BinaryMessage(bs)))
        } catch {
          case e: Exception => env.logger.error("Error while handling Websocket message", e)
        }
      }

      override protected def onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel) {
        try {
          get(id).foreach(_.complete())
          connections.remove(id)
        } catch {
          case e: Exception => env.logger.error("Error after closing Websocket connection", e)
        }
      }
    }
    channel.getReceiveSetter.set(listener)
    // channel.getCloseSetter.set(new ChannelListener[_] {
    //   try {
    //     get(id).foreach(_.complete)
    //     connections.remove(id)
    //   } catch {
    //     case e: Exception => Env.logger.error("Error after closing Websocket connection", e)
    //   }
    // })
    channel.resumeReceives
  }

  private def get(id: String): Option[SourceQueueWithComplete[Message]] = Option(connections.get(id))
}
