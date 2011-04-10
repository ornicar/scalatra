package org.scalatra
package akka

import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServletRequest, HttpServletResponse, HttpServlet}

class ScalatraMistServlet extends HttpServlet with core.ScalatraKernel with core.Initializable with ScalatraMistKernel {

  type Config = ServletConfig

  /**
   * Initializes Mist
   */
  override def initialize(config: Config) = super.initialize(config)

  override def init(config: ServletConfig) {
    super.init(config)
    _kernelName = "servlet:"+config.getServletName
    initialize(config) // see Initializable.initialize for why
  }

  private var _kernelName: String = _
  def kernelName = _kernelName

  // pathInfo is for path-mapped servlets (i.e., the mapping ends in "/*").  Path-mapped Scalatra servlets will work even
  // if the servlet is not mapped to the context root.  Routes should contain everything matched by the "/*".
  //
  // If the servlet mapping is not path-mapped, then we fall back to the servletPath.  Routes should have a leading
  // slash and include everything between the context route and the query string.
  def requestPath = if (request.getPathInfo != null) request.getPathInfo else request.getServletPath

  protected var doNotFound: () => Any = () => {
    // TODO - We should return a 405 if the route matches a different method
    response.setStatus(404)
    response.getWriter println "Requesting %s but only have %s".format(request.getRequestURI, routes)
  }

  protected override def  doDelete(req: HttpServletRequest, res: HttpServletResponse) {
    mistify(req, res)(_factory.get.Delete)
  }
  protected override def     doGet(req: HttpServletRequest, res: HttpServletResponse) {
    mistify(req, res)(_factory.get.Get)
  }
  protected override def    doHead(req: HttpServletRequest, res: HttpServletResponse) {
    mistify(req, res)(_factory.get.Head)
  }
  protected override def doOptions(req: HttpServletRequest, res: HttpServletResponse) {
    mistify(req, res)(_factory.get.Options)
  }
  protected override def    doPost(req: HttpServletRequest, res: HttpServletResponse) {
    mistify(req, res)(_factory.get.Post)
  }
  protected override def     doPut(req: HttpServletRequest, res: HttpServletResponse) {
    mistify(req, res)(_factory.get.Put)
  }
  protected override def   doTrace(req: HttpServletRequest, res: HttpServletResponse) {
    mistify(req, res)(_factory.get.Trace)
  }
}