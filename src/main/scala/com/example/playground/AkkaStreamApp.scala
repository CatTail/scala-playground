package com.example.playground

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source, ZipWith}

import scala.concurrent.Future
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

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
    .mapAsync(2)(action) // throwing ArithmeticException: / by zero
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
}

/*
========== simple error ==========

before: -5
before: -3
before: -4
before: -1
after: -5
before: -2
after: -4
before: 0
before: 1
after: -2
before: 2
after: -3
before: 3
after: -1
[ERROR] [06/06/2018 22:17:07.890] [QuickStart-akka.actor.default-dispatcher-4] [akka.stream.Log(akka://QuickStart/system/StreamSupervisor-0)] [error logging] Upstream failed.
java.lang.ArithmeticException: / by zero
	at com.example.playground.AkkaStreamPlayground.$anonfun$action$1(AkkaStreamApp.scala:30)
	at scala.runtime.java8.JFunction0$mcI$sp.apply(JFunction0$mcI$sp.java:12)
	at scala.concurrent.Future$.$anonfun$apply$1(Future.scala:655)
	at scala.util.Success.$anonfun$map$1(Try.scala:251)
	at scala.util.Success.map(Try.scala:209)
	at scala.concurrent.Future.$anonfun$map$1(Future.scala:289)
	at scala.concurrent.impl.Promise.liftedTree1$1(Promise.scala:29)
	at scala.concurrent.impl.Promise.$anonfun$transform$1(Promise.scala:29)
	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
	at scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask.exec(ExecutionContextImpl.scala:140)
	at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
	at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
	at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
	at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)


========== error with recover ==========

before: -5
before: -4
before: -2
before: -3
after: -5
after: -3
after: -4
before: 0
before: -1
before: 1
after: -2
before: 2
after: 9999999

========== error with broadcast flows ==========

before: -4
before: -5
before: -3
flow2: -5
before: -2
before: -1
flow1: -5
flow1: -4
flow1: -3
before: 0
flow2: -4
after: -8
flow2: -3
after: -10
flow1: -2
after: -6
flow2: -2
before: 1
[ERROR] [06/06/2018 22:17:08.122] [QuickStart-akka.actor.default-dispatcher-4] [akka.stream.Log(akka://QuickStart/system/StreamSupervisor-0)] [error logging] Upstream failed.
java.lang.ArithmeticException: / by zero
	at com.example.playground.AkkaStreamPlayground.$anonfun$action$1(AkkaStreamApp.scala:30)
	at scala.runtime.java8.JFunction0$mcI$sp.apply(JFunction0$mcI$sp.java:12)
	at scala.concurrent.Future$.$anonfun$apply$1(Future.scala:655)
	at scala.util.Success.$anonfun$map$1(Try.scala:251)
	at scala.util.Success.map(Try.scala:209)
	at scala.concurrent.Future.$anonfun$map$1(Future.scala:289)
	at scala.concurrent.impl.Promise.liftedTree1$1(Promise.scala:29)
	at scala.concurrent.impl.Promise.$anonfun$transform$1(Promise.scala:29)
	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
	at scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask.exec(ExecutionContextImpl.scala:140)
	at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
	at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
	at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
	at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)

flow2: -1
before: 2
flow1: -1

========== error inside one of broadcast flows ==========

before: -5
flow1: -5
before: -3
before: -4
flow1: -4
before: -2
flow2: -4
flow2: -5
flow2: -3
flow1: -3
before: 0
flow1: -2
after: -10
before: -1
after: -8
after: -6
flow1: -1
flow2: -2
before: 1
flow2: 0
flow2: -1
flow1: 1
after: -2
flow1: 0
flow2: 1
after: -4
before: 2
before: 3
[ERROR] [06/06/2018 22:17:08.230] [QuickStart-akka.actor.default-dispatcher-2] [akka.stream.Log(akka://QuickStart/system/StreamSupervisor-0)] [error logging] Upstream failed.
java.lang.ArithmeticException: / by zero
	at com.example.playground.AkkaStreamPlayground.$anonfun$action$1(AkkaStreamApp.scala:30)
	at scala.runtime.java8.JFunction0$mcI$sp.apply(JFunction0$mcI$sp.java:12)
	at scala.concurrent.Future$.$anonfun$apply$1(Future.scala:655)
	at scala.util.Success.$anonfun$map$1(Try.scala:251)
	at scala.util.Success.map(Try.scala:209)
	at scala.concurrent.Future.$anonfun$map$1(Future.scala:289)
	at scala.concurrent.impl.Promise.liftedTree1$1(Promise.scala:29)
	at scala.concurrent.impl.Promise.$anonfun$transform$1(Promise.scala:29)
	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
	at scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask.exec(ExecutionContextImpl.scala:140)
	at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
	at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
	at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
	at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)


========== error inside one of broadcast flows with buffer ==========

before: -5
before: -3
before: -4
flow2: -5
flow1: -5
flow1: -4
flow2: -4
before: -2
after: -10
before: -1
flow2: -3
flow1: -3
after: -8
before: 1
flow2: -2
before: 0
flow2: -1
flow1: -2
flow1: -1
after: -6
before: 3
before: 2
flow1: 0
flow1: 1
flow2: 0
[ERROR] [06/06/2018 22:17:08.348] [QuickStart-akka.actor.default-dispatcher-4] [akka.stream.Log(akka://QuickStart/system/StreamSupervisor-0)] [error logging] Upstream failed.
java.lang.ArithmeticException: / by zero
	at com.example.playground.AkkaStreamPlayground.$anonfun$action$1(AkkaStreamApp.scala:30)
	at scala.runtime.java8.JFunction0$mcI$sp.apply(JFunction0$mcI$sp.java:12)
	at scala.concurrent.Future$.$anonfun$apply$1(Future.scala:655)
	at scala.util.Success.$anonfun$map$1(Try.scala:251)
	at scala.util.Success.map(Try.scala:209)
	at scala.concurrent.Future.$anonfun$map$1(Future.scala:289)
	at scala.concurrent.impl.Promise.liftedTree1$1(Promise.scala:29)
	at scala.concurrent.impl.Promise.$anonfun$transform$1(Promise.scala:29)
	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
	at scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask.exec(ExecutionContextImpl.scala:140)
	at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
	at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
	at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
	at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)

after: -4
flow1: 2
before: 4
flow2: 1
before: 5

 */