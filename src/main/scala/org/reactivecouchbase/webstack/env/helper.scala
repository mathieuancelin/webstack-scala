package org.reactivecouchbase.webstack.env

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.reactivecouchbase.webstack.WebStackApp
import org.reactivecouchbase.webstack.config.Configuration
import org.reactivecouchbase.webstack.ws.{HttpClientFinal, WebSocketClientFinal}
import org.slf4j.Logger

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait EnvAware {
  def logger(implicit env: EnvLike): Logger = env.logger
  def env(implicit env: EnvLike): EnvLike = env
  def configuration(implicit env: EnvLike): Configuration = env.configuration
  def actorSystem(implicit env: EnvLike): ActorSystem = env.defaultActorSystem
  def executionContext(implicit env: EnvLike): ExecutionContext = env.defaultExecutionContext
  def materializer(implicit env: EnvLike): Materializer = env.defaultMaterializer
  def mode(implicit env: EnvLike): Mode = env.mode
  def httpClient(protocol: String, host: String, port: Int = 80)(implicit env: EnvLike): HttpClientFinal = env.HttpClient(protocol, host, port)
  def webSocketClient(protocol: String, host: String, port: Int = 80)(implicit env: EnvLike): WebSocketClientFinal = env.WebSocketClient(protocol, host, port)
  def app[A <: WebStackApp](implicit env: EnvLike, ct: ClassTag[A]): A = env.app[A](ct)
}
