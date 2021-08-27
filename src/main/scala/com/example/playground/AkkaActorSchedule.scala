package com.example.playground

import akka.actor.{Actor, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._

object AkkaActorSchedule extends App with CommonContext {
  case object Ping

  class Worker extends Actor {
    import system.dispatcher

    val cancellable = system.scheduler.schedule(0.seconds, 1.seconds, self, Ping)

    var count = 0

    override def preStart(): Unit = {
      println("Worker starting")
    }

    override def receive: Receive = {
      case Ping =>
        count = count + 1
        println(s"pong $count")
        if (count == 10) context.stop(self)
    }

    override def postStop(): Unit = {
      println("Worker stopped")
      cancellable.cancel()
    }
  }

  val supervisor =
    BackoffSupervisor.props(Backoff.onStop(Props(new Worker()), "worker", 2.seconds, 16.seconds, 0.2).withManualReset)
  system.actorOf(supervisor, "supervisor")
}
