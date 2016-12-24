# webstack-scala

this project is a highly experimental web framework written in scala on top of `undertow` and `akka-http`. It focuses on async/stream programming and simplicity.

## Create a project

you can use the `sbt new` feature to create a new webstack project

```
sbt new mathieuancelin/webstack-scala-seed.g8
cd xxx
sbt
~re-start
```

or just clone the [empty-project](https://github.com/mathieuancelin/webstack-scala/tree/master/empty-project) directory and your ready to go :-)

```sh
cd empty-project
sbt ~re-start
# or sbt run
open http://localhost:9000
```

## Routes

the routes of your application are located in `app/Routes.scala` and looks like

```scala
import controllers._
import org.reactivecouchbase.webstack.{ClassPathDirectory, WebStackApp}

// you can you a class too
object Routes extends WebStackApp {

  Get    →       "/"           →         HomeController.index
  Get    →       "/users"      →         HomeController.users
  Post   →       "/users"      →         HomeController.createUser
  Get    →       "/users/{id}" →         HomeController.user
  Ws     →       "/websocket"  →         HomeController.websocket
  Assets →       "/assets"     →         ClassPathDirectory("public")

}
```

## Controllers

to create a controller, just create a object with functions that returns `Action`s

```scala
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._

object MyController {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

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

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

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

  val ManagedAction = LogBeforeAction ~> ApiKeyAction ~> LogAfterAction
  // val ManagedAction = LogBeforeAction.andThen(ApiKeyAction).andThen(LogAfterAction)

  def index = ManagedAction.sync { ctx =>
    Ok.text("Hello World!\n")
  }
}
```

## Streaming support

You can easily stream in / out data off your application

```scala
import akka.stream.scaladsl.Framing
import akka.util.ByteString
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._
import play.api.libs.json._

case class User(id: String, name: String, email: String, address: String, phone: String) {
  def toJson = Json.obj(
    "id" -> this.id,
    "name" -> this.name,
    "email" -> this.email,
    "address" -> this.address,
    "phone" -> this.phone
  )
}

object MyController {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

  // Server Sent event
  def stream = Action.sync { ctx =>
    Ok.stream(
      Source.tick(FiniteDuration(0, TimeUnit.SECONDS), FiniteDuration(1, TimeUnit.SECONDS), "")
        .map(l => Json.obj(
          "time" ->System.currentTimeMillis(),
          "value" -> l
        )).map(Json.stringify).map(j => s"data: $j\n\n")
    ).as("text/event-stream")
  }

  // send stream out a file
  def file = Action.sync { ctx =>
    Ok.sendFile(new File("/tmp/bigfile.csv"))
  }

  // curl -X POST --data-binary @/tmp/users.csv -H "Content-Type: text/csv" http://localhost:9000/fromcsv
  def processCsv = Action.sync { ctx =>
    // stream in and process
    val source = ctx.bodyAsStream
      .via(Framing.delimiter(ByteString("\n"), 10000))
      .drop(1)
      .map(_.utf8String)
      .map(_.split(";").toSeq)
      .collect {
        case Seq(id, name, email, address, phone) => User(id, name, email, address, phone)
      }
      .map(_.toJson)
      .map(Json.stringify)
      .map(u => s"$u\n")
    // stream out
    Ok.stream(source).as("application/json")
  }
}
```

## WebSockets support

You can also expose WebSockets using `Ws` in `Routes`

```scala
import org.reactivecouchbase.webstack.actions.WebSocketAction
import org.reactivecouchbase.webstack.websocket.ActorFlow
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._
import org.reactivecouchbase.webstack.ws.WS
import akka.actor._
import akka.http.scaladsl.model.ws._
import org.reactivecouchbase.webstack.{ClassPathDirectory, WebStackApp}

object Routes extends WebStackApp {
  Ws →  "/echo"  →   MyController.echo
}

object MyController {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer
  implicit val system = Env.defaultActorSystem

  def echo = WebSocketAction.accept { context =>
    ActorFlow.actorRef(
      out => EchoActor.props(out)
    )
  }
}

object EchoActor {
  def props(ref: ActorRef) = Props[EchoActor](new EchoActor(ref))
}

class EchoActor(out: ActorRef) extends Actor {
  override def receive = {
    case m: TextMessage => out ! TextMessage(s"received: ${m.getStrictText}")
    case m => unhandled(m)
  }
}
```

## Http client

An http client with streaming capabilities is available too

```scala
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._
import org.reactivecouchbase.webstack.ws.WS

object MyController {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

  def location = Action.async { ctx =>
    WS.host("http://freegeoip.net").withPath("/json/")
      .call()
      .flatMap(_.body)
      .map(b => Json.obj("processed_at" -> System.currentTimeMillis) ++ b.as[JsObject])
      .map(r => Json.prettyPrint(r.json))
      .map(p => Ok.json(p))
  }
}
```

## WebSockets client

You can also consume WebSocket from the WS client

```scala
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.Results._
import org.reactivecouchbase.webstack.ws.WS

object MyController {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

  def location = Action.async { ctx =>
    val sink = Sink.head[Message]
    val source = Source.single("Hello World!")
    val flow = Flow.fromSinkAndSourceMat(sink, source)(Keep.left[Future[Message], Cancellable])
    WS.websocketHost("ws://echo.websocket.org/").call(flow).materialized.map { message =>
      Ok.text(message.asTextMessage.getStrictText)
    }
  }
}
```

## TODO

* [ ] Typesafe templating system
* [ ] Typesafe reverse routing
* [x] Various helpers for webdev (codec, etc ...)
* [x] Session based on cookie
* [x] actual dev flow with hot reload
  * done using sbt-revolver for now