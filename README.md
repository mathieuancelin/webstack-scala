# webstack-scala

this project is a highly experimental web framework written in scala on top of `undertow` and `akka-http`. It focuses on async/stream programming and simplicity.

## Create a project

just clone the [empty-project](https://github.com/mathieuancelin/webstack-scala/tree/master/empty-project) directory and your ready to go :-)

## Routes

the routes of your application are located in `app/Routes.scala` and looks like

```scala
import controllers._
import org.reactivecouchbase.webstack.{ClassPathDirectory, WebStackApp}

class Routes extends WebStackApp {

  Get    ⟶       "/"           ⟶         HomeController.index
  Get    ⟶       "/users"      ⟶         HomeController.users
  Post   ⟶       "/users"      ⟶         HomeController.createUser
  Get    ⟶       "/users/{id}" ⟶         HomeController.user
  Ws     ⟶       "/websocket"  ⟶         HomeController.websocket
  Assets ⟶       "/assets"     ⟶         ClassPathDirectory("public")

}
```

## Controllers

to create a controller, just create a object with functions that returns `Action`s

```scala
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._

object MyController {

  implicit val ec  = Env.globalExecutionContext
  implicit val mat = Env.globalMaterializer

  def index = Action.sync { ctx =>
    // return the handlebars template located in res/templates/index.html
    Ok.template("index",
      Map("who" -> ctx.queryParam("who").getOrElse("World"))
    )
  }

  def users = Action.async { ctx =>
    // User.fetchAll returns a Future[JsArray]
    Users.fetchAll().map { users =>
      Ok.json(users)
    }
  }

  def user = Action.async { ctx =>
    ctx.pathParam("id") match {
      case None => Future.failed(NotFound.json(Json.obj("error" -> s"You have to provide an id"))
      case Some(id) => User.findUser(id).map {
        case None => NotFound.json(Json.obj("error" -> s"User with id $id was not found")
        case Some(user) => Ok.json(user)
      }
    }
  }

  def createUser = Action.async { ctx =>
    ctx.body
      .map(body => body.json.as[JsObject])
      .map(json => Users.createUser(User.from(json)))
      .map(Ok.json)
  }
}
```

## Action composition

It is possible to create new `Action`s by composition `ActionStep`s

```scala
import org.reactivecouchbase.webstack.actions.{ Action, ActionStep }
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._

object MyController {

  implicit val ec  = Env.globalExecutionContext
  implicit val mat = Env.globalMaterializer

  val ApiKeyAction = ActionStep.from { (ctx, block) =>
    ctx.header("Api-Key") match {
      case Some(value) if value == "12345" => block(ctx)
      case None => Future.successful(Results.Unauthorized.json(Json.obj("error" -> "you have to provide an Api-Key")))
    }
  }

  val LogBeforeAction = ActionStep.from { (ctx, block) =>
    Env.logger.info(s"Before call of ${ctx.uri}")
    block(ctx)
  }

  val LogAfterAction = ActionStep.from { (ctx, block) =>
    block(ctx).andThen {
      case _ => Env.logger.info(s"After call of ${ctx.uri}")
    }
  }

  val BigAction = LogBeforeAction.andThen(ApiKeyAction).andThen(LogAfterAction)

  def index = BigAction.sync { ctx =>
    Ok.text("Hello World!\n")
  }
}
```

## Streaming support

## WebSockets support

## Http client

## WebSockets client

## TODO

* [ ] Typesafe templating system
* [ ] Typesafe reverse routing
* [ ] Various helpers for webdev (codec, etc ...)
* [ ] Session based on cookie
* [ ] actual dev flow with hot reload