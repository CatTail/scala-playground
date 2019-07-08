package com.example.playground

import akka.event.LoggingAdapter
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, MergeHub, RestartSource, Sink, Source, ZipWith}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object AkkaStreamApp extends App with CommonContext {
  Source(-5 to 5)
    .runWith(Sink.foreach(println))
}

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
      val flow1 = builder.add(Flow[Int].buffer(10, OverflowStrategy.backpressure).mapAsync(2)(getPrint("flow1")).mapAsync(2)(action))
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

object SourceQueueApp extends App with CommonContext {
  desc("throw exception if offer element to failed source queue")
  val queue = Source.queue[Int](1, OverflowStrategy.backpressure)
    .map { value =>
      if (value == 42) {
        throw new RuntimeException("Oops")
      }
      value
    }
    .to(Sink.foreach(println)).run()
  Await.result(queue.offer(1), Duration.Inf)
  Await.result(queue.offer(42), Duration.Inf)
  Await.result(queue.offer(3), Duration.Inf)
}

object SinkQueueApp extends App with CommonContext {
  desc("return stream result as interable")
  val queue = Source(1 to 10)
    .toMat(Sink.queue())(Keep.right)
    .run()
  val iterable = new Iterable[Int] {
    override def iterator: Iterator[Int] = new Iterator[Int] {
      var cursor: Option[Int] = Await.result(queue.pull(), Duration.Inf)

      override def hasNext: Boolean = cursor.isDefined

      override def next(): Int = {
        val oldCursor = cursor
        cursor = Await.result(queue.pull(), Duration.Inf)
        oldCursor.get
      }
    }
  }
  println(iterable.toList)
}

object CustomLoggingAdaptor extends App with CommonContext {
  desc("send error metrics to somewhere")

  Source(-5 to 5)
    .mapAsync(2)(action)
    .log("error logging")(new ErrorLoggingAdapter())
    .runWith(Sink.ignore)
}

class ErrorLoggingAdapter() extends LoggingAdapter {
  override def isErrorEnabled: Boolean = true

  override def isWarningEnabled: Boolean = false

  override def isInfoEnabled: Boolean = false

  override def isDebugEnabled: Boolean = false

  override protected def notifyError(message: String): Unit = {
    println(s"got error message $message")
  }

  override protected def notifyError(cause: Throwable, message: String): Unit = {
    println(s"got error message $message with throwable cause")
  }

  override protected def notifyWarning(message: String): Unit = {

  }

  override protected def notifyInfo(message: String): Unit = {

  }

  override protected def notifyDebug(message: String): Unit = {

  }
}
