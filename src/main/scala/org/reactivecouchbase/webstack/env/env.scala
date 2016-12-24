package org.reactivecouchbase.webstack.env

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.reactivecouchbase.webstack.config.Configuration
import org.reactivecouchbase.webstack.mvc.SessionConfig
import org.reactivecouchbase.webstack.result.{TemplateConfig, TemplatesResolver}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, JsString, Json, Writes}

import scala.annotation.implicitNotFound
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

object EnvLike {

  private[webstack] def actorSystem(name: String, config: Configuration): ActorSystem = {
    ActorSystem(s"$name-system", config.underlying.atPath(s"app.systems.$name").withFallback(ConfigFactory.empty()))
  }

  def apply(_configuration: Configuration, loggerName: String, templateConfig: TemplateConfig = TemplateConfig("/templates", ".html")): EnvLike = {
    val _logger = LoggerFactory.getLogger(loggerName)
    val _templatesResolver = new TemplatesResolver(templateConfig.path, templateConfig.extension)
    val _defaultActorSystem = actorSystem("global", _configuration)
    val _blockingActorSystem = actorSystem("blocking", _configuration)
    val _webserviceActorSystem = actorSystem("ws", _configuration)
    val _websocketActorSystem = actorSystem("websocket", _configuration)
    new EnvLike {
      override def webserviceActorSystem: ActorSystem = _webserviceActorSystem
      override def templateResolver: TemplatesResolver = _templatesResolver
      override def websocketActorSystem: ActorSystem = _websocketActorSystem
      override def blockingActorSystem: ActorSystem = _blockingActorSystem
      override def configuration: Configuration = _configuration
      override def logger: Logger = _logger
      override def defaultActorSystem: ActorSystem = _defaultActorSystem
      override def stop(): Unit = {
        defaultActorSystem.terminate()
        blockingActorSystem.terminate()
        webserviceActorSystem.terminate()
        websocketActorSystem.terminate()
      }
    }
  }
}

@implicitNotFound("Cannot find an instance of EnvLike. Try to create one or use 'import org.reactivecouchbase.webstack.env.Env.Implicits._'")
trait EnvLike {

  // provided
  def logger: Logger
  def configuration: Configuration
  def defaultActorSystem: ActorSystem
  def blockingActorSystem: ActorSystem
  def webserviceActorSystem: ActorSystem
  def websocketActorSystem: ActorSystem
  def templateResolver: TemplatesResolver
  def stop(): Unit

  // default implementation
  lazy val defaultMaterializer = ActorMaterializer.create(defaultActorSystem)
  lazy val blockingMaterializer = ActorMaterializer.create(blockingActorSystem)
  lazy val webserviceMaterializer = ActorMaterializer.create(webserviceActorSystem)
  lazy val websocketMaterializer = ActorMaterializer.create(websocketActorSystem)
  lazy val wsHttp = Http.get(webserviceActorSystem)
  lazy val websocketHttp = Http.get(websocketActorSystem)
  lazy val mode = Mode.valueOf(configuration.getString("app.mode").getOrElse("Prod")).getOrElse(Mode.prod)

  private[webstack] lazy val sessionConfig = new SessionConfig(configuration)

  def defaultExecutionContext: ExecutionContext = defaultActorSystem.dispatcher
  def blockingExecutionContext: ExecutionContext = blockingActorSystem.dispatcher
  def wsExecutionContext: ExecutionContext = webserviceActorSystem.dispatcher
  def websocketExecutionContext: ExecutionContext = websocketActorSystem.dispatcher

  def throwableWriter: Writes[Throwable] = Writes { t =>
    val obj = Json.obj(
      "message" -> t.getMessage,
      "stack" -> Json.arr(t.getStackTrace.map { ste =>
        JsString(ste.toString)
      })
    )
    Option(t.getCause) match {
      case Some(cause) => obj ++ throwableWriter.writes(cause).as[JsObject]
      case None => obj
    }
  }
}

object Env extends EnvLike {

  lazy val configuration = Configuration(ConfigFactory.load)
  lazy val logger = LoggerFactory.getLogger("application")
  lazy val defaultActorSystem = EnvLike.actorSystem("global", configuration)
  lazy val blockingActorSystem = EnvLike.actorSystem("blocking", configuration)
  lazy val webserviceActorSystem = EnvLike.actorSystem("ws", configuration)
  lazy val websocketActorSystem = EnvLike.actorSystem("websocket", configuration)
  lazy val templateResolver = new TemplatesResolver("/templates", ".html")

  override def stop(): Unit = {
    logger.info("Stopping env...")
    defaultActorSystem.terminate()
    blockingActorSystem.terminate()
    webserviceActorSystem.terminate()
    websocketActorSystem.terminate()
  }

  object Implicits {
    implicit val env: EnvLike = Env
  }
}