package org.scalatra
package socketio
package tests

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers


class FrameSpec extends WordSpec with MustMatchers {

  "For decoding a frame" should {

    "decode a valid message" in {
      val msg = Frame.decode("~m~5~m~abcde" + "~m~9~m~123456789")
      msg must have size(2)
      msg(0) must be("abcde")
      msg(1) must be("123456789")
    }

    "decode a badly framed message" in {
      val msg = Frame.decode("~m~5~m~abcde" + "~maffsdaasdfd9~m~1aaa23456789")
      msg must have size(1)
      msg(0) must be("abcde")
    }

  }

  "For encoding a message" should {

    "encode a message" in {
      Frame.encode("abcde", "123456789").toList must equal(List("~m~5~m~abcde", "~m~9~m~123456789"))
      Frame.encode("asdasdsad").toList must equal(List("~m~9~m~asdasdsad"))
      Frame.encode("").toList must equal(List("~m~0~m~"))
      Frame.encode(null).toList must equal(List("~m~0~m~"))
    }

  }
}