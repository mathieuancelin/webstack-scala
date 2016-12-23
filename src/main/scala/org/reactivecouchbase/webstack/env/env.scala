package org.reactivecouchbase.webstack.env

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.{ActorMaterializer, Materializer}
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

@implicitNotFound("Cannot find an instance of EnvLike. Try to import a org.reactivecouchbase.webstack.env.Env")
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
  private lazy val _defaultMaterializer = ActorMaterializer.create(defaultActorSystem)
  private lazy val _blockingMaterializer = ActorMaterializer.create(blockingActorSystem)
  private lazy val _webserviceMaterializer = ActorMaterializer.create(webserviceActorSystem)
  private lazy val _websocketMaterializer = ActorMaterializer.create(websocketActorSystem)
  private lazy val _webserviceHttp = Http.get(webserviceActorSystem)
  private lazy val _websocketHttp = Http.get(websocketActorSystem)
  private[webstack] lazy val sessionConfig = new SessionConfig(configuration)
  lazy val mode: Mode = Mode.valueOf(configuration.getString("app.mode").getOrElse("Prod")).getOrElse(Mode.prod)

  def defaultExecutionContext: ExecutionContext = defaultActorSystem.dispatcher
  def defaultMaterializer: Materializer = _defaultMaterializer

  def blockingExecutionContext: ExecutionContext = blockingActorSystem.dispatcher
  def blockingMaterializer: Materializer = _blockingMaterializer

  def wsExecutionContext: ExecutionContext = webserviceActorSystem.dispatcher
  def wsMaterializer: Materializer = _webserviceMaterializer
  def wsHttp: HttpExt = _webserviceHttp

  def websocketExecutionContext: ExecutionContext = websocketActorSystem.dispatcher
  def websocketMaterializer: Materializer = _websocketMaterializer
  def websocketHttp: HttpExt = _websocketHttp

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

  private lazy val DEFAULT = Configuration(ConfigFactory.load)
  private lazy val APP_LOGGER: Logger = LoggerFactory.getLogger("application")
  private lazy val _defaultActorSystem = EnvLike.actorSystem("global", configuration)
  private lazy val _blockingActorSystem = EnvLike.actorSystem("blocking", configuration)
  private lazy val _webserviceActorSystem = EnvLike.actorSystem("ws", configuration)
  private lazy val _websocketActorSystem = EnvLike.actorSystem("websocket", configuration)
  private lazy val _templateResolver = new TemplatesResolver("/templates", ".html")

  override def logger: Logger = APP_LOGGER
  override def configuration: Configuration = DEFAULT
  override def websocketActorSystem: ActorSystem = _websocketActorSystem
  override def blockingActorSystem: ActorSystem = _blockingActorSystem
  override def webserviceActorSystem: ActorSystem = _webserviceActorSystem
  override def defaultActorSystem: ActorSystem = _defaultActorSystem
  override def templateResolver: TemplatesResolver = _templateResolver

  override def stop(): Unit = {
    _defaultActorSystem.terminate()
    _blockingActorSystem.terminate()
    _webserviceActorSystem.terminate()
    _websocketActorSystem.terminate()
  }

  object Implicits {
    implicit val env: EnvLike = Env
  }
}