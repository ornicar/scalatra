package org.scalatra
package core

import scala.util.DynamicVariable
import scala.util.matching.Regex
import scala.collection.JavaConversions._
import scala.collection.mutable.{ConcurrentMap, ListBuffer}
import ssgi.core._
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import util.{MultiMap, MapWithIndifferentAccess, MultiMapHeadView}

object ScalatraKernel
{
  type MultiParams = MultiMap

  type Action = () => Any

  @deprecated("Use HttpMethods.methods")
  val httpMethods = HttpMethod.methods map { _.toString }

  @deprecated("Use HttpMethods.methods filter { !_.isSafe }")
  val writeMethods = HttpMethod.methods filter { !_.isSafe } map { _.toString }

  @deprecated("Use CsrfTokenSupport.DefaultKey")
  val csrfKey = CsrfTokenSupport.DefaultKey

  val EnvironmentKey = "org.scalatra.environment"
}
import ScalatraKernel._

/**
 * ScalatraKernel provides the DSL for building Scalatra applications.
 *
 * At it's core a type mixing in ScalatraKernel is a registry of possible actions,
 * every request is dispatched to the first route matching.
 *
 * The [[org.scalatra.ScalatraKernel#get]], [[org.scalatra.ScalatraKernel#post]],
 * [[org.scalatra.ScalatraKernel#put]] and [[org.scalatra.ScalatraKernel#delete]]
 * methods register a new action to a route for a given HTTP method, possibly
 * overwriting a previous one. This trait is thread safe.
 */
trait ScalatraKernel extends Handler with Initializable
{
  protected implicit def map2multimap(map: Map[String, Seq[String]]) = new MultiMap(map)

  protected val routes: ConcurrentMap[HttpMethod, List[Route]] = {
    val map = new ConcurrentHashMap[HttpMethod, List[Route]]
    HttpMethod.methods foreach { x: HttpMethod => map += ((x, List[Route]())) }
    map
  }

  def contentType: String
  def contentType_=(value: String): Unit

  protected val defaultCharacterEncoding = "UTF-8"

  protected[scalatra] class Route(val routeMatchers: Iterable[RouteMatcher], val action: Action) {
    def apply(realPath: String): Option[Any] = RouteMatcher.matchRoute(routeMatchers) flatMap { invokeAction(_) }

    private def invokeAction(routeParams: MultiParams) =
      _multiParams.withValue(multiParams ++ routeParams) {
        try {
          Some(action.apply())
        }
        catch {
          case e: PassException => None
        }
      }

    override def toString = routeMatchers.toString()
  }

  /**
   * Pluggable way to convert Strings into RouteMatchers.  By default, we
   * interpret them the same way Sinatra does.
   */
  protected implicit def string2RouteMatcher(path: String): RouteMatcher =
    SinatraPathPatternParser(path)

  /**
   * Path pattern is decoupled from requests.  This adapts the PathPattern to
   * a RouteMatcher by supplying the request path.
   */
  protected implicit def pathPatternParser2RouteMatcher(pattern: PathPattern): RouteMatcher =
    new RouteMatcher {
      def apply() = pattern(requestPath)

      // By overriding toString, we can list the available routes in the
      // default notFound handler.
      override def toString = pattern.regex.toString
    }

  protected implicit def regex2RouteMatcher(regex: Regex): RouteMatcher = new RouteMatcher {
    def apply() = regex.findFirstMatchIn(requestPath) map { _.subgroups match {
      case Nil => Map.empty
      case xs => Map("captures" -> xs)
    }}

    override def toString = regex.toString
  }

  protected implicit def booleanBlock2RouteMatcher(matcher: => Boolean): RouteMatcher =
    () => { if (matcher) Some(MultiMap()) else None }

  def requestPath: String

  protected val beforeFilters = new ListBuffer[() => Any]
  def before(fun: => Any) = beforeFilters += { () => fun }

  protected val afterFilters = new ListBuffer[() => Any]
  def after(fun: => Any) = afterFilters += { () => fun }

  protected var doNotFound: Action
  def notFound(fun: => Any) = doNotFound = { () => fun }

  protected def handleError(e: Throwable): Any = {
    (renderError orElse defaultRenderError).apply(e)
  }

  protected def renderError : PartialFunction[Throwable, Any] = defaultRenderError

  protected def defaultRenderError: PartialFunction[Throwable, Any]

  protected var errorHandler: Action = { () => throw caughtThrowable }
  def error(fun: => Any) = errorHandler = { () => fun }

  protected def caughtThrowable: Throwable

  protected def renderResponse(actionResult: Any) {
    if (contentType == null)
      contentType = inferContentType(actionResult)
    renderResponseBody(actionResult)
  }

  type ContentTypeInferrer = PartialFunction[Any, String]

  protected def defaultContentTypeInfer: ContentTypeInferrer = {
    case _: String => "text/plain"
    case _: Array[Byte] => "application/octet-stream"
    case _ => "text/html"
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

  protected def defaultRenderResponse: PartialFunction[Any, Any]
  protected[scalatra] val _multiParams = new DynamicVariable[MultiMap](new MultiMap)
  protected def multiParams: MultiParams = (_multiParams.value).withDefaultValue(Seq.empty)
  /*
   * Assumes that there is never a null or empty value in multiParams.  The servlet container won't put them
   * in request.getParameters, and we shouldn't either.
   */
  protected val _params = new MultiMapHeadView[String, String] with MapWithIndifferentAccess[String] {
    protected def multiMap = multiParams
  }
  def params = _params

  def redirect(uri: String): Unit

  def status(code: Int): Unit

  def halt(code: Int, msg: String) = throw new HaltException(Some(code), Some(msg))
  def halt(code: Int) = throw new HaltException(Some(code), None)
  def halt() = throw new HaltException(None, None)
  protected case class HaltException(val code: Option[Int], val msg: Option[String]) extends RuntimeException

  def pass() = throw new PassException
  protected[scalatra] class PassException extends RuntimeException

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
  protected def addRoute(method: HttpMethod, routeMatchers: Iterable[RouteMatcher], action: => Any): Route = {
    val route = new Route(routeMatchers, () => action)
    modifyRoutes(method, route :: _ )
    route
  }

  @deprecated("Use addRoute(HttpMethod, Iterable[RouteMatcher], =>Any)")
  protected[scalatra] def addRoute(verb: String, routeMatchers: Iterable[RouteMatcher], action: => Any): Route =
    addRoute(HttpMethod(verb), routeMatchers, action)

  /**
   * removes _all_ the actions of a given route for a given HTTP method.
   * If [[addRoute]] is overriden this should probably be overriden too.
   *
   * @see addRoute
   */
  protected def removeRoute(method: HttpMethod, route: Route): Unit = {
    modifyRoutes(method, _ filterNot (_ == route) )
    route
  }

  protected def removeRoute(verb: String, route: Route): Unit =
    removeRoute(HttpMethod(verb), route)

  /**
   * since routes is a ConcurrentMap and we avoid locking, we need to retry if there are
   * concurrent modifications, this is abstracted here for removeRoute and addRoute
   */
  @tailrec private def modifyRoutes(method: HttpMethod, f: (List[Route] => List[Route])): Unit = {
    val oldRoutes = routes(method)
    if (!routes.replace(method, oldRoutes, f(oldRoutes))) {
      modifyRoutes(method,f)
    }
  }

  protected var config: Config = _
  def initialize(config: Config) = this.config = config

  def initParameter(name: String): Option[String]

  def environment: String = System.getProperty(EnvironmentKey, initParameter(EnvironmentKey).getOrElse("development"))
  def isDevelopmentMode = environment.toLowerCase.startsWith("dev")

  /**
   * Uniquely identifies this ScalatraKernel inside the webapp.
   */
  def kernelName: String
}
