package org.scalatra
package core

import ssgi.core._
import core.ScalatraKernel.MultiParams
import scala.util.matching.Regex
import util.MultiMap

trait Dsl {

  protected implicit def map2multimap(map: Map[String, Seq[String]]) = new MultiMap(map)

  protected def requestPath: String

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


  val routes = new RouteRegistry

  private val matchAllRoutes = new RouteMatcher { def apply() = Some(new MultiParams) }

  private def ensureMatcher(routeMatchers: Iterable[RouteMatcher]) =
    if (routeMatchers.isEmpty) List(matchAllRoutes).toIterable else routeMatchers

  def before(fun: => Any) { before(matchAllRoutes)(fun) }
  def before(routeMatchers: RouteMatcher*)(fun: => Any) {
    routes += ensureMatcher(routeMatchers) -> BeforeFilter(() => fun)
  }

  def after(fun: => Any) { after(matchAllRoutes)(fun) }
  def after(routeMatchers: RouteMatcher*)(fun: => Any) {
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

  def pass(): Any
  def notFound(fun: => Any): Any
  def error(fun: => Any): Any
  def halt(code: Int, msg: String): Any
  def halt(code: Int): Any
  def halt(): Any

}