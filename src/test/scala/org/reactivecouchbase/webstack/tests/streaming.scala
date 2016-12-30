package org.reactivecouchbase.webstack.tests

import akka.stream.scaladsl.Framing
import akka.util.ByteString
import org.reactivecouchbase.webstack.WebStackApp
import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.mvc.Controller
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

object StreamingRoutes extends WebStackApp with App {
  Post -> "/csv" -> StreamingController.processCsv
  Post -> "/csv2array" -> StreamingController.processCsvAsJsonArray
  start(port = Some(8888))
}

object StreamingController extends Controller {

  implicit val ec  = Env.defaultExecutionContext
  implicit val mat = Env.defaultMaterializer

  // curl -X POST --data-binary @/tmp/bigfile.txt -H "Content-Type: text/csv" http://localhost:8888/csv
  def processCsv = Action.sync { ctx =>
    // stream in and process
    val source = ctx.bodyAsStream
      .via(Framing.delimiter(ByteString("\n"), 1000))
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
    Ok.stream(source).as("application/json-stream")
  }

  def processCsvAsJsonArray = Action.sync { ctx =>
    // stream in and process
    val source = ctx.bodyAsStream
      .via(Framing.delimiter(ByteString("\n"), 1000))
      .drop(1)
      .map(_.utf8String)
      .map(_.split(";").toSeq)
      .collect {
        case Seq(id, name, email, address, phone) => User(id, name, email, address, phone)
      }
      .map(_.toJson)
      .map(Json.stringify)
      .intersperse("[", ",\n", "]")
    // stream out
    Ok.stream(source).as("application/json")
  }
}