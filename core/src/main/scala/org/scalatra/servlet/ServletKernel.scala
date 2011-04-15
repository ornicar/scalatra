package org.scalatra
package servlet

import ssgi.servlet.{RichRequest, RichSession, RichServletContext}
import scala.util.DynamicVariable
import javax.servlet.{FilterConfig, ServletConfig, ServletContext}
import javax.servlet.http.{HttpSession, HttpServletRequest, HttpServletResponse}
import ssgi.core.{HttpMethod, Head, Get}
import core.ScalatraKernel
import scala.collection.JavaConversions._
import java.io.{FileInputStream, File}
import util.using
import util.io.zeroCopy

trait ServletKernel extends ScalatraKernel {

  def contentType = response.getContentType
  def contentType_=(value: String): Unit = response.setContentType(value)

  protected val _request = new DynamicVariable[HttpServletRequest](null)
  implicit def request: HttpServletRequest = _request.value

  protected val _response = new DynamicVariable[HttpServletResponse](null)
  implicit def response: HttpServletResponse = _response.value

  protected implicit def requestWrapper(r: HttpServletRequest) = RichRequest(r)
  protected implicit def sessionWrapper(s: HttpSession) = new RichSession(s)
  protected implicit def servletContextWrapper(sc: ServletContext) = new RichServletContext(sc)

  def handle(request: HttpServletRequest, response: HttpServletResponse) {
    // As default, the servlet tries to decode params with ISO_8859-1.
    // It causes an EOFException if params are actually encoded with the other code (such as UTF-8)
    if (request.getCharacterEncoding == null)
      request.setCharacterEncoding(defaultCharacterEncoding)

    val realMultiParams = request.getParameterMap.asInstanceOf[java.util.Map[String,Array[String]]].toMap
      .transform { (k, v) => v: Seq[String] }

    response.setCharacterEncoding(defaultCharacterEncoding)

    _request.withValue(request) {
      _response.withValue(response) {
        _multiParams.withValue(Map() ++ realMultiParams) {
          val result = try {
            beforeFilters foreach { _() }
            routes(effectiveMethod).toStream.flatMap { _(requestPath) }.headOption.getOrElse(doNotFound())
          }
          catch {
            case e => handleError(e)
          }
          finally {
            afterFilters foreach { _() }
          }
          renderResponse(result)
        }
      }
    }
  }

  protected def effectiveMethod: HttpMethod =
    HttpMethod(request.getMethod) match {
      case Head => Get
      case x => x
    }

  protected val defaultRenderError: PartialFunction[Throwable, Any] = {
    case HaltException(Some(code), Some(msg)) => response.sendError(code, msg)
    case HaltException(Some(code), None) => response.sendError(code)
    case HaltException(None, _) =>
    case e => {
      status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      _caughtThrowable.withValue(e) { errorHandler() }
    }
  }
  private val _caughtThrowable = new DynamicVariable[Throwable](null)
  protected def caughtThrowable: Throwable = _caughtThrowable.value

  protected def defaultRenderResponse: PartialFunction[Any, Any] = {
    case bytes: Array[Byte] =>
      response.getOutputStream.write(bytes)
    case file: File =>
      using(new FileInputStream(file)) { in => zeroCopy(in, response.getOutputStream) }
    case _: Unit =>
    // If an action returns Unit, it assumes responsibility for the response
    case x: Any  =>
      response.getWriter.print(x.toString)
  }

  def redirect(uri: String) = (_response value) sendRedirect uri

  def session = request.getSession
  def sessionOption = request.getSession(false) match {
    case s: HttpSession => Some(s)
    case null => None

  }

  def status(code: Int) = (_response value) setStatus code

  def initParameter(name: String): Option[String] = config match {
    case config: ServletConfig => Option(config.getInitParameter(name))
    case config: FilterConfig => Option(config.getInitParameter(name))
    case _ => None
  }

  protected def servletContext: ServletContext
}
