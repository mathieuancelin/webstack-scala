package org.reactivecouchbase.webstack.websocket

import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.spi.WebSocketHttpExchange
import org.reactivecouchbase.webstack.env.Env

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class WebSocketContext(state: Map[String, AnyRef], exchange: WebSocketHttpExchange, channel: WebSocketChannel) {

  val headers = new WebSocketRequestHeaders(exchange)
  val queryParams = new WebSocketRequestQueryParams(exchange)
  val pathParams = new WebSocketRequestPathParams(exchange)

  def getValue(key: String): AnyRef = state.get(key).get
  def getValue[T](key: String)(implicit clazz: ClassTag[T]): Option[T] = state.get(key).flatMap(clazz.unapply)
  def setValue(key: String, value: AnyRef): WebSocketContext = {
    if (key == null || value == null) {
      this
    } else {
      WebSocketContext(state + (key -> value), exchange, channel)
    }
  }

  def currentExecutor: ExecutionContext = Env.globalExecutionContext
  def uri: String = exchange.getRequestURI
  def scheme: String = exchange.getRequestScheme
  def queryString: String = exchange.getQueryString
  def header(name: String): Option[String] = headers.header(name)
  def queryParam(name: String): Option[String] = queryParams.param(name)
  def pathParam(name: String): Option[String] = pathParams.param(name)

}

case class WebSocketRequestHeaders(request: WebSocketHttpExchange) {

  private val headers = Option.apply(request.getRequestHeaders).map(_.toMap.mapValues(_.toIndexedSeq)).getOrElse(Map.empty[String, Seq[String]])

  def header(name: String): Option[String] = headers.get(name).flatMap(_.headOption)

  def simpleHeaders: Map[String, String] = headers.mapValues(_.head)

  def headerNames: Seq[String] = headers.keys.toSeq

  def raw: Map[String, Seq[String]] = headers
}

case class WebSocketRequestPathParams(request: WebSocketHttpExchange) {
  private val pathParams: Map[String, String] = Option.apply(request.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY))
    .map(m => m.getParameters.toMap).getOrElse(Map.empty[String, String])

  def raw: Map[String, String] = pathParams

  def paramNames: Seq[String] = pathParams.keys.toSeq

  def param(name: String): Option[String] = pathParams.get(name)
}

case class WebSocketRequestQueryParams(request: WebSocketHttpExchange) {

  private val queryParams: Map[String, Seq[String]] = Option(request.getQueryString)
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
  def raw: Map[String, Seq[String]] = queryParams
  def simpleParams: Map[String, String] = queryParams.mapValues(_.head)
  def paramsNames: Seq[String] = queryParams.keys.toSeq
  def params(name: String): Seq[String] = queryParams.getOrElse(name, Seq.empty)
  def param(name: String): Option[String] = queryParams.get(name).flatMap(_.headOption)
}

