package org.scalatra
package ssgi.servlet

import collection._
import javax.servlet.http.{HttpServletResponse, Cookie => ServletCookie}
import util.RicherString._

class SweetCookies(private val reqCookies: Map[String, String], private val response: HttpServletResponse) {
  private lazy val cookies = mutable.HashMap[String, String]() ++ reqCookies

  def get(key: String) = cookies.get(key)

  def apply(key: String) = cookies.get(key) getOrElse (throw new Exception("No cookie could be found for the specified key"))

  def update(name: String, value: String)(implicit cookieOptions: ssgi.core.CookieOptions=ssgi.core.CookieOptions()) = {
    val sCookie = new ServletCookie(name, value)
    if(cookieOptions.domain.isNotBlank) sCookie.setDomain(cookieOptions.domain)
    if(cookieOptions.path.isNotBlank) sCookie.setPath(cookieOptions.path)
    sCookie.setMaxAge(cookieOptions.maxAge)
    if(cookieOptions.secure) sCookie.setSecure(cookieOptions.secure)
    if(cookieOptions.comment.isNotBlank) sCookie.setComment(cookieOptions.comment)
    cookies += name -> value
    //response.addHeader("Set-Cookie", cookie.toCookieString)
    response.addCookie(sCookie)
    sCookie
  }

  def set(name: String, value: String)(implicit cookieOptions: ssgi.core.CookieOptions=ssgi.core.CookieOptions()) = {
    this.update(name, value)(cookieOptions)
  }

  def delete(name: String) {
    cookies -= name
    response.addHeader("Set-Cookie", ssgi.core.Cookie(name, "")(ssgi.core.CookieOptions(maxAge = 0)).toCookieString)
  }

  def +=(keyValuePair: (String, String))(implicit cookieOptions: ssgi.core.CookieOptions = ssgi.core.CookieOptions()) = {
    this.update(keyValuePair._1, keyValuePair._2)(cookieOptions)
  }


  def -=(key: String) {
    delete(key)
  }
}


