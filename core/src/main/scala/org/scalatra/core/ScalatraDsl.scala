package org.scalatra
package core

import ssgi.core._
import scala.util.DynamicVariable
import javax.servlet.http.{HttpSession, HttpServletRequest, HttpServletResponse}
import javax.servlet.ServletContext
import core.ScalatraKernel._
import java.io.{File, FileInputStream}
import xml.NodeSeq
import annotation.tailrec
//import util.{MapWithIndifferentAccess, MultiMapHeadView, MultiMap}
import util._
import io.zeroCopy
import collection.JavaConversions._

trait ScalatraRequestHandler extends core.Handler { self: ScalatraDsl =>
  private def runAction(actionRoutes: List[MatchedRoute[ScalatraAction]], realMultiParams: Map[String, scala.Seq[String]]): (MultiParams, Any) = {
    var actionParams = new MultiParams()
    val ares = (actionRoutes flatMap {
      r =>
        _multiParams.withValue(realMultiParams ++ r.routeParams) {
          val acts = r.actions.map(_.asInstanceOf[Action])
          acts.filter(_.method == effectiveMethod).foldLeft(None.asInstanceOf[Option[Any]]) {
            (acc, rr) =>
              if (acc.isEmpty) {
                actionParams = r.routeParams // keeping these around so subsequent actions also have access to the goodies
                val res = rr(multiParams)
                res
              } else acc
          }
        }
    } headOption) orElse {
      val alt = actionRoutes.flatMap(_.actions.map(_.asInstanceOf[Action]))
          .filterNot(_.method == effectiveMethod)
          .map(_.method).toList
      if (!alt.isEmpty) {
        methodNotAllowed(alt)
      }
      None
    } getOrElse doNotFound()
    (actionParams, ares)
  }

  def handle(request: HttpServletRequest, response: HttpServletResponse) {
    // As default, the servlet tries to decode params with ISO_8859-1.
    // It causes an EOFException if params are actually encoded with the other code (such as UTF-8)
    if (request.getCharacterEncoding == null)
      request.setCharacterEncoding(defaultCharacterEncoding)

    val realMultiParams = request.getParameterMap.asInstanceOf[java.util.Map[String, Array[String]]].toMap
        .transform {
      (k, v) => v: Seq[String]
    }

    response.setCharacterEncoding(defaultCharacterEncoding)

    _request.withValue(request) {
      _response.withValue(response) {
        _multiParams.withValue(Map() ++ realMultiParams) {
          var actionParams: MultiParams = MultiMap()
          val result = try {
            val actionRoutes = routes(Actions, requestPath)
            val notFound = actionRoutes.isEmpty
            // TODO: Should before filters always run or only when an action matches?
            runFilters(BeforeActions, multiParams)
            if (notFound) {
              doNotFound()
            } else {
              val (ap, res) = runAction(actionRoutes, realMultiParams)
              actionParams = ap
              res
            }
          }
          catch {
            case e => {
              _multiParams.withValue(multiParams ++ actionParams) {
                handleError(e)
              }
            }
          }
          finally {
            // TODO: should after filters always run or only when there was a match?
            // TODO: should after fitlers run when an error occurred?
            runFilters(AfterActions, multiParams ++ actionParams)
          }
          renderResponse(result)
        }
      }
    }
  }



  private def runFilters(lifeCycle: Filtering, pars: => MultiParams) {
    routes(lifeCycle, requestPath).reverse foreach {
      r =>
        _multiParams.withValue(pars ++ r.routeParams) {
          r.actions foreach {
            _(multiParams)
          }
        }
    }
  }

  protected def effectiveMethod: HttpMethod =
    HttpMethod(request.getMethod) match {
      case Head => Get
      case x => x
    }

  def contentType = response.getContentType
  def contentType_=(value: String) {
    response.setContentType(value)
  }

  protected val defaultCharacterEncoding = "UTF-8"
  protected val _response   = new DynamicVariable[HttpServletResponse](null)
  protected val _request    = new DynamicVariable[HttpServletRequest](null)

  protected implicit def requestWrapper(r: HttpServletRequest) = new ssgi.servlet.RichRequest(r)
  protected implicit def sessionWrapper(s: HttpSession) = new ssgi.servlet.RichSession(s)
  protected implicit def servletContextWrapper(sc: ServletContext) = new ssgi.servlet.RichServletContext(sc)

  def requestPath: String

  def methodNotAllowed(alternatives: List[HttpMethod]) =
    halt(405, alternatives.mkString("Only the methods: [", ",", "] are allowed"))

  protected var doNotFound: () => Any
  def notFound(fun: => Any) = doNotFound = { () => fun }

  protected def handleError(e: Throwable): Any = {
    (renderError orElse defaultRenderError).apply(e)
  }

  protected def renderError : PartialFunction[Throwable, Any] = defaultRenderError

  protected final def defaultRenderError : PartialFunction[Throwable, Any] = {
    case HaltException(Some(code), Some(msg)) => response.sendError(code, msg)
    case HaltException(Some(code), None) => response.sendError(code)
    case HaltException(None, _) =>
    case e => {
      status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      _caughtThrowable.withValue(e) { errorHandler() }
    }
  }

  protected var errorHandler: () => Any = { () => throw caughtThrowable }
  def error(fun: => Any) = errorHandler = { () => fun }

  private val _caughtThrowable = new DynamicVariable[Throwable](null)
  protected def caughtThrowable = _caughtThrowable.value

  protected def renderResponse(actionResult: Any) {
    if (contentType == null)
      contentType = inferContentType(actionResult)
    renderResponseBody(actionResult)
  }

  type ContentTypeInferrer = PartialFunction[Any, String]

  protected def defaultContentTypeInfer: ContentTypeInferrer = {
    case _: NodeSeq => "text/html"
    case _: Array[Byte] => "application/octet-stream"
    case _ => "text/plain"
  }
  protected def contentTypeInfer: ContentTypeInferrer = defaultContentTypeInfer

  protected def inferContentType(actionResult: Any): String =
    (contentTypeInfer orElse defaultContentTypeInfer).apply(actionResult)

  protected def renderResponseBody(actionResult: Any) {
    @tailrec def loop(ar: Any): Any = ar match {
      case r: Unit => r
      case a => loop((renderPipeline orElse defaultRenderResponse) apply a)
    }
    loop(actionResult)
  }

  protected def renderPipeline: PartialFunction[Any, Any] = defaultRenderResponse

  protected final def defaultRenderResponse: PartialFunction[Any, Any] = {
    case bytes: Array[Byte] =>
      response.getOutputStream.write(bytes)
    case file: File =>
      using(new FileInputStream(file)) { in => zeroCopy(in, response.getOutputStream) }
    case _: Unit =>
    // If an action returns Unit, it assumes responsibility for the response
    case x: Any  =>
      response.getWriter.print(x.toString)
  }

  protected[scalatra] val _multiParams = new DynamicVariable[MultiMap](new MultiMap)
  protected def multiParams: MultiParams = (MultiMap(_multiParams.value)).withDefaultValue(Seq.empty)
  /*
   * Assumes that there is never a null or empty value in multiParams.  The servlet container won't put them
   * in request.getParameters, and we shouldn't either.
   */
  protected val _params = new MultiMapHeadView[String, String] with MapWithIndifferentAccess[String] {
    protected def multiMap = multiParams
  }
  def params = _params

  def redirect(uri: String) {
    (_response value) sendRedirect uri
  }
  implicit def request = _request value
  implicit def response = _response value
  def session = request.getSession
  def sessionOption = request.getSession(false) match {
    case s: HttpSession => Some(s)
    case null => None
  }
  def status(code: Int) {
    (_response value) setStatus code
  }

  def halt(code: Int, msg: String) = throw new HaltException(Some(code), Some(msg))
  def halt(code: Int) = throw new HaltException(Some(code), None)
  def halt() = throw new HaltException(None, None)
  protected[scalatra] case class HaltException(code: Option[Int], msg: Option[String]) extends RuntimeException

  def pass() = throw new PassException
  protected[scalatra] class PassException extends RuntimeException

}

trait ScalatraDsl extends ScalatraRouteImplicits {

  def routes: RouteRegistry

  private val matchAllRoutes = new RouteMatcher { def apply(path: String) = Some(MultiMap()) }


  private def ensureMatcher(routeMatchers: Iterable[RouteMatcher]) =
    if (routeMatchers.isEmpty) List(matchAllRoutes).toIterable else routeMatchers

  def beforeAll(fun: => Any) { beforeSome(matchAllRoutes)(fun) }
  @deprecated("Use beforeAll")
  def before(fun: => Any) = beforeAll(fun)
  def beforeSome(routeMatchers: RouteMatcher*)(fun: => Any) {
    routes += ensureMatcher(routeMatchers) -> BeforeFilter(() => fun)
  }

  def afterAll(fun: => Any) { afterSome(matchAllRoutes)(fun) }
  @deprecated("Use afterAll")
  def after(fun: => Any) = beforeAll(fun)
  def afterSome(routeMatchers: RouteMatcher*)(fun: => Any) {
    routes += ensureMatcher(routeMatchers) -> AfterFilter(() => fun)
  }

  /**
   * The Scalatra DSL core methods take a list of [[org.scalatra.RouteMatcher]] and a block as
   * the action body.
   * The return value of the block is converted to a string and sent to the client as the response body.
   *
   * See [[org.scalatra.ScalatraKernel.renderResponseBody]] for the detailed behaviour and how to handle your
   * response body more explicitly, and see how different return types are handled.
   *
   * The block is executed in the context of the ScalatraKernel instance, so all the methods defined in
   * this trait are also available inside the block.
   *
   * {{{
   *   get("/") {
   *     <form action="/echo">
   *       <label>Enter your name</label>
   *       <input type="text" name="name"/>
   *     </form>
   *   }
   *
   *   post("/echo") {
   *     "hello {params('name)}!"
   *   }
   * }}}
   *
   * ScalatraKernel provides implicit transformation from boolean blocks, strings and regular expressions
   * to [[org.scalatra.RouteMatcher]], so you can write code naturally
   * {{{
   *   get("/", request.getRemoteHost == "127.0.0.1") { "Hello localhost!" }
   * }}}
   *
   */
  def get(routeMatchers: RouteMatcher*)(action: => Any) = addRoute(Get, routeMatchers, action)

  /**
   * @see [[org.scalatra.ScalatraKernel.get]]
   */
  def post(routeMatchers: RouteMatcher*)(action: => Any) = addRoute(Post, routeMatchers, action)

  /**
   * @see [[org.scalatra.ScalatraKernel.get]]
   */
  def put(routeMatchers: RouteMatcher*)(action: => Any) = addRoute(Put, routeMatchers, action)

  /**
   * @see [[org.scalatra.ScalatraKernel.get]]
   */
  def delete(routeMatchers: RouteMatcher*)(action: => Any) = addRoute(Delete, routeMatchers, action)

  /**
   * @see [[org.scalatra.ScalatraKernel.get]]
   */
  def options(routeMatchers: RouteMatcher*)(action: => Any) = addRoute(Options, routeMatchers, action)

  /**
   * registers a new route for the given HTTP method, can be overriden so that subtraits can use their own logic
   * for example, restricting protocol usage, namespace routes based on class name, raise errors on overlapping entries
   * etc.
   *
   * This is the method invoked by get(), post() etc.
   *
   * @see removeRoute
   */
  protected def addRoute(method: HttpMethod, routeMatchers: Iterable[RouteMatcher], action: => Any): ScalatraRoute = {
    routes += routeMatchers -> Action(method, () => action)
  }

  @deprecated("Use addRoute(HttpMethod, Iterable[RouteMatcher], =>Any)")
  protected[scalatra] def addRoute(verb: String, routeMatchers: Iterable[RouteMatcher], action: => Any): ScalatraRoute =
    addRoute(HttpMethod(verb), routeMatchers, action)

  /**
   * removes _all_ the actions of a given route for a given HTTP method.
   * If [[addRoute]] is overriden this should probably be overriden too.
   *
   * @see addRoute
   */
  protected def removeRoute(method: HttpMethod, route: ScalatraRoute) {
    route -= method
  }

 @deprecated("Use removeRoute(HttpMethod, ScalatraRoute)")
  protected def removeRoute(verb: String, route: ScalatraRoute) {
    removeRoute(HttpMethod(verb), route)
  }

}