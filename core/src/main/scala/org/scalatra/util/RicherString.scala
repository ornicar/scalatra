package org.scalatra.util

import java.nio.charset.Charset
import java.net.{URLEncoder, URLDecoder}

class RicherString(orig: String) {
  def isBlank = orig == null || orig.trim.isEmpty
  def isNonBlank = !isBlank
  def urlDecode(encoding: String): String = URLDecoder.decode(orig, encoding)
  def urlDecode: String = urlDecode("UTF-8")

  def urlEncode(encoding: String): String = URLEncoder.encode(orig, encoding)
  def urlEncode: String = urlEncode("UTF-8")
}

object RicherString {
  implicit def stringToRicherString(s: String) = new RicherString(s)
}
