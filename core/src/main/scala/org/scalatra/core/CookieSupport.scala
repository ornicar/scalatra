package org.scalatra
package core

import javax.servlet.http.{HttpServletRequest, HttpServletResponse, Cookie => ServletCookie}
import scala.util.DynamicVariable
import ssgi.servlet.SweetCookies
import ssgi.core.{Cookie, CookieOptions}

trait CookieSupport extends Handler {
  self: ScalatraKernel =>

  implicit def cookieOptions: CookieOptions = _cookieOptions.value

  def cookies = _cookies.value

  abstract override def handle(req: HttpServletRequest, res: HttpServletResponse) {
    _cookies.withValue(new SweetCookies(req.cookies, res)) {
      _cookieOptions.withValue(CookieOptions(path = req.getContextPath)) {
        super.handle(req, res)
      }
    }
  }

  private val _cookies = new DynamicVariable[SweetCookies](null)
  private val _cookieOptions = new DynamicVariable[CookieOptions](CookieOptions())
}
