package com.example.playground

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import akka.routing.{Broadcast, RoundRobinGroup, RoundRobinPool}

object AkkaRouting extends App with CommonContext {
  system.actorOf(Props[PoolMaster])
  system.actorOf(Props[GroupMaster])
}

class PoolMaster extends Actor {
  val router: ActorRef = context.actorOf(RoundRobinPool(5).props(Props[Worker]), "router")

  override def preStart(): Unit = {
    router ! "hello"
    router ! Broadcast("broadcast hello")
  }

  override def receive: Receive = LoggingReceive(Actor.emptyBehavior)
}

class GroupMaster extends Actor {
  val routees = List.fill(5) {
    val r = context.actorOf(Props[Worker])
    context.watch(r)
  }
  val router: ActorRef = context.actorOf(RoundRobinGroup(routees.map(_.path.toString)).props(), "router")

  override def preStart(): Unit = {
    router ! "hello"
    router ! Broadcast("broadcast hello")
  }

  override def receive: Receive = LoggingReceive(Actor.emptyBehavior)
}

class Worker extends Actor {
  override def receive: Receive = LoggingReceive {
    case msg => sender() ! s"receive $msg"
  }
}
