package com.example.playground

import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{MergeHub, Sink, Source}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object AlsoToContext extends CommonContext {
  val sink: Sink[Int, NotUsed] = MergeHub
    .source[Int]
    .mapAsync(1)(action)
    .log("MergeHub")
    .to(Sink.foreach(value => println(s"MergeHub: $value")))
    .run()
}

object AlsoToMergeHubApp extends App with CommonContext {
  import AlsoToContext._

  desc("consume element with two sinks")
  Source(1 to 3)
    .alsoTo(sink)
    .log("Main")
    .to(Sink.foreach(println(_)))
    .run()
}

object AlsoToFailedMergeHubApp extends App with CommonContext {
  import AlsoToContext._

  desc("fail merge sink")
  Source(-5 to 5)
    .alsoTo(sink)
    .log("Main")
    .to(Sink.foreach(println(_)))
    .run()

  desc("alsoTo an canceled sink")
  val queue = Source
    .queue(10, OverflowStrategy.backpressure)
    .alsoTo(sink)
    .log("Main")
    .to(Sink.foreach(println(_)))
    .run()

  Await.result(queue.offer(1), Duration.Inf)
  Await.result(queue.offer(2), Duration.Inf)
  Await.result(queue.offer(3), Duration.Inf)
}
