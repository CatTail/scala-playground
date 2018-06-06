package com.example.playground

import org.scalatest._

class SimpleAppTest extends FlatSpec with Matchers {

  "The Answer" should "always be 42" in {
    Quiz.theAnswer should be (42)
  }

}
