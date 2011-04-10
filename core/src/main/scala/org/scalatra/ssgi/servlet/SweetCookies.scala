package org.scalatra.ssgi
package servlet

import collection._
import javax.servlet.http.{HttpServletResponse, Cookie => ServletCookie}
import org.scalatra.util.RicherString._
import core.{Cookie, CookieOptions}

class SweetCookies(private val reqCookies: Map[String, String], private val response: HttpServletResponse) {
  private lazy val cookies = mutable.HashMap[String, String]() ++ reqCookies

  def get(key: String) = cookies.get(key)

  def apply(key: String) = cookies.get(key) getOrElse (throw new Exception("No cookie could be found for the specified key"))

  def update(name: String, value: String)(implicit cookieOptions: CookieOptions=CookieOptions()) = {
    val sCookie = new ServletCookie(name, value)
    if(cookieOptions.domain.isNonBlank) sCookie.setDomain(cookieOptions.domain)
    if(cookieOptions.path.isNonBlank) sCookie.setPath(cookieOptions.path)
    sCookie.setMaxAge(cookieOptions.maxAge)
    if(cookieOptions.secure) sCookie.setSecure(cookieOptions.secure)
    if(cookieOptions.comment.isNonBlank) sCookie.setComment(cookieOptions.comment)
    cookies += name -> value
    //response.addHeader("Set-Cookie", cookie.toCookieString)
    response.addCookie(sCookie)
    sCookie
  }

  def set(name: String, value: String)(implicit cookieOptions: CookieOptions=CookieOptions()) = {
    this.update(name, value)(cookieOptions)
  }

  def delete(name: String) {
    cookies -= name
    response.addHeader("Set-Cookie", Cookie(name, "")(CookieOptions(maxAge = 0)).toCookieString)
  }

  def +=(keyValuePair: (String, String))(implicit cookieOptions: CookieOptions = CookieOptions()) = {
    this.update(keyValuePair._1, keyValuePair._2)(cookieOptions)
  }


  def -=(key: String) {
    delete(key)
  }
}


