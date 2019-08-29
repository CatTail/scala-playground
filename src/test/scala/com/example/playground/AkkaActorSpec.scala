package com.example.playground

import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
import org.scalatest._

import scala.concurrent.duration._

object TestActor {

  class TestException(msg: String) extends Exception(msg)

  class StoppingException extends TestException("stopping exception")

  class NormalException extends TestException("normal exception")

  def props(probe: ActorRef): Props = Props(new TestActor(probe))
}

class TestActor(probe: ActorRef) extends Actor {

  probe ! "STARTED"

  def receive = {
    case "DIE"                      => context.stop(self)
    case "THROW"                    => throw new TestActor.NormalException
    case "THROW_STOPPING_EXCEPTION" => throw new TestActor.StoppingException
    case ("TO_PARENT", msg)         => context.parent ! msg
    case other                      => probe ! other
  }
}

class AkkaActorSpec
    extends TestKit(ActorSystem("MySpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "An Echo actor" should {
    "send back messages unchanged" in {
      val echo = system.actorOf(TestActors.echoActorProps)
      echo ! "hello world"
      expectMsg("hello world")
    }
  }

  "An BackoffSupervisor" should {
    "terminate when exceed maxRestarts" in {
      val probe = TestProbe()

      val backoffProps = BackoffSupervisor.props(
        Backoff
          .onFailure(TestActor.props(probe.ref), "child", 100.millis, 800.millis, 0.0, -1)
          .withSupervisorStrategy(OneForOneStrategy(3, 16.seconds)(SupervisorStrategy.defaultDecider))
      )
      val supervisor = system.actorOf(backoffProps, "supervisor")
      probe.watch(supervisor)

      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      probe.expectTerminated(supervisor)
    }

    "withinTimeRange not working as expected" in {
      val probe = TestProbe()

      val backoffProps = BackoffSupervisor.props(
        Backoff
          .onFailure(TestActor.props(probe.ref), "child", 100.millis, 800.millis, 0.0, -1)
          .withSupervisorStrategy(OneForOneStrategy(3, 1.milli)(SupervisorStrategy.defaultDecider))
      )
      val supervisor = system.actorOf(backoffProps, "supervisor")
      probe.watch(supervisor)

      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      probe.expectMsg("STARTED")
      supervisor ! "THROW"
      // we should not terminate here because it intended to be reset after 1 millisecond but it't not
      probe.expectTerminated(supervisor)
    }
  }

}
