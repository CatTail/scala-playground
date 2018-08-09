package com.example.playground

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object AwaitApp extends App with Utils {
  desc("Await.ready don't throw exception on failure")
  println(Await.ready(Future {
    throw new RuntimeException("Oops")
  }, Duration.Inf))

  desc("Await.result throw exception on failure")
  Await.result(Future {
    throw new RuntimeException("Oops")
  }, Duration.Inf)
}