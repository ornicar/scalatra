package org

import javax.servlet.http.HttpSession
import scalatra.util.RicherString

package object scalatra {
  /**
   * Structural type for the various Servlet API objects that have attributes.  These include ServletContext,
   * HttpSession, and ServletRequest.
   */
  type Attributes = {
    def getAttribute(name: String): AnyRef
    def getAttributeNames(): java.util.Enumeration[_]
    def setAttribute(name: String, value: AnyRef): Unit
    def removeAttribute(name: String): Unit
  }

  implicit def string2RicherString(s: String) = new RicherString(s)
}