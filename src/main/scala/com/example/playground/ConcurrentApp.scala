package com.example.playground

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Success, Failure}
import scala.concurrent.duration._

object AwaitApp extends App with CommonContext {
  desc("Await.ready don't throw exception on failure")
  println(Await.ready(Future {
    throw new RuntimeException("Oops")
  }, Duration.Inf))

  desc("Await.result throw exception on failure")
  Await.result(Future {
    throw new RuntimeException("Oops")
  }, Duration.Inf)
}

object FutureTransform extends App with CommonContext {
  desc("Future.transform should able to convert Failure to Success")
  val future = Future {
    throw new Exception("Oops")
    "hello world"
  }

  val newFuture = future.transform {
    case Failure(exception) => Success(exception.getMessage)
    case result             => Success(result)
  }

  println(Await.result(newFuture, 1.second))
}
