package com.example.playground

import akka.actor.{Actor, Props, Terminated}

import scala.concurrent.duration._

object AkkaActorApp extends App with CommonContext {
  case object Ping

  class DBSupervisor extends Actor {
    import system.dispatcher

    val child = context watch (context.actorOf(Props(new DBWorker()), "DBWorker"))
    system.scheduler.schedule(10.seconds, 10.seconds, child, Ping)

    override def preStart(): Unit = println("DBSupervisor starting")

    override def receive: Receive = {
      case Terminated(`child`) =>
        println("child db worker terminated")
        context stop self
      case msg =>
        println(s"get unknown message $msg")
    }

    override def postStop(): Unit = println("DBSupervisor stopped")
  }

  class DBWorker extends Actor {
    override def preStart(): Unit = println("DBWorker starting")
    override def receive: Receive = {
      case Ping => throw new RuntimeException("Oops")
    }
    override def postStop(): Unit = println("DBWorker stopped")
  }

  system.actorOf(Props(new DBSupervisor()), "DBSupervisor")
}
