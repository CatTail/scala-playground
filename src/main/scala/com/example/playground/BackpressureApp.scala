package com.example.playground

import akka.stream.{Attributes, OverflowStrategy, ThrottleMode}
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.duration._

object BackpressureApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .throttle(1, 1.second, 0, ThrottleMode.Shaping)
    .to(Sink.foreach(println(_)))
    .run()
}

object BackpressureWithBufferApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .buffer(100, OverflowStrategy.backpressure)
    .throttle(1, 1.second, 0, ThrottleMode.Shaping)
    .to(Sink.foreach(println(_)))
    .run()
}

object BackpressureWithAsyncBoundaryApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .async
    .throttle(1, 1.second, 0, ThrottleMode.Shaping)
    .to(Sink.foreach(println(_)))
    .run()
}

object BackpressureWithCustomAsyncBoundaryApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .async
    .throttle(1, 1.second, 0, ThrottleMode.Shaping)
    .addAttributes(Attributes.inputBuffer(initial = 1, max = 128))
    .to(Sink.foreach(println(_)))
    .run()
}

object BlockingBackpressureApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .map { i =>
      Thread.sleep(1000)
      i
    }
    .to(Sink.foreach(println(_)))
    .run()
}

object BlockingBackpressureWithBufferApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .buffer(100, OverflowStrategy.backpressure)
    .map { i =>
      Thread.sleep(1000)
      i
    }
    .to(Sink.foreach(println(_)))
    .run()
}

object BlockingBackpressureWithAsyncBoundaryApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .buffer(100, OverflowStrategy.backpressure)
    .async
    .map { i =>
      Thread.sleep(1000)
      i
    }
    .to(Sink.foreach(println(_)))
    .run()
}

object BlockingBackpressureWithAsyncBoundary2App extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .buffer(100, OverflowStrategy.backpressure)
    .map { i =>
      Thread.sleep(1000)
      i
    }
    .async
    .to(Sink.foreach(println(_)))
    .run()
}

object BlockingBackpressureWithCustomAsyncBoundaryApp extends App with CommonContext {
  Source(1 to 100)
    .via(printlnFlow("before"))
    .async
    .map { i =>
      Thread.sleep(1000)
      i
    }
    .addAttributes(Attributes.inputBuffer(initial = 1, max = 128))
    .to(Sink.foreach(println(_)))
    .run()
}

//  Source(1 to 100)
//    .alsoTo(Flow[Int]
//      .map(i => {
//        println("before", i)
//        i
//      })
//      .buffer(100, OverflowStrategy.backpressure)
//      .async
//      .map(i => {
//        Thread.sleep(1000)
//        i
//      })
////      .addAttributes(Attributes.inputBuffer(initial = 1, max = 128))
//      .to(Sink.foreach(println(_)))
//    )
//    .to(Sink.foreach(println(_)))
//    .run()
