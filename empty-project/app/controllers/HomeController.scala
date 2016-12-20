package controllers

import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._

object HomeController {

  implicit val ec  = Env.globalExecutionContext
  implicit val mat = Env.globalMaterializer

  def index = Action.sync { ctx =>
    Ok.template("index",
      Map("who" -> ctx.queryParam("who").getOrElse("World")))
  }
}