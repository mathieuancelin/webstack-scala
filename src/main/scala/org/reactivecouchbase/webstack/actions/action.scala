package org.reactivecouchbase.webstack.actions

import io.undertow.server.HttpServerExchange
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.result.{Result, Results}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Action {

  private[actions] val emptyAction = ActionStep.from { (request, block) =>
    Try(block.apply(request)) match {
      case Success(s) => s
      case Failure(e) => {
        Env.logger.error("Empty action error", e)
        Future.successful(transformError(e, request))
      }
    }
  }

  private[actions] def transformError(t: Throwable, request: RequestContext): Result = {
    Results.InternalServerError.json(Json.obj("error" -> t.getMessage)) // TODO : throwable writer
  }

  def sync(block: RequestContext => Result): Action = {
    emptyAction.sync(block)
  }

  def async(block: RequestContext => Future[Result])(implicit ec: ExecutionContext): Action = {
    emptyAction.async(block)(ec)
  }
}

class Action(actionStep: ActionStep, rcBuilder: HttpServerExchange => RequestContext, block: RequestContext => Future[Result], val ec: ExecutionContext) {
  def run(httpServerExchange: HttpServerExchange): Future[Result] = {
    implicit val e = ec
    Try {
      val rc = rcBuilder.apply(httpServerExchange)
      val result = actionStep.innerInvoke(rc, block)
      result.recoverWith {
        case t => Future.successful(Action.transformError(t, rc))
      }
    } get
  }
}

object ActionStep {
  def from(f: (RequestContext, Function[RequestContext, Future[Result]]) => Future[Result]): ActionStep = new ActionStep {
    override def invoke(request: RequestContext, block: Function[RequestContext, Future[Result]]): Future[Result] = f(request, block)
  }
}

trait ActionStep {

  def invoke(request: RequestContext, block: RequestContext => Future[Result]): Future[Result]

  def innerInvoke(request: RequestContext, block: RequestContext => Future[Result]): Future[Result] = {
    Try(this.invoke(request, block)) match {
      case Success(e) => e
      case Failure(e) => {
        Env.logger.error("innerInvoke action error", e)
        Future.successful(Action.transformError(e, request))
      }
    }
  }

  def sync(block: Function[RequestContext, Result]): Action = {
    // TODO : find a better way to pass the execution context
    implicit val ec = Env.blockingExecutor
    async { req => Future {
      Try(block.apply(req)) match {
        case Success(e) => e
        case Failure(e) => {
          Env.logger.error("Sync action error", e)
          Action.transformError(e, req)
        }
      }
    } }
  }

  def async(block: RequestContext => Future[Result])(implicit ec: ExecutionContext): Action = {
    def rcBuilder(ex: HttpServerExchange) = new RequestContext(Map.empty[String, AnyRef], ex, ec)
    new Action(this, rcBuilder, block, ec)
  }

  def combine(other: ActionStep): ActionStep = {
    val that: ActionStep = this
    ActionStep.from {(request, block) =>
      that.innerInvoke(request, r1 => other.innerInvoke(r1, block))
    }
  }

  def andThen(other: ActionStep): ActionStep = combine(other)
}