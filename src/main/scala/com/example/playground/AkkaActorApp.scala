package com.example.playground

import akka.actor.{Actor, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.util.Timeout

import scala.concurrent.duration._

class Child extends Actor {
  var state = 0

  override def preStart(): Unit = {
    super.preStart()

    println("starting child actor")
  }

  def receive = {
    case ex: Exception => throw ex
    case x: Int        => state = x
    case "get"         => sender() ! state
  }

  override def postStop(): Unit = {
    println("child actor stopped")

    super.postStop()
  }
}

object BackoffSupervisorApp extends App with CommonContext {
  implicit val timeout = Timeout(3.seconds)
  val minBackoff = 100.millis
  val maxBackoff = 800.millis
  val maxRetries = 1
  val resetBackoff = 16.seconds
  val props = Props(new Child())
  val backoffProps = BackoffSupervisor.props(
    Backoff
      .onFailure(props, "child", minBackoff, maxBackoff, 0.2, maxRetries)
      .withManualReset
      .withSupervisorStrategy(OneForOneStrategy(maxRetries, resetBackoff)(SupervisorStrategy.defaultDecider))
  )
  val supervisor = system.actorOf(backoffProps, "supervisor")

  Thread.sleep(2000)
  supervisor ! new RuntimeException("Oops1")
  Thread.sleep(2000)
  supervisor ! new RuntimeException("Oops2")
  Thread.sleep(2000)
  supervisor ! new RuntimeException("Oops3")
}
