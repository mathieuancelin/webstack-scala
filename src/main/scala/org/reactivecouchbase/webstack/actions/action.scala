package org.reactivecouchbase.webstack.actions

import io.undertow.server.HttpServerExchange
import org.reactivecouchbase.webstack.env.{Env, EnvLike}
import org.reactivecouchbase.webstack.result.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Action {

  private[actions] val emptyAction = ActionStep[RequestContext] { (request, block) =>
    Try(block(request)) match {
      case Success(s) => s
      case Failure(e) => {
        request.env.logger.error("Empty action error", e)
        Future.successful(transformError(e, request.env))
      }
    }
  }

  private[actions] def transformError(t: Throwable, env: EnvLike): Result = {
    Results.InternalServerError.json(env.throwableWriter.writes(t))
  }

  def sync(block: RequestContext => Result)(implicit env: EnvLike = Env): Action[RequestContext] = {
    emptyAction.sync(block)(env)
  }

  def async(block: RequestContext => Future[Result])(implicit env: EnvLike = Env, ec: ExecutionContext): Action[RequestContext] = {
    emptyAction.async(block)(env, ec)
  }
}

class Action[A](
    private[webstack] val actionStep: ActionStep[A],
    private[webstack] val rcBuilder: HttpServerExchange => RequestContext,
    private[webstack] val block: A => Future[Result],
    private[webstack] val ec: ExecutionContext) {

  def run(httpServerExchange: HttpServerExchange): Future[Result] = {
    implicit val e = ec
    val rc = rcBuilder.apply(httpServerExchange)
    val result = actionStep.innerInvoke(rc, block)
    result.recoverWith {
      case t => Future.successful(Action.transformError(t, rc.env))
    }
  }
}

object ActionStep {
  private val FUTURE = Future.successful(Unit)
  def apply[A](f: (RequestContext, A => Future[Result]) => Future[Result]): ActionStep[A] = new ActionStep[A] {
    override def invoke(ctx: RequestContext, block: A => Future[Result]): Future[Result] = {
      // PERFS ???
      FUTURE.flatMap(_ => f(ctx, block))(ctx.currentExecutionContext)
    }
  }
}

trait ActionStep[A] {

  def invoke(request: RequestContext, block: A => Future[Result]): Future[Result]

  private[webstack] def innerInvoke(request: RequestContext, block: A => Future[Result]): Future[Result] = {
    Try(invoke(request, block)) match {
      case Success(e) => e
      case Failure(e) => {
        request.env.logger.error("innerInvoke action error", e)
        Future.successful(Action.transformError(e, request.env))
      }
    }
  }

  def sync(block: A => Result)(implicit env: EnvLike = Env): Action[A] = {
    implicit val ec = env.blockingExecutionContext
    async { req =>
      Future {
        Try(block(req)) match {
          case Success(e) => e
          case Failure(e) => {
            env.logger.error("Sync action error", e)
            Action.transformError(e, env)
          }
        }
      }
    }
  }

  def async(block: A => Future[Result])(implicit env: EnvLike = Env, ec: ExecutionContext): Action[A] = {
    def rcBuilder(ex: HttpServerExchange) = new RequestContext(Map.empty[String, AnyRef], ex, env, ec)
    new Action[A](this, rcBuilder, block, ec)
  }

  def combine[B](other: ActionStep[B]): ActionStep[B] = {
    val that: ActionStep[A] = this
    ActionStep[B] { (request, block) =>
      that.innerInvoke(request, r1 => other.innerInvoke(request, block))
    }
  }

  def andThen[B](other: ActionStep[B]): ActionStep[B]  = combine(other)
  def ~>[B](other: ActionStep[B]): ActionStep[B]  = combine(other)

  private[webstack] def combine[B](action: Action[B]): Action[B] = {
    val step = this.combine(action.actionStep)
    new Action[B](step, action.rcBuilder, action.block, action.ec)
  }
}
