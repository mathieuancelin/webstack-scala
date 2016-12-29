package org.reactivecouchbase.webstack.actions

import akka.Done
import akka.stream.scaladsl.{Keep, Sink}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, HttpString}
import org.reactivecouchbase.webstack.WebStackApp
import org.reactivecouchbase.webstack.env.EnvLike
import play.api.libs.json.Json

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object ReactiveActionHandler {
  def apply(env: EnvLike, action: => Action[_]): ReactiveActionHandler = new ReactiveActionHandler(env, action)
}

class ReactiveActionHandler(env: EnvLike, action: => Action[_]) extends HttpHandler {

  def handleRequest(exchange: HttpServerExchange) {
    exchange.setMaxEntitySize(Long.MaxValue)
    exchange.dispatch(new Runnable {
      override def run(): Unit = {
        implicit val ec = env.blockingExecutionContext
        if (exchange.isInIoThread) env.logger.warn("Request processed in IO thread !!!!")
        action.run(exchange).andThen {
          case Failure(e) => {
            exchange.setStatusCode(500)
            exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "application/json")
            exchange.getResponseSender.send(Json.stringify(env.throwableWriter.writes(e)))
            exchange.endExchange()
          }
          case Success(result) => {
            if (exchange.isInIoThread) env.logger.warn("Request running in IO thread !!!!")
            result.headers.foreach(t => exchange.getResponseHeaders.putAll(HttpString.tryFromString(t._1), t._2.toList))
            result.cookies.foreach(c => exchange.getResponseCookies.put(c.name, c.undertowCookie))
            exchange.setStatusCode(result.status)
            exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, result.contentType)
            exchange.getResponseHeaders.put(HttpString.tryFromString("Transfer-Encoding"), "chunked")
            exchange.getResponseHeaders.put(HttpString.tryFromString("X-Transfer-Encoding"), "chunked")
            val responseChannel = exchange.getResponseChannel
            val (first, second) =
              result.source
                .toMat(Sink.foreach { bs =>
                  responseChannel.write(bs.asByteBuffer)
                })(Keep.both[Any, Future[Done]]).run()(env.blockingMaterializer)
            result.materializedValue.trySuccess(first)
            def endExchange() = {
              Try {
                responseChannel.flush()
                exchange.endExchange()
              } match {
                case Success(e) => e
                case Failure(e) => env.logger.error("Error while ending exchange", e)
              }
            }
            second.andThen {
              case Success(_) => endExchange()
              case Failure(e) => endExchange()
            }
          }
        }
      }
    })
  }
}






