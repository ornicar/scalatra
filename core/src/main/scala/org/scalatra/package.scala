package org
package object scalatra {

  @deprecated("Moved to org.scalatra.ssgi.core")
  type Cookie = ssgi.core.Cookie
  @deprecated("Moved to org.scalatra.ssgi.core")
  val Cookie = ssgi.core.Cookie

  @deprecated("Moved to org.scalatra.ssgi.core")
  type CookieOptions = ssgi.core.CookieOptions
  @deprecated("Moved to org.scalatra.ssgi.core")
  val CookieOptions = ssgi.core.CookieOptions

  @deprecated("Moved to org.scalatra.ssgi.servlet")
  type Attributes = ssgi.servlet.Attributes

  @deprecated("Moved to org.scalatra.ssgi.servlet")
  type AttributesMap = ssgi.servlet.AttributesMap

  @deprecated("Moved to org.scalatra.ssgi.servlet")
  type RichRequest = ssgi.servlet.RichRequest
  @deprecated("Moved to org.scalatra.ssgi.core")
  val RichRequest = ssgi.servlet.RichRequest

  @deprecated("Moved to org.scalatra.ssgi.servlet")
  type RichSession = ssgi.servlet.RichSession

  @deprecated("Moved to org.scalatra.ssgi.servlet")
  type RichServletContext = ssgi.servlet.RichServletContext

  @deprecated("Moved to org.scalatra.servlet")
  type ScalatraFilter = servlet.ScalatraFilter

  @deprecated("Moved to org.scalatra.servlet")
  type ScalatraServlet = servlet.ScalatraServlet

  @deprecated("Moved to org.scalatra.util")
  val NotEmpty = util.NotEmpty

  @deprecated("Moved to org.scalatra.core")
  type CookieSupport = core.CookieSupport

  @deprecated("Moved to org.scalatra.core.CsrfTokenSupport")
  type CSRFTokenSupport = core.CsrfTokenSupport
  @deprecated("Moved to org.scalatra.core.CsrfTokenSupport")
  val CSRFTokenSupport = core.CsrfTokenSupport

  @deprecated("Moved to org.scalatra.core")
  val GenerateId = core.GenerateId

  @deprecated("Moved to org.scalatra.core")
  type FlashMap = core.FlashMap
  @deprecated("Moved to org.scalatra.core")
  val FlashMap = core.FlashMap

  @deprecated("Moved to org.scalatra.core")
  type FlashMapSupport = core.FlashMapSupport
  @deprecated("Moved to org.scalatra.core")
  val FlashMapSupport = core.FlashMapSupport

  @deprecated("Moved to org.scalatra.core")
  type Handler = core.Handler

  @deprecated("Moved to org.scalatra.core")
  type Initializable = core.Initializable

  @deprecated("Moved to org.scalatra.core")
  type MethodOverride = core.MethodOverride

  @deprecated("Moved to org.scalatra.core")
  val PathPattern = core.PathPattern

  @deprecated("Moved to org.scalatra.core")
  type PathPatternParser = core.PathPatternParser

  @deprecated("Moved to org.scalatra.core")
  val RailsPathPatternParser = core.RailsPathPatternParser

  @deprecated("Moved to org.scalatra.core")
  type RegexPathPatternParser = core.RegexPathPatternParser

  @deprecated("Moved to org.scalatra.core")
  val SinatraPathPatternParser = core.SinatraPathPatternParser

  @deprecated("Moved to org.scalatra.core")
  val ScalatraKernel = core.ScalatraKernel
  @deprecated("Moved to org.scalatra.core")
  type ScalatraKernel = core.ScalatraKernel

  @deprecated("Moved to org.scalatra.core")
  type UrlSupport = core.UrlSupport
}
