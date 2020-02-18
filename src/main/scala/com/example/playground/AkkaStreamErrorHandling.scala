package com.example.playground

import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.stream.Supervision.{Resume, Stop}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RestartSource, Sink, Source, Zip, ZipWith}
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision.resumingDecider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

object SimpleErrorApp extends App with CommonContext {
  desc("simple error")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .mapAsync(2)(action)
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object ErrorWithRecoverApp extends App with CommonContext {
  desc("error with recover")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .mapAsync(2)(action)
    .recover {
      case _: RuntimeException => 9999999
    }
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object ErrorWithRecoverAndResumeApp extends App with CommonContext {
  desc("error with recover and resume")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .mapAsync(2)(action)
    .recover {
      case _: RuntimeException => 9999999
    }
    .withAttributes(supervisionStrategy(resumingDecider))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object ErrorWithBroadcastFlowsApp extends App with CommonContext {
  desc("error with broadcast flows")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .mapAsync(2)(action)
    .via(Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(ZipWith[Int, Int, Int](_ + _))
      val flow1 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow1")))
      val flow2 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow2")))

      broadcast ~> flow1 ~> zip.in0
      broadcast ~> flow2 ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object ErrorInsideBroadcastFlowsApp extends App with CommonContext {
  desc("error inside one of broadcast flows")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .via(Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(ZipWith[Int, Int, Int](_ + _))
      val flow1 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow1")).mapAsync(2)(action))
      val flow2 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow2")))

      broadcast ~> flow1 ~> zip.in0
      broadcast ~> flow2 ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object ErrorInsideBroadcastFlowsWithBufferApp extends App with CommonContext {
  desc("error inside one of broadcast flows with buffer")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .via(Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(ZipWith[Int, Int, Int](_ + _))
      val flow1 = builder.add(
        Flow[Int].buffer(10, OverflowStrategy.backpressure).mapAsync(2)(getPrint("flow1")).mapAsync(2)(action)
      )
      val flow2 = builder.add(Flow[Int].buffer(10, OverflowStrategy.backpressure).mapAsync(2)(getPrint("flow2")))

      broadcast ~> flow1 ~> zip.in0
      broadcast ~> flow2 ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object DelayedRestartWithBackoffStageApp extends App with CommonContext {
  desc("Delayed restarts with a backoff stage")
  RestartSource
    .withBackoff(
      minBackoff = 100.milliseconds,
      maxBackoff = 5000.milliseconds,
      randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
      maxRestarts = 5 // limits the amount of restarts to 20
    ) { () ⇒
      // Create a source from a future of a source
      Source(-5 to 5)
        .mapAsync(2)(getPrint("before"))
        .mapAsync(2)(action)
        .log("error logging")
    }
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object MaxRestartsApplyToMinBackoffApp extends App with CommonContext {
  desc("MaxRestarts only apply to minBackoff duration, larger duration will reset counter")

  val shouldThrowException = new AtomicBoolean(false)

  RestartSource
    .withBackoff(
      minBackoff = 100.milliseconds,
      maxBackoff = 5000.milliseconds,
      randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
      maxRestarts = 5 // limits the amount of restarts to 20
    ) { () ⇒
      // Create a source from a future of a source
      Source(Stream.from(1))
        .mapAsync(2) { num =>
          Future {
            Thread.sleep(Random.nextInt(10))
            if (shouldThrowException.getAndSet(false)) {
              throw new RuntimeException("Oops")
            }
            num
          }
        }
        .log("error logging")
    }
    .runWith(Sink.ignore)

  // throw exception every second
  system.scheduler.schedule(0.second, 1.second) {
    shouldThrowException.set(true)
  }
}

// recover element will lost when working with supervision strategy
object RecoverWithSupervisionStrategyApp extends App with CommonContext {
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .mapAsync(2)(action)
    .recover {
      case _ => 99999
    }
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)
}

object ErrorBetweenBroadcastAndZipApp extends App with CommonContext {
  desc("SourceQueue start to backpressure after failure even with Resume supervision strategy")
  var input = 0
  val isEndOfStream = new AtomicBoolean(false)

  val queue = Source
    .queue[Int](1000, OverflowStrategy.backpressure)
    .via(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      val `{` = b.add(Broadcast[Int](2))
      val `}` = b.add(Zip[Done, Int])
      val `=` = b.add(Flow[Int])

      `{`.out(0)
        .mapAsync(1)(
          shardId =>
            Future {
              Thread.sleep(Random.nextInt(10))
              if (isEndOfStream.getAndSet(false)) throw new ArithmeticException
              else Done
          }
        ) ~> `}`.in0
      `{`.out(1) ~> `}`.in1

      `}`.out.map(_._2) ~> `=`

      FlowShape(`{`.in, `=`.out)
    })
    .log("error logging")
    .withAttributes(ActorAttributes.supervisionStrategy {
      case ex: ArithmeticException =>
        println("ArithmeticException")
        Resume
      case ex =>
        println("Other Exception")
        Stop
    })
    .to(Sink.foreach(println))
    .run()

  while (true) {
    Await.result(queue.offer(input), 10.seconds)
    input = input + 1
    if (input == 1000) {
      isEndOfStream.set(true)
    }
  }
}
