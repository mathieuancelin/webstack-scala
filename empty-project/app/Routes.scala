import controllers._
import org.reactivecouchbase.webstack.{ClassPathDirectory, WebStackApp}

object Routes extends WebStackApp {

  Get    →       "/"       →         HomeController.index
  Assets →       "/assets" →         ClassPathDirectory("public")

}