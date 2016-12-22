package org.reactivecouchbase.webstack.websocket

import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.reactivecouchbase.webstack.env.Env

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class WebSocketContext(state: Map[String, AnyRef], exchange: WebSocketHttpExchange, channel: WebSocketChannel) {

  lazy val headers = WebSocketRequestHeaders(exchange)
  lazy val queryParams = WebSocketRequestQueryParams(exchange)
  lazy val pathParams = WebSocketRequestPathParams(exchange)

  def currentExecutionContext: ExecutionContext = Env.defaultExecutionContext
  def uri: String = exchange.getRequestURI
  def scheme: String = exchange.getRequestScheme
  def queryString: String = exchange.getQueryString
  def header(name: String): Option[String] = headers.header(name)
  def queryParam(name: String): Option[String] = queryParams.param(name)
  def pathParam(name: String): Option[String] = pathParams.param(name)
  def getValue(key: String): AnyRef = state.get(key).get
  def getValue[T](key: String)(implicit clazz: ClassTag[T]): Option[T] = state.get(key).flatMap(clazz.unapply)
  def setValue(key: String, value: AnyRef): WebSocketContext = {
    if (key == null || value == null) {
      this
    } else {
      WebSocketContext(state + (key -> value), exchange, channel)
    }
  }
}

case class WebSocketRequestHeaders(exchange: WebSocketHttpExchange) {
  private lazy val raw: Map[String, Seq[String]] = {
    Option.apply(exchange.getRequestHeaders).map(_.toMap.mapValues(_.toIndexedSeq)).getOrElse(Map.empty[String, Seq[String]])
  }
  def header(name: String): Option[String] = raw.get(name).flatMap(_.headOption)
  def simpleHeaders: Map[String, String] = raw.mapValues(_.head)
  def headerNames: Seq[String] = raw.keys.toSeq
}

case class WebSocketRequestPathParams(exchange: WebSocketHttpExchange) {
  private lazy val raw: Map[String, String] = {
    Option.apply(exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY))
      .map(m => m.getParameters.toMap).getOrElse(Map.empty[String, String])
  }
  def paramNames: Seq[String] = raw.keys.toSeq
  def param(name: String): Option[String] = raw.get(name)
}

case class WebSocketRequestQueryParams(exchange: WebSocketHttpExchange) {
  private lazy val raw: Map[String, Seq[String]] = {
    Option(exchange.getQueryString)
      .map(s => {
        var map = Map.empty[String, Seq[String]]
        s.split("&").toSeq.foreach { item =>
          val parts = item.split("=")
          if (parts.size == 2) {
            map.get(parts(0)) match {
              case Some(vals) => map = map + (parts(0) -> (vals :+ parts(1)))
              case None => map = map + (parts(0) -> Seq(parts(1)))
            }
          }
        }
        map
      }).getOrElse(Map.empty[String, Seq[String]])
  }
  def simpleParams: Map[String, String] = raw.mapValues(_.head)
  def paramsNames: Seq[String] = raw.keys.toSeq
  def params(name: String): Seq[String] = raw.getOrElse(name, Seq.empty)
  def param(name: String): Option[String] = raw.get(name).flatMap(_.headOption)
}

