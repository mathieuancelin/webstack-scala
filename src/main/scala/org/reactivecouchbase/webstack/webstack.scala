package org.reactivecouchbase.webstack

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import io.undertow.Handlers._
import io.undertow.server.handlers.resource.{ClassPathResourceManager, FileResourceManager}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.HttpString
import io.undertow.{Handlers, Undertow}
import org.reactivecouchbase.webstack.actions.{Action, Filter, ReactiveActionHandler, RequestContext}
import org.reactivecouchbase.webstack.env.{Env, EnvLike}
import org.reactivecouchbase.webstack.websocket.{ReactiveWebSocketHandler, WebSocketAction, WebSocketContext}
import org.reflections.Reflections
import play.api.libs.json.Json

import scala.collection.JavaConversions._
import scala.util.Try

case class BootstrappedContext(undertow: Undertow, app: WebStackApp, env: EnvLike) {

  private val stopped = new AtomicBoolean(false)

  env.logger.trace("Registering shutdown hook")
  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = stop
  }))

  def stop(): Unit = {
    if (!stopped.get()) {
      try {
        stopped.getAndSet(true)
        app.beforeStop()
        undertow.stop()
        app.afterStop()
        env.stop()
      } catch {
        case e: Exception => env.logger.error("Error while stopping server", e)
      }
    }
  }
}

case class RootRoute(app: WebStackApp, method: HttpMethod) {
  def ->(template: String) = TemplateRoute(app, method, template)
  def →(template: String) = TemplateRoute(app, method, template)
  def on(template: String) = TemplateRoute(app, method, template)
}

case class RootWSRoute(app: WebStackApp) {
  def ->(template: String) = TemplateWSRoute(app, template)
  def →(template: String) = TemplateWSRoute(app, template)
  def on(template: String) = TemplateWSRoute(app, template)
}

case class TemplateRoute(app: WebStackApp, method: HttpMethod, template: String) {
  def ->(action: => Action[_])(implicit env: EnvLike = Env) = app.route(method, template, action)(env)
  def →(action: => Action[_])(implicit env: EnvLike = Env) = app.route(method, template, action)(env)
  def call(action: => Action[_])(implicit env: EnvLike = Env) = app.route(method, template, action)(env)
}

case class TemplateWSRoute(app: WebStackApp, template: String) {
  def ->(action: => WebSocketAction)(implicit env: EnvLike = Env) = app.websocketRoute(template, action)(env)
  def →(action: => WebSocketAction)(implicit env: EnvLike = Env) = app.websocketRoute(template, action)(env)
  def call(action: => WebSocketAction)(implicit env: EnvLike = Env) = app.websocketRoute(template, action)(env)
}

case class AssetsRoute(app: WebStackApp) {
  def ->(path: String) = AssetsRouteWithPath(app, path)
  def →(path: String) = AssetsRouteWithPath(app, path)
  def on(path: String) = AssetsRouteWithPath(app, path)
}

case class AssetsRouteWithPath(app: WebStackApp, path: String) {
  def ->(dir: ResourcesDiractory)(implicit env: EnvLike = Env)     = app.assets(path, dir)
  def →(dir: ResourcesDiractory)(implicit env: EnvLike = Env)      = app.assets(path, dir)
  def serves(dir: ResourcesDiractory)(implicit env: EnvLike = Env) = app.assets(path, dir)
}

sealed trait ResourcesDiractory
case class ClassPathDirectory(path: String) extends ResourcesDiractory
case class FSDirectory(path: File) extends ResourcesDiractory

case class ReverseRoute(method: String, path: String) {
  def url(pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any]): String = {
    val q = if (queryParams.isEmpty) "" else "?" + queryParams.toSeq.map(t => s"${t._1}=${t._2.toString}").mkString("&")
    pathParams.toSeq.foldLeft(path)((p, t) => p.replace(s"{${t._1}}", t._2.toString)) + q
  }
  def absoluteUrl(pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any])(implicit ctx: RequestContext): String = {
    val actualPath = url(pathParams, queryParams)
    s"${ctx.scheme}://${ctx.hostAndPort}$actualPath"
  }
  def absoluteWebSocketUrl(pathParams: Map[String, Any] = Map.empty[String, Any], queryParams: Map[String, Any] = Map.empty[String, Any])(implicit ctx: WebSocketContext): String = {
    val actualPath = url(pathParams, queryParams)
    s"${ctx.scheme}://${ctx.hostAndPort}$actualPath"
  }
}

class WebStackApp {

  private[webstack] val routingHandler = Handlers.routing()

  def route(method: HttpMethod, url: String, action: => Action[_])(implicit env: EnvLike = Env): ReverseRoute =  {
    env.logger.debug(s"Add route on ${method.value} -> $url")
    routingHandler.add(method.name, url, ReactiveActionHandler(env, action, this))
    ReverseRoute(method.value, url)
  }

  def assets(url: String, dir: ResourcesDiractory)(implicit env: EnvLike = Env): Unit = {
    dir match {
      case ClassPathDirectory(p) =>
        env.logger.debug(s"Add assets on $url -> $p")
        routingHandler.setFallbackHandler(path().addPrefixPath(url, resource(new ClassPathResourceManager(classOf[WebStackApp].getClassLoader, p))))
      case FSDirectory(p) =>
        env.logger.debug(s"Add assets on $url -> ${p.getAbsolutePath}")
        routingHandler.setFallbackHandler(path().addPrefixPath(url, resource(new FileResourceManager(p, 0))))
    }
  }

  def websocketRoute(url: String, action: => WebSocketAction)(implicit env: EnvLike = Env): ReverseRoute = {
    env.logger.debug(s"Add websocket on -> $url")
    routingHandler.add("GET", url, Handlers.websocket(new ReactiveWebSocketHandler(env, action)))
    ReverseRoute("WS", url)
  }

  def beforeStart(): Unit = {}

  def afterStart(): Unit = {}

  def beforeStop(): Unit = {}

  def afterStop(): Unit = {}

  def start(host: Option[String] = None, port: Option[Int] = None)(implicit env: EnvLike = Env): BootstrappedContext = WebStack.startWebStackApp(this, host, port, env)

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

  // TODO : implements ???
  def filters: Seq[Filter] = Seq.empty[Filter]
}

object WebStack extends App {

  Env.logger.trace("Scanning classpath looking for WebStackApp class or object")
  new Reflections("").getSubTypesOf(classOf[WebStackApp]).headOption.map { serverClazz =>
    Try {
      classOf[WebStackApp].cast(serverClazz.getField("MODULE$").get(serverClazz))
    }.toOption match {
      case Some(singleton) => {
        Env.logger.info(s"Found WebStackApp object: ${serverClazz.getName.init}")
        startWebStackApp(singleton)
      }
      case None => {
        Env.logger.info(s"Found WebStackApp class: ${serverClazz.getName}")
        val context = serverClazz.newInstance()
        startWebStackApp(context)
      }
    }
  }.getOrElse(Env.logger.error("No implementation of WebStackApp found :("))

  private[webstack] def startWebStackApp(
      webStackApp: WebStackApp,
      maybeHost: Option[String] = None,
      maybePort: Option[Int] = None,
      env: EnvLike = Env): BootstrappedContext = {
    if (env == Env) {
      // SHOULD WORKS BECAUSE AT STARTUP ONLY
      Env._app.set(webStackApp)
    }
    val port = maybePort.orElse(env.configuration.getInt("app.port")).getOrElse(9000)
    val host = maybeHost.orElse(env.configuration.getString("app.host")).getOrElse("0.0.0.0")
    env.logger.trace("Starting WebStackApp")
    val handler = webStackApp.routingHandler.setInvalidMethodHandler(new HttpHandler {
      override def handleRequest(ex: HttpServerExchange): Unit = {
        ex.setStatusCode(400)
        ex.getResponseHeaders.put(HttpString.tryFromString("Content-Type"), "application/json")
        ex.getResponseSender.send(Json.stringify(Json.obj(
          "error" -> s"Invalid Method ${ex.getRequestMethod} on uri ${ex.getRequestURI}"
        )))
      }
    })
    env.logger.trace("Starting Undertow")
    val server = Undertow
      .builder()
      .addHttpListener(port, host)
      .setHandler(handler)
      .build()
    webStackApp.beforeStart()
    server.start()
    webStackApp.afterStart()
    env.logger.trace("Undertow started")
    env.logger.info(s"Running WebStack on http://$host:$port")
    val bootstrapedContext = BootstrappedContext(server, webStackApp, env)
    env.logger.trace("Init done")
    bootstrapedContext
  }
}
