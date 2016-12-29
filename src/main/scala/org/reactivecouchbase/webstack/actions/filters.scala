package org.reactivecouchbase.webstack.actions

import org.reactivecouchbase.webstack.result.Result

import scala.concurrent.Future

object Filter {
  def apply(process: (RequestContext, (RequestContext) => Future[Result]) => Future[Result]): Filter = new Filter {
    override def apply(ctx: RequestContext, nextFilter: (RequestContext) => Future[Result]): Future[Result] = process(ctx, nextFilter)
  }
}

trait Filter {
  def matches(ctx: RequestContext): Boolean = true
  def apply(ctx: RequestContext, nextFilter: RequestContext => Future[Result]): Future[Result]
}

object FilterAction {
  def apply(filters: Filter*): FilterAction = new FilterAction(filters)
}

class FilterAction(filters: Seq[Filter]) extends ActionStep[RequestContext] {

  private[webstack] def runFilter(ctx: RequestContext, block: RequestContext => Future[Result], remaining: Seq[Filter]): Future[Result] = {
    val tail = if (remaining.isEmpty) Seq.empty else remaining.tail
    remaining.headOption match {
      case None => block(ctx)
      case Some(head) if !head.matches(ctx) => runFilter(ctx, block, tail)
      case Some(head) if head.matches(ctx) => {
        head.apply(ctx, rc => {
          runFilter(rc, block, tail)
        })
      }
    }
  }

  override def invoke(request: RequestContext, block: RequestContext => Future[Result]): Future[Result] = {
    if (filters.isEmpty) {
      block(request)
    } else {
      runFilter(request, block, filters)
    }
  }
}