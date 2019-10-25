package com.example.playground

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import akka.routing.RoundRobinPool

object AkkaRouting extends App with CommonContext {
  val master = system.actorOf(Props[Master])
}

class Master extends Actor {
  val router: ActorRef = context.actorOf(RoundRobinPool(5).props(Props[Worker]), "router")

  override def preStart(): Unit = {
    router ! "hello"
  }

  override def receive: Receive = LoggingReceive(Actor.emptyBehavior)
}

class Worker extends Actor {
  override def receive: Receive = LoggingReceive {
    case msg => sender() ! s"receive $msg"
  }
}
