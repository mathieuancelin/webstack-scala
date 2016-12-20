package org.reactivecouchbase.webstack.env

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.ConfigFactory
import org.reactivecouchbase.webstack.config.Configuration
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext

sealed trait Mode
case object Dev extends Mode
case object Test extends Mode
case object Prod extends Mode

object Mode {
  def prod = Prod
  def test = Test
  def dev = Dev
  def valueOf(name: String): Option[Mode] = name match {
    case "Dev"  => Some(Dev)
    case "Test" => Some(Test)
    case "Prod" => Some(Prod)
    case "dev"  => Some(Dev)
    case "test" => Some(Test)
    case "prod" => Some(Prod)
    case _ => None
  }
}

object Env {
  private val DEFAULT = Configuration(ConfigFactory.load)
  private val APP_LOGGER: Logger = LoggerFactory.getLogger("application")

  private val system = ActorSystem("global-system",
    configuration.underlying.atPath("webstack.systems.global").withFallback(ConfigFactory.empty()))
  private val materializer = ActorMaterializer.create(system)
  private val executor = system.dispatcher
  // offered to the internals of actions
  private[webstack] val blockingSystem = ActorSystem("blocking-system",
    configuration.underlying.atPath("webstack.systems.blocking").withFallback(ConfigFactory.empty()))
  private[webstack] val blockingActorMaterializer = ActorMaterializer.create(blockingSystem)
  private[webstack] val blockingExecutor = blockingSystem.dispatcher

  // offered to the internals of ws
  private val wsSystem = ActorSystem("ws-system", configuration.underlying.atPath("webstack.systems.ws").withFallback(ConfigFactory.empty()))
  private[webstack] val wsHttp = Http.get(wsSystem)

  // offered to the internals of websockets
  private val websocketSystem = ActorSystem("websocket-system", configuration.underlying.atPath("webstack.systems.websocket").withFallback(ConfigFactory.empty()))
  private[webstack] val websocketExecutionContext = websocketSystem.dispatcher
  private[webstack] val websocketMaterializer = ActorMaterializer.create(websocketSystem)
  private[webstack] val websocketHttp = Http.get(websocketSystem)

  private[webstack] def stopEnv() = {
    system.terminate()
    blockingSystem.terminate()
    wsSystem.terminate()
    websocketSystem.terminate()
  }

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = stopEnv()
  }))

  def logger: Logger = APP_LOGGER
  def configuration: Configuration = DEFAULT
  def globalActorSystem: ActorSystem = system
  def globalMaterializer: Materializer = materializer
  def globalExecutionContext: ExecutionContext = executor
  lazy val mode: Mode = Mode.valueOf(configuration.getString("app.mode").getOrElse("Prod")).getOrElse(Mode.prod)
}