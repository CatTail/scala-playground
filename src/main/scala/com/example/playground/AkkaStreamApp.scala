package com.example.playground

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RestartSource, Sink, Source, ZipWith}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random
import scala.concurrent.duration._

trait AkkaStreamPlayground {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  def desc(title: String): Unit = {
    Thread.sleep(100)
    println()
    println(s"========== $title ==========")
    println()
  }

  def getPrint[T](name: String) = (input: T) => Future {
    Thread.sleep(Random.nextInt(10))
    println(s"$name: ${input.toString}")
    input
  }

  // throwing ArithmeticException: / by zero
  def action(input: Int) = Future {
    Thread.sleep(Random.nextInt(10))
    1 / input
    input
  }
}

object AkkaStreamApp extends App with AkkaStreamPlayground {
  Source(-5 to 5)
    .runWith(Sink.foreach(println))
}

object AkkaStreamErrorHandingApp extends App with AkkaStreamPlayground {
  desc("simple error")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .mapAsync(2)(action)
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)

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

  desc("error with broadcast flows")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .mapAsync(2)(action)
    .via(Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(ZipWith[Int, Int, Int](_+_))
      val flow1 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow1")))
      val flow2 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow2")))

      broadcast ~> flow1 ~> zip.in0
      broadcast ~> flow2 ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)

  desc("error inside one of broadcast flows")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .via(Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(ZipWith[Int, Int, Int](_+_))
      val flow1 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow1")).mapAsync(2)(action))
      val flow2 = builder.add(Flow[Int].mapAsync(2)(getPrint("flow2")))

      broadcast ~> flow1 ~> zip.in0
      broadcast ~> flow2 ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)

  desc("error inside one of broadcast flows with buffer")
  Source(-5 to 5)
    .mapAsync(2)(getPrint("before"))
    .via(Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(ZipWith[Int, Int, Int](_+_))
      val flow1 = builder.add(Flow[Int].buffer(10, OverflowStrategy.backpressure).mapAsync(2)(getPrint("flow1")).mapAsync(2)(action))
      val flow2 = builder.add(Flow[Int].buffer(10, OverflowStrategy.backpressure).mapAsync(2)(getPrint("flow2")))

      broadcast ~> flow1 ~> zip.in0
      broadcast ~> flow2 ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }))
    .log("error logging")
    .mapAsync(2)(getPrint("after"))
    .runWith(Sink.ignore)

  desc("Delayed restarts with a backoff stage")
  RestartSource.withBackoff(
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