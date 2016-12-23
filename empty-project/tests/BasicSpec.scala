import java.util.concurrent.TimeUnit

import org.reactivecouchbase.webstack.{BootstrappedContext, ClassPathDirectory, WebStackApp}
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.ws.WS
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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
  val port = 7002

  override protected def beforeAll(): Unit = {
    server = Routes.start(port = Some(port))
  }

  override protected def afterAll(): Unit = {
    server.stop
  }

  "My app" should "be return a homepage" in {
    val future = for {
      resp     <- WS.host("http://localhost", port).withPath("/").call()
      body     <- resp.body
    } yield (resp.status, resp.header("Content-Type").getOrElse("none"))
    val (status, contentType) = future.await
    assert(status == 200)
    assert(contentType == "text/html")
  }

}