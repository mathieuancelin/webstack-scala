package org.reactivecouchbase.webstack.websocket

import akka.NotUsed
import akka.actor._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import org.reactivestreams.Publisher

object ActorFlow {

  def actorRef[In, Out](props: ActorRef => Props, bufferSize: Int = 1000, overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew, fanout: Boolean = false)(implicit factory: ActorRefFactory, mat: Materializer): Flow[In, Out, NotUsed] = {
    val (ref, publisher) = Source.actorRef[Out](bufferSize, overflowStrategy)
      .toMat(Sink.asPublisher(fanout))(Keep.both[ActorRef, Publisher[Out]])
      .run()
    Flow.fromSinkAndSource(
      Sink.actorRef(
        factory.actorOf(Props[WebsocketFlowActor](new WebsocketFlowActor(props, ref))),
        Status.Success("")
      ),
      Source.fromPublisher(publisher)
    )
  }

  private class WebsocketFlowActor(props: ActorRef => Props, ref: ActorRef) extends Actor {

    val flowActor = context.watch(context.actorOf(props.apply(ref), "flowActor"))

    override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

    override def receive: Receive = {
      case e: Status.Success => flowActor.tell(PoisonPill.getInstance, self)
      case t: Terminated => {
        context.stop(self)
        flowActor.tell(PoisonPill.getInstance, self)
      }
      case m => flowActor.tell(m, self)
    }
  }
}