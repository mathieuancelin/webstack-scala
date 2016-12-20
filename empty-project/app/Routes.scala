import controllers._
import org.reactivecouchbase.webstack.{ClassPathDirectory, WebStackApp}

class Routes extends WebStackApp {

  Get ->       "/" ->               HomeController.index
  Assets ->    "/assets" ->         ClassPathDirectory("public")

}