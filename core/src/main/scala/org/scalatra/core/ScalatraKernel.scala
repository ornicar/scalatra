package org.scalatra
package core

import javax.servlet._
import ssgi.core._
import util.MultiMap

object ScalatraKernel {
  type MultiParams = MultiMap

  @deprecated("Use HttpMethods.methods")
  val httpMethods = HttpMethod.methods map {
    _.toString
  }

  @deprecated("Use HttpMethods.methods filter { _.isUnsafe }")
  val writeMethods = HttpMethod.methods filter {
    _.isUnsafe
  } map {
    _.toString
  }

  @deprecated("Use CsrfTokenSupport.DefaultKey")
  val csrfKey = CsrfTokenSupport.DefaultKey

  val EnvironmentKey = "org.scalatra.environment"
}

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
trait ScalatraKernel extends core.Initializable with core.ScalatraDsl with core.ScalatraRequestHandler {

  val routes = new RouteRegistry

  /**
   * Uniquely identifies this ScalatraKernel inside the webapp.
   */
  def kernelName: String

  import ScalatraKernel._
  def environment: String = System.getProperty(EnvironmentKey, initParameter(EnvironmentKey).getOrElse("development"))
  def isDevelopmentMode = environment.toLowerCase.startsWith("dev")

  private var config: Config = _

  def initialize(config: Config) {
    this.config = config
  }

  def initParameter(name: String): Option[String] = config match {
    case config: ServletConfig => Option(config.getInitParameter(name))
    case config: FilterConfig => Option(config.getInitParameter(name))
    case _ => None
  }
}
