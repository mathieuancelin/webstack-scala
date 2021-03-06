package org.reactivecouchbase.webstack.tests

import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorRef, Cancellable, PoisonPill, Props}
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.{ActorPublisher, ActorPublisherMessage}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.reactivecouchbase.webstack.actions.{Action, ActionStep, Filter, RequestContext}
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.mvc.Controller
import org.reactivecouchbase.webstack.result.Results
import org.reactivecouchbase.webstack.websocket.{ActorFlow, WebSocketAction, WebSocketContext}
import org.reactivecouchbase.webstack.ws.{HttpClient, WebSocketClient}
import org.reactivecouchbase.webstack.{BootstrappedContext, ClassPathDirectory, WebStackApp}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import play.api.libs.json.{JsObject, JsString, Json}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}
import scala.util.Success

object BasicTestSpecRoutes extends WebStackApp {

  val hello = Get    →   "/hello/{name}"      →   MyController.hello
              Get    →   "/sayhello"          →   MyController.index
              Get    →   "/sse"               →   MyController.stream
              Get    →   "/sse2"              →   MyController.stream2
              Get    →   "/test"              →   MyController.text
              Get    →   "/huge"              →   MyController.hugeText
              Get    →   "/json"              →   MyController.json
              Get    →   "/html"              →   MyController.html
              Get    →   "/template"          →   MyController.template
              Get    →   "/ws"                →   MyController.testWS
              Get    →   "/ws2"               →   MyController.testWS2
              Post   →   "/post"              →   MyController.testPost
              Ws     →   "/websocketping"     →   MyController.webSocketPing
              Ws     →   "/websocketsimple"   →   MyController.simpleWebsocket
              Ws     →   "/websocket/{id}"    →   MyController.webSocketWithContext
              Assets →   "/assets"            →   ClassPathDirectory("public")

  override def filters = Seq(
    Filter { (ctx, next) =>
      ctx.env.logger.info("Before action")
      next(ctx)
    },
    Filter { (ctx, next) =>
      next(ctx).andThen {
        case _ => ctx.env.logger.info("After action")
      }(MyController.ec)
    }
  )
}

case class LoggedContext(ctx: RequestContext)

object MyController extends Controller {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer
  implicit val system = Env.defaultActorSystem

  // val FilteredAction = FilterAction(
  //   Filter { (ctx, next) =>
  //     ctx.env.logger.info("Before action")
  //     next(ctx)
  //   },
  //   Filter { (ctx, next) =>
  //     next(ctx).andThen {
  //       case _ => ctx.env.logger.info("After action")
  //     }
  //   }
  // )

  val LoggedAction = ActionStep[LoggedContext] { (ctx, block) =>
    ctx.env.logger.info(s"Calling ${ctx.uri} now")
    block(LoggedContext(ctx)).andThen {
      case _ => ctx.env.logger.trace(s"Call ended on ${ctx.uri}")
    }
  }

  val ApiKeyAction = LoggedAction ~> ActionStep[RequestContext] { (ctx, block) =>
    ctx.header("Api-Key") match {
      case Some(value) if value == "12345" => block(ctx)
      case None => Future.successful(Results.Unauthorized.json(Json.obj("error" -> "you have to provide an Api-Key")))
    }
  }

  def index = ApiKeyAction.sync { implicit ctx =>
    ctx.env.logger.info(s"the route is ${BasicTestSpecRoutes.hello.absoluteUrl(Map("name" -> "Mathieu"), Map("q" -> "who"))}")
    Ok.text("Hello World!\n")
  }

  def stream = Action.sync { ctx =>
    val result = Ok.stream(
      Source.tick(FiniteDuration(0, TimeUnit.SECONDS), FiniteDuration(1, TimeUnit.SECONDS), "")
        .map(l => Json.obj(
          "time" ->System.currentTimeMillis(),
          "value" -> l
        )).map(Json.stringify).map(j => s"data: $j\n\n")
    ).as("text/event-stream")
    result.matValue[Cancellable].andThen {
      case Success(c) => Env.defaultActorSystem.scheduler.scheduleOnce(FiniteDuration(500, TimeUnit.MILLISECONDS)) {
        c.cancel()
      }
    }
    result
  }

  def stream2 = ApiKeyAction.sync { ctx =>
    val result = Ok.stream(SSEActor.source).as("text/event-stream")
    result.matValue[ActorRef].andThen {
      case Success(ref) => ref ! "START"
    }
    result
  }

  def text = ApiKeyAction.sync { ctx =>
    Ok.text("Hello World!\n")
  }

  def hello = ApiKeyAction.sync { ctx =>
    Ok.text("Hello " + ctx.pathParam("name").getOrElse("Unknown") + "!\n")
  }

  def hugeText = ApiKeyAction.sync { ctx =>
    Ok.text(HUGE_TEXT + "\n")
  }

  def json = ApiKeyAction.sync { ctx =>
    Ok.json(Json.obj("message" -> "Hello World!"))
  }

  def html = ApiKeyAction.sync { ctx =>
    Ok.html("<h1>Hello World!</h1>")
  }

  def template = ApiKeyAction.sync { ctx =>
    Ok.template("hello", Map("name" -> ctx.queryParam("who").getOrElse("Mathieu")))
  }

  def testPost = Action.async { ctx =>
    ctx.body
      .map(body => body.json.as[JsObject])
      .map(payload => payload ++ Json.obj("processed_by" -> "SB"))
      .map(Ok.json)
  }

  def testWS = Action.async { ctx =>
    HttpClient("http", "freegeoip.net").withPath("/json/")
      .call()
      .flatMap(_.body)
      .map(r => Json.prettyPrint(r.json))
      .map(p => Ok.json(p))
  }

  def testWS2 = Action.async { ctx =>
    HttpClient("http", "freegeoip.net")
      .withPath("/json/")
      .withHeader("Sent-At", System.currentTimeMillis() + "")
      .call()
      .flatMap(_.body)
      .map(r => Json.prettyPrint(r.json))
      .map(p => Ok.json(p))
  }

  def simpleWebsocket = WebSocketAction.accept { ctx =>
    Flow.fromSinkAndSource(
      Sink.foreach[Message](msg => Env.logger.info(msg.asTextMessage.getStrictText)),
      Source.tick(
        FiniteDuration(0, TimeUnit.MILLISECONDS),
        FiniteDuration(10, TimeUnit.MILLISECONDS),
        TextMessage(Json.stringify(Json.obj("msg" -> "Hello World!")))
      )
    )
  }

  def webSocketPing = WebSocketAction.accept { implicit context =>
    context.env.logger.info(s"the ws route is ${BasicTestSpecRoutes.hello.absoluteWebSocketUrl(Map("name" -> "Mathieu"), Map("q" -> "who"))}")
    ActorFlow.actorRef(
      out => WebsocketPing.props(context, out)
    )
  }

  def webSocketWithContext = WebSocketAction.accept { context =>
    ActorFlow.actorRef(
      out => MyWebSocketActor.props(context, out)
    )
  }

  val HUGE_TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum rhoncus ultrices neque, nec consectetur ex molestie et. Integer dolor purus, laoreet vel condimentum vel, pulvinar at augue. Quisque tempor ac nisl vitae faucibus. Nunc placerat lacus dolor, nec finibus nibh semper eget. Nullam ac ipsum egestas, porttitor leo eget, suscipit risus. Donec sit amet est at erat pellentesque condimentum eu quis mauris. Aliquam tristique consectetur neque, a euismod magna mattis in. Nullam ac orci lectus. Interdum et malesuada fames ac ante ipsum primis in faucibus. Curabitur iaculis, mauris non tempus sagittis, eros nisl maximus quam, sed euismod sapien est id nisl. Nulla vitae enim dictum, tincidunt lorem nec, posuere arcu. Nulla tempus elit eu magna euismod maximus. Morbi varius nulla velit, eget pulvinar augue gravida eu.\n" + "Curabitur enim nisl, sollicitudin at odio laoreet, finibus gravida tellus. Nulla auctor urna magna, non egestas eros dignissim sollicitudin. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Nullam eget magna sit amet magna venenatis consequat vel vel lectus. Morbi fringilla pulvinar diam sed fermentum. Praesent ac tincidunt urna. Praesent in mi dolor. Curabitur posuere massa quis lectus fringilla, at congue ante faucibus. Mauris massa lacus, egestas quis consequat ac, pretium quis arcu. Fusce placerat vel massa eu blandit.\n" + "Curabitur fermentum, ante a tristique interdum, enim diam pulvinar urna, nec aliquet tellus lectus id lectus. Integer ullamcorper lacinia est vulputate pretium. In a dictum velit. In mattis justo sollicitudin iaculis iaculis. Quisque suscipit lorem vel felis accumsan, quis lobortis diam imperdiet. Nullam ornare metus massa, rutrum ullamcorper metus scelerisque a. Nullam finibus diam magna, et fringilla dui faucibus vel. Etiam semper libero sit amet ullamcorper consectetur. Curabitur velit ipsum, cursus sit amet justo eget, rhoncus congue enim. In elit ex, sodales vel odio non, ultricies egestas risus. Proin venenatis consectetur augue, et vestibulum leo dictum vel. Etiam id risus vitae dolor viverra blandit ut ac ante.\n" + "Quisque a nibh sem. Nulla facilisi. Ut gravida, dui et malesuada interdum, nunc arcu eleifend ligula, quis ornare tortor quam at ante. Vestibulum ac porta nibh, vitae imperdiet erat. Pellentesque nec lacus ex. Nullam sed hendrerit lacus. Curabitur varius sem sit amet tortor sollicitudin auctor. Donec eu feugiat enim, quis pellentesque urna. Morbi finibus fermentum varius. Aliquam quis efficitur nisi. Cras at tortor erat. Vestibulum interdum diam lacus, a lacinia mauris dapibus ut. Suspendisse potenti.\n" + "Vestibulum vel diam nec felis sodales porta nec sit amet eros. Quisque sit amet molestie risus. Pellentesque turpis ante, aliquam at urna vel, pulvinar fermentum massa. Proin posuere eu erat id condimentum. Nulla imperdiet erat a varius laoreet. Curabitur sollicitudin urna non commodo condimentum. Ut id ligula in ligula maximus pulvinar et id eros. Fusce et consequat orci. Maecenas leo sem, tristique quis justo nec, accumsan interdum quam. Nunc imperdiet scelerisque iaculis. Praesent sollicitudin purus et purus porttitor volutpat. Duis tincidunt, ipsum vel dignissim imperdiet, ligula nisi ultrices velit, at sodales felis urna at mi. Donec arcu ligula, pulvinar non posuere vel, accumsan eget lorem. Vivamus ac iaculis enim, ut rutrum felis. Praesent non ultrices nibh. Proin tristique, nibh id viverra varius, orci nisi faucibus turpis, quis suscipit sem nisi eu purus."
}

object MyWebSocketActor {
  def props(ctx: WebSocketContext, ref: ActorRef) = Props[MyWebSocketActor](new MyWebSocketActor(ctx, ref))
}

class MyWebSocketActor(ctx: WebSocketContext, out: ActorRef) extends Actor {
  override def receive = {
    case m: Message => {
      val value = Json.parse(m.asTextMessage.getStrictText)
      val response = Json.obj(
        "sent_at" -> System.currentTimeMillis,
        "resource" -> JsString(ctx.pathParam("id").getOrElse("No value !!!")),
        "sourceMessage" -> value
      )
      out ! TextMessage(Json.stringify(response))
    }
    case m => unhandled(m)
  }
}

object WebSocketClientActor {
  def props(out: ActorRef, promise: Promise[Seq[Message]]) = Props[WebSocketClientActor](new WebSocketClientActor(out, promise))
}

class WebSocketClientActor(out: ActorRef, promise: Promise[Seq[Message]]) extends Actor {

  implicit val ec = Env.defaultExecutionContext
  val count = new AtomicInteger(0)
  var messages = Seq.empty[Message]

  override def preStart(): Unit = {
    val me = self
    context.system.scheduler.scheduleOnce(FiniteDuration(100, TimeUnit.MILLISECONDS)) {
      me ! TextMessage("chunk")
    }
  }

  override def receive = {
    case m: Message if count.get == 10 => {
      promise.trySuccess(messages)
      out ! PoisonPill
      self ! PoisonPill
    }
    case m: Message if count.get < 10 => {
      Env.logger.info("[WebSocketClientActor] Sending a chunk {}", count.get)
      count.incrementAndGet
      messages = messages :+ m
      out ! TextMessage("chunk")
    }
    case m => unhandled(m)
  }
}

object WebsocketPing {
  def props(ctx: WebSocketContext, ref: ActorRef) = Props[WebsocketPing](new WebsocketPing(ctx, ref))
}

class WebsocketPing(ctx: WebSocketContext, out: ActorRef) extends Actor {
  override def receive: Receive = {
    case m: Message => out ! m
    case m => unhandled(m)
  }
}

object SSEActor {
  def props = Props[SSEActor](new SSEActor())
  def source: Source[String, ActorRef] = Source.actorPublisher[String](props)
}

class SSEActor extends ActorPublisher[String] {

  var total = 0

  override def receive = {
    case "START" =>
    case m@ActorPublisherMessage.Request(n) => {
      1.toLong.to(n).foreach { v =>
        total = total + 1
        onNext("data: " + Json.stringify(Json.obj("Hello" -> "World!")) + "\n\n")
      }
      if (total == 3) {
        onCompleteThenStop()
      }
    }
    case m@ActorPublisherMessage.Cancel => context.stop(self)
    case m@ActorPublisherMessage.SubscriptionTimeoutExceeded => context.stop(self)
    case m => unhandled(m)
  }
}

object SpecImplicits {
  implicit final class EnhancedFuture[A](future: Future[A]) {
    def await = Await.result(future, Duration(4, TimeUnit.SECONDS))
  }
}

class BasicTestSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  import SpecImplicits._

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

  var server: BootstrappedContext = _

  val port = 7001

  override protected def beforeAll(): Unit = {
    server = BasicTestSpecRoutes.start(port = Some(port))
  }

  override protected def afterAll(): Unit = {
    server.stop
  }

  "Webstack" should "be able to respond with simple text result" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("sayhello")
                    .withHeader("Api-Key" -> "12345")
                    .call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == "Hello World!\n")
    assert(contentType == "text/plain")
  }

  "Webstack" should "be able to respond with a huge text result" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("huge")
                    .withHeader("Api-Key" -> "12345")
                    .call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == MyController.HUGE_TEXT + "\n")
    assert(contentType == "text/plain")
  }

  "Webstack" should "be able to respond with a SSE result" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("sse")
                    .call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    val slugs = body.split("\n").toSeq.filterNot(_.isEmpty).map(_.replace("data: ", "")).map(Json.parse(_).as[JsObject])
    val valid: Boolean = slugs.map(i => (i \ "value").asOpt[String].isDefined && (i \ "time").asOpt[Long].isDefined).foldLeft(true)(_ && _)
    assert(status == 200)
    assert(slugs.size < 7)
    assert(valid)
    assert(contentType == "text/event-stream")
  }

  "Webstack" should "be able to respond with a SSE result from an actor" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("sse")
                    .call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    val slugs = body.split("\n").toSeq.filterNot(_.isEmpty).map(_.replace("data: ", "")).map(Json.parse(_).as[JsObject])
    Env.logger.info(slugs.mkString(" - "))
    val valid: Boolean = slugs.map(i => (i \ "Hello").asOpt[String].getOrElse("false") == "World!").foldLeft(true)(_ && _)
    assert(status == 200)
    assert(slugs.size < 7)
    assert(valid)
    assert(contentType == "text/event-stream")
  }

  "Webstack" should "be able to respond with simple assets" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("assets")
                    .addPathSegment("test.txt").call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == "Hello Test")
    assert(contentType == "text/plain")
  }

  "Webstack" should "be able to respond with a path param result" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("hello")
                    .addPathSegment("Mathieu")
                    .withHeader("Api-Key" -> "12345")
                    .call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == "Hello Mathieu!\n")
    assert(contentType == "text/plain")
  }

  "Webstack" should "be able to respond with simple json result" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("json")
                    .withHeader("Api-Key" -> "12345")
                    .call()
      body     <- resp.body
    } yield (body.json, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == Json.obj("message" -> "Hello World!"))
    assert(contentType == "application/json")
  }

  "Webstack" should "be able to respond with simple html result" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("html")
                    .withHeader("Api-Key" -> "12345")
                    .call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == "<h1>Hello World!</h1>")
    assert(contentType == "text/html")
  }

  "Webstack" should "be able to respond with simple template result" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("template")
                    .withQueryParam("who", "Billy")
                    .withHeader("Api-Key" -> "12345")
                    .call()
      body     <- resp.body
    } yield (body.string, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == "<div><h1>Hello Billy!</h1></div>")
    assert(contentType == "text/html")
  }

  "Webstack" should "be able to respond to a post request" in {
    val uuid = UUID.randomUUID().toString
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("post")
                    .withMethod(HttpMethods.POST)
                    .withBody(Json.obj("uuid" -> uuid))
                    .call()
      body     <- resp.body
    } yield (body.json, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert(body == Json.obj("uuid" -> uuid, "processed_by" -> "SB"))
    assert(contentType == "application/json")
  }

  "Webstack" should "be able to respond with WS json response" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("ws")
                    .call()
      body     <- resp.body
    } yield (body.json, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert((body \ "latitude").asOpt[Double].isDefined)
    assert((body \ "longitude").asOpt[Double].isDefined)
    assert((body \ "ip").asOpt[String].isDefined)
    assert((body \ "city").asOpt[String].isDefined)
    assert((body \ "country_name").asOpt[String].isDefined)
    assert(contentType == "application/json")
  }

  "Webstack" should "be able to respond with WS json response from query param" in {
    val future = for {
      resp     <- HttpClient("http", "localhost", port)
                    .addPathSegment("ws")
                    .withQueryParam("q", "81.246.24.51")
                    .call()
      body     <- resp.body
    } yield (body.json, resp.status, resp.header("Content-Type").getOrElse("none"))
    val (body, status, contentType) = future.await
    assert(status == 200)
    assert((body \ "latitude").asOpt[Double].isDefined)
    assert((body \ "longitude").asOpt[Double].isDefined)
    assert((body \ "ip").asOpt[String].isDefined)
    assert((body \ "city").asOpt[String].isDefined)
    assert((body \ "country_name").asOpt[String].isDefined)
    assert(contentType == "application/json")
  }

  def jsonSource(json: JsObject, duration: Long) = Source.tick(FiniteDuration(0, TimeUnit.MILLISECONDS), FiniteDuration(duration, TimeUnit.MILLISECONDS), TextMessage(Json.stringify(json)))

  "Webstack" should "be able to consume external websocket" in {
    val sink = Sink.head[Message]
    val source = jsonSource(Json.obj("hello" ->"world"), 100)
    val flow = Flow.fromSinkAndSourceMat(sink, source)(Keep.left[Future[Message], Cancellable])
    val future = WebSocketClient("ws", "echo.websocket.org").call(flow).materialized.map { message =>
      Json.parse(message.asTextMessage.getStrictText).as[JsObject]
    }
    val jsonBody = future.await
    assert(Json.obj("hello" -> "world") == jsonBody.as[JsObject])
  }

  "Webstack" should "be able to respond with a websocket result" in {
    val sink = Sink.head[Message]
    val source = jsonSource(Json.obj("hello" ->"world"), 100)
    val flow: Flow[Message, Message, Future[Message]] = Flow.fromSinkAndSourceMat(sink, source)(Keep.left[Future[Message], Cancellable])
    val future = WebSocketClient("ws", s"localhost", port)
        .addPathSegment("websocket")
        .addPathSegment("Mathieu")
        .call(flow)
        .materialized.map { message =>
      Json.parse(message.asTextMessage.getStrictText).as[JsObject]
    }
    val jsonBody = future.await
    assert(Json.obj("hello" -> "world") == (jsonBody \ "sourceMessage").asOpt[JsObject].getOrElse(Json.obj()))
    assert("Mathieu" == (jsonBody \ "resource").asOpt[String].getOrElse(""))
    assert((jsonBody \ "sent_at").asOpt[Long].isDefined)
  }

  "Webstack" should "be able to respond with a websocket result that ping 1" in {
    val sink = Sink.head[Message]
    val source = jsonSource(Json.obj("hello" ->"world"), 100)
    val flow: Flow[Message, Message, Future[Message]] = Flow.fromSinkAndSourceMat(sink, source)(Keep.left[Future[Message], Cancellable])
    val future = WebSocketClient("ws", s"localhost", port)
        .addPathSegment("websocketping")
        .call(flow)
        .materialized
        .map  { message =>
      Json.parse(message.asTextMessage.getStrictText).as[JsObject]
    }
    val jsonBody = future.await
    assert(Json.obj("hello" -> "world") == jsonBody.as[JsObject])
  }

  "Webstack" should "be able to respond with a websocket result that ping 2" in {
    implicit val system = Env.defaultActorSystem
    val promise = Promise[Seq[Message]]
    val flow = ActorFlow.actorRef(out => WebSocketClientActor.props(out, promise))
    WebSocketClient("ws", s"localhost", port).addPathSegment("websocketping").callNoMat(flow)
    val messages = promise.future.await.map(_.asTextMessage.getStrictText)
    assert(Seq("chunk", "chunk", "chunk", "chunk", "chunk", "chunk", "chunk", "chunk", "chunk", "chunk") == messages)
  }

  "Webstack" should "be able to respond with a simple websocket result" in {
    val sink = Sink.head[Message]
    val source = jsonSource(Json.obj("hello" ->"world"), 100)
    val flow: Flow[Message, Message, Future[Message]] = Flow.fromSinkAndSourceMat(sink, source)(Keep.left[Future[Message], Cancellable])
    val future = WebSocketClient("ws", s"localhost", port)
        .addPathSegment("websocketsimple")
        .call(flow)
        .materialized
        .map { message =>
      Json.parse(message.asTextMessage.getStrictText).as[JsObject]
    }
    val jsonBody = future.await
    assert(Json.obj("msg" -> "Hello World!") == jsonBody.as[JsObject])
  }

  val TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum rhoncus ultrices neque, nec consectetur ex molestie et. Integer dolor purus, laoreet vel condimentum vel, pulvinar at augue. Quisque tempor ac nisl vitae faucibus. Nunc placerat lacus dolor, nec finibus nibh semper eget. Nullam ac ipsum egestas, porttitor leo eget, suscipit risus. Donec sit amet est at erat pellentesque condimentum eu quis mauris. Aliquam tristique consectetur neque, a euismod magna mattis in. Nullam ac orci lectus. Interdum et malesuada fames ac ante ipsum primis in faucibus. Curabitur iaculis, mauris non tempus sagittis, eros nisl maximus quam, sed euismod sapien est id nisl. Nulla vitae enim dictum, tincidunt lorem nec, posuere arcu. Nulla tempus elit eu magna euismod maximus. Morbi varius nulla velit, eget pulvinar augue gravida eu.\n" + "Curabitur enim nisl, sollicitudin at odio laoreet, finibus gravida tellus. Nulla auctor urna magna, non egestas eros dignissim sollicitudin. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Nullam eget magna sit amet magna venenatis consequat vel vel lectus. Morbi fringilla pulvinar diam sed fermentum. Praesent ac tincidunt urna. Praesent in mi dolor. Curabitur posuere massa quis lectus fringilla, at congue ante faucibus. Mauris massa lacus, egestas quis consequat ac, pretium quis arcu. Fusce placerat vel massa eu blandit.\n" + "Curabitur fermentum, ante a tristique interdum, enim diam pulvinar urna, nec aliquet tellus lectus id lectus. Integer ullamcorper lacinia est vulputate pretium. In a dictum velit. In mattis justo sollicitudin iaculis iaculis. Quisque suscipit lorem vel felis accumsan, quis lobortis diam imperdiet. Nullam ornare metus massa, rutrum ullamcorper metus scelerisque a. Nullam finibus diam magna, et fringilla dui faucibus vel. Etiam semper libero sit amet ullamcorper consectetur. Curabitur velit ipsum, cursus sit amet justo eget, rhoncus congue enim. In elit ex, sodales vel odio non, ultricies egestas risus. Proin venenatis consectetur augue, et vestibulum leo dictum vel. Etiam id risus vitae dolor viverra blandit ut ac ante.\n" + "Quisque a nibh sem. Nulla facilisi. Ut gravida, dui et malesuada interdum, nunc arcu eleifend ligula, quis ornare tortor quam at ante. Vestibulum ac porta nibh, vitae imperdiet erat. Pellentesque nec lacus ex. Nullam sed hendrerit lacus. Curabitur varius sem sit amet tortor sollicitudin auctor. Donec eu feugiat enim, quis pellentesque urna. Morbi finibus fermentum varius. Aliquam quis efficitur nisi. Cras at tortor erat. Vestibulum interdum diam lacus, a lacinia mauris dapibus ut. Suspendisse potenti.\n" + "Vestibulum vel diam nec felis sodales porta nec sit amet eros. Quisque sit amet molestie risus. Pellentesque turpis ante, aliquam at urna vel, pulvinar fermentum massa. Proin posuere eu erat id condimentum. Nulla imperdiet erat a varius laoreet. Curabitur sollicitudin urna non commodo condimentum. Ut id ligula in ligula maximus pulvinar et id eros. Fusce et consequat orci. Maecenas leo sem, tristique quis justo nec, accumsan interdum quam. Nunc imperdiet scelerisque iaculis. Praesent sollicitudin purus et purus porttitor volutpat. Duis tincidunt, ipsum vel dignissim imperdiet, ligula nisi ultrices velit, at sodales felis urna at mi. Donec arcu ligula, pulvinar non posuere vel, accumsan eget lorem. Vivamus ac iaculis enim, ut rutrum felis. Praesent non ultrices nibh. Proin tristique, nibh id viverra varius, orci nisi faucibus turpis, quis suscipit sem nisi eu purus."
  val HUGE_TEXT = (1 to 200).map(_ => TEXT).mkString("\n")
}
