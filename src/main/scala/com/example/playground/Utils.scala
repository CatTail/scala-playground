package com.example.playground

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

trait Utils {
  implicit val system: ActorSystem = ActorSystem("QuickStart")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

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

  def printlnFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(i => {
    println(i)
    i
  })
}
