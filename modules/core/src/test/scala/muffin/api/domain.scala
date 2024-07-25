package muffin.api

object domain {

  case class TestObject(field: String)

  object TestObject {
    val default: TestObject = TestObject("default")
  }

}
