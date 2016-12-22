package org.reactivecouchbase.webstack.tests

import org.reactivecouchbase.webstack.WebStackApp
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.config.Configuration
import org.reactivecouchbase.webstack.env.{Env, EnvLike}
import org.reactivecouchbase.webstack.mvc.Controller
import play.api.libs.json.Json

class Routes1 extends WebStackApp {

  lazy val _env = EnvLike(Configuration("""app.value = "service1""""), "service1")
  lazy val controller = new ServiceController()(_env)

  override def env = _env

  Get -> "/service" -> controller.service

}

class Routes2 extends WebStackApp {
  lazy val _env = EnvLike(Configuration("""app.value = "service2""""), "service2")
  lazy val controller = new ServiceController()(_env)

  override def env = _env

  Get -> "/service" -> controller.service

}

class ServiceController()(implicit env: EnvLike) extends Controller {

  implicit val ec = env.defaultActorSystem

  def service = Action.sync { ctx =>
    env.logger.info(s"Service called at ${System.currentTimeMillis()}")
    val value = env.configuration.getString("app.value").getOrElse("Not found :(")
    Ok.json(Json.obj(
      "value" -> value
    ))
  }

}

object MultiApp extends App {
  new Routes1().start(port = Some(7005))
  new Routes2().start(port = Some(7006))
  // Killing static env to prove it works
  Env.stop()
}