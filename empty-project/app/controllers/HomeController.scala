package controllers

import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._

object HomeController {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

  def index = Action.sync { ctx =>
    val who = ctx.queryParam("who").getOrElse("World")
    Ok.template(views.html.index(who))
  }
}