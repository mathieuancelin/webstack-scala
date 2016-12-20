package org.reactivecouchbase.webstack

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import io.undertow.Handlers._
import io.undertow.server.handlers.resource.{ClassPathResourceManager, FileResourceManager}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.HttpString
import io.undertow.{Handlers, Undertow}
import org.reactivecouchbase.webstack.actions.{Action, ReactiveActionHandler}
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.websocket.{ReactiveWebSocketHandler, WebSocketAction}
import org.reflections.Reflections
import play.api.libs.json.Json

import scala.collection.JavaConversions._
import scala.util.Try

case class BootstrappedContext(undertow: Undertow, app: WebStackApp) {

  private val stopped = new AtomicBoolean(false)

  Env.logger.trace("Registering shutdown hook")
  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = stop
  }))

  def stop {
    if (!stopped.get()) {
      try {
        stopped.getAndSet(true)
        app.beforeStop
        undertow.stop
        app.afterStop
        Env.stopEnv()
      } catch {
        case e: Exception => Env.logger.error("Error while stopping server")
      }
    }
  }
}

case class RootRoute(app: WebStackApp, method: HttpMethod) {
  def ->(template: String) = TemplateRoute(app, method, template)
  def ⟶(template: String) = TemplateRoute(app, method, template)
}

case class RootWSRoute(app: WebStackApp) {
  def ->(template: String) = TemplateWSRoute(app, template)
  def ⟶(template: String) = TemplateWSRoute(app, template)
}

case class TemplateRoute(app: WebStackApp, method: HttpMethod, template: String) {
  def ->(action: => Action) = app.route(method, template, action)
  def ⟶(action: => Action) = app.route(method, template, action)
}

case class TemplateWSRoute(app: WebStackApp, template: String) {
  def ->(action: => WebSocketAction) = app.websocketRoute(template, action)
  def ⟶(action: => WebSocketAction) = app.websocketRoute(template, action)
}

case class AssetsRoute(app: WebStackApp) {
  def ->(path: String) = AssetsRouteWithPath(app, path)
  def ⟶(path: String) = AssetsRouteWithPath(app, path)
}

case class AssetsRouteWithPath(app: WebStackApp, path: String) {
  def ->(cpDir: ClassPathDirectory) = app.assets(path, cpDir)
  def ⟶(cpDir: ClassPathDirectory) = app.assets(path, cpDir)
  def ->(fsDir: FSDirectory) =        app.assets(path, fsDir)
  def ⟶(fsDir: FSDirectory) =        app.assets(path, fsDir)
}

case class ClassPathDirectory(path: String)
case class FSDirectory(path: File)

class WebStackApp {

  private[webstack] val routingHandler = Handlers.routing()

  def route(method: HttpMethod, url: String, action: => Action) {
    Env.logger.debug(s"Add route on ${method.value} -> $url")
    routingHandler.add(method.name, url, ReactiveActionHandler(action))
  }

  def assets(url: String, dir: ClassPathDirectory): Unit = {
    Env.logger.debug(s"Add assets on $url -> ${dir.path}")
    routingHandler.setFallbackHandler(path().addPrefixPath(url, resource(new ClassPathResourceManager(classOf[WebStackApp].getClassLoader, dir.path))))
    // routingHandler.add("GET", url, resource(new ClassPathResourceManager(classOf[WebStackApp].getClassLoader, dir.path)))
  }

  def assets(url: String, dir: FSDirectory): Unit = {
    Env.logger.debug(s"Add assets on $url -> ${dir.path}")
    routingHandler.setFallbackHandler(path().addPrefixPath(url, resource(new FileResourceManager(dir.path, 0))))
    // routingHandler.add("GET", url, resource(new FileResourceManager(dir.path, 0)))
  }

  def websocketRoute(url: String, action: => WebSocketAction) {
    routingHandler.add("GET", url, Handlers.websocket(new ReactiveWebSocketHandler(action)))
  }

  def beforeStart {}

  def afterStart {}

  def beforeStop {}

  def afterStop {}

  def start(port: Option[Int] = None): BootstrappedContext = WebStack.startWebStackApp(this, port)

  val Connect = RootRoute(this, HttpMethods.CONNECT)
  val Delete  = RootRoute(this, HttpMethods.DELETE )
  val Get     = RootRoute(this, HttpMethods.GET    )
  val Head    = RootRoute(this, HttpMethods.HEAD   )
  val Options = RootRoute(this, HttpMethods.OPTIONS)
  val Patch   = RootRoute(this, HttpMethods.PATCH  )
  val Post    = RootRoute(this, HttpMethods.POST   )
  val Put     = RootRoute(this, HttpMethods.PUT    )
  val Trace   = RootRoute(this, HttpMethods.TRACE  )
  val Assets  = AssetsRoute(this)
  val Ws      = RootWSRoute(this)
}

object WebStack extends App {

  Env.logger.trace("Scanning classpath looking for WebStackApp implementations")
  new Reflections("").getSubTypesOf(classOf[WebStackApp]).headOption.map { serverClazz =>
    Try {
      Env.logger.info(s"Found WebStackApp class: ${serverClazz.getName}")
      val context = serverClazz.newInstance()
      startWebStackApp(context)
    } get
  }.getOrElse(Env.logger.error("No implementation of WebStackApp found :("))

  private[webstack] def startWebStackApp(webstackApp: WebStackApp, _port: Option[Int] = None): BootstrappedContext = {
    Env.logger.trace("Starting WebStackApp")
    val port = _port.orElse(Env.configuration.getInt("webstack.port")).getOrElse(9000)
    val host = Env.configuration.getString("webstack.host").getOrElse("0.0.0.0")
    val handler = webstackApp.routingHandler.setInvalidMethodHandler(new HttpHandler {
      override def handleRequest(ex: HttpServerExchange): Unit = {
        ex.setStatusCode(400)
        ex.getResponseHeaders.put(HttpString.tryFromString("Content-Type"), "application/json")
        ex.getResponseSender.send(Json.stringify(Json.obj(
          "error" -> s"Invalid Method ${ex.getRequestMethod} on uri ${ex.getRequestURI}"
        )))
      }
    })
    Env.logger.trace("Starting Undertow")
    val server = Undertow
      .builder()
      .addHttpListener(port, host)
      .setHandler(handler)
      .build()
    webstackApp.beforeStart
    server.start()
    webstackApp.afterStart
    Env.logger.trace("Undertow started")
    Env.logger.info("Running WebStack on http://" + host + ":" + port)
    val bootstrapedContext = BootstrappedContext(server, webstackApp)
    Env.logger.trace("Init done")
    bootstrapedContext
  }
}
