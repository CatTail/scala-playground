package com.example.playground

import akka.event.LoggingAdapter
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.concurrent.Await
import scala.concurrent.duration._

object AkkaStreamApp extends App with CommonContext {
  Source(-5 to 5)
    .runWith(Sink.foreach(println))
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
