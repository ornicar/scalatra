package org.scalatra

import test.scalatest.ScalatraFunSuite

class UrlGeneratorTest extends ScalatraFunSuite {

  addServlet(classOf[UrlGeneratorTestServlet], "/*")

  object TestUrlGenerator extends UrlGenerator
  import TestUrlGenerator._

  object TestServlet extends UrlGeneratorTestServlet
  import TestServlet._

  test("Simple string route reverses to the same url") {
    generate(simpleString) should equal ("/foo")
  }

  test("Route with one named parameter replaces the parameter") {
    generate(singleNamed, "bar" -> "bryan") should equal ("/foo/bryan")
  }

  test("Route with two named parameters replaces both parameters") {
    generate(multipleNameds, "bar" -> "moonless", "rebar" -> "midnight") should equal ("/foo/moonless/and/midnight")
  }

  test("Named of path containing a dot get replaced anyway") {
    generate(dotOutsideNamedParam, "file" -> "TroutLauncher", "ext" -> "scala") should equal ("/dot-outside-named-param/TroutLauncher.scala")
  }

  test("Missing parameter produces an exception") {
    evaluating {
      generate(multipleNameds, "bar" -> "moonless")
    } should produce [Exception]
  }

  test("Unexpected parameters are just ignored at the moment") {
    generate(singleNamed, "bar" -> "pepper", "unexpected" -> "surprise") should equal ("/foo/pepper")
  }

  test("One splat parameter gets replaced") {
    generate(singleSplat, "malt") should equal ("/single-splat/malt")
  }

  test("Many splat parameters get replaced") {
    generate(multipleSplats, "made", "in", "japan") should equal ("/mixing-multiple-splats/made/foo/in/japan")
  }

  test("Mix named and splat") {
    generate(mixNamedAndSplat, Map("foo" -> "deep"), Seq("purple")) should equal ("/mix-named-and-splat-params/deep/purple")
  }

  test("Unexpected splat parameters are just ignored at the moment") {
    generate(singleSplat, "black", "coffee") should equal ("/single-splat/black")
  }
}