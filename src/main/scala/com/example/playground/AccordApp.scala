package com.example.playground

import com.wix.accord.dsl._
import com.wix.accord._

object AccordSimpleApp extends App {

  case class Person(firstName: String, lastName: String)

  case class Classroom(teacher: Person, students: Seq[Person])

  implicit val personValidator = validator[Person] { p =>
    p.firstName is notEmpty
    p.lastName as "last name" is notEmpty
  }

  implicit val classValidator = validator[Classroom] { c =>
    c.teacher is valid
    c.students.each is valid
    c.students have size > 0
  }

  // Validate an object successfully
  val validPerson = Person("Wernher", "von Braun")
  println(validate(validPerson))

  // Or get a detailed failure back:
  val invalidPerson = Person("", "No First Name")
  println(validate(invalidPerson))
}

object AccordConditionIssueApp extends App {

  case class Something(bool1: Boolean, bool2: Boolean, bool3: Boolean)

  val condition = true

  implicit val somethingValidator = validator[Something] { o =>
    if (condition) {
      o.bool1 is false
      o.bool2 is false
    }

    o.bool3 is false
  }

  // Following example expected to be invalid, but it turned out to be valid
  val invalidSomething = Something(bool1 = true, bool2 = false, bool3 = false)
  println(validate(invalidSomething))
}
