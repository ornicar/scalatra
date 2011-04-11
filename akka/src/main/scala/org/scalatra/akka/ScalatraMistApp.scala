package org.scalatra
package akka

import _root_.akka.http._
import _root_.akka.actor.Actor._
import _root_.akka.servlet.AkkaLoader
import _root_.akka.config.Supervision._
import javax.servlet.{ServletContextEvent, ServletContextListener}
import _root_.akka.remote.BootableRemoteActorService
import org.scalatra.core._
import javax.servlet.http.HttpServletRequest
import _root_.akka.actor.{ActorRef, BootableActorLoaderService, Actor}
import _root_.akka.dispatch.Dispatchers
import ssgi.core.HttpMethod
import scala.util.DynamicVariable

class ScalatraMistInitializer extends ServletContextListener  {

  private val loader = new AkkaLoader 

  def contextDestroyed(p1: ServletContextEvent) {
    loader.shutdown
  }

  def contextInitialized(p1: ServletContextEvent) {
    loader.boot(false, new BootableActorLoaderService with BootableRemoteActorService)
  }
}

object ScalatraMistApp {

  private lazy val RouteHandlerDispatcher = Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("scalatra-mist").build

  private class ScalatraRouteHandler(basePath: String, val routes: RouteRegistry, loadBalance: Boolean) extends Actor with ScalatraDsl with ScalatraRequestHandler {

    self.id = "actor-for-base-" + basePath
    if(loadBalance) self.dispatcher = RouteHandlerDispatcher


    protected var doNotFound: () => Any = () => {
      response.setStatus(404)
      response.getWriter println "Requesting %s but only have %s".format(request.getRequestURI, routes)
    }

    def requestPath = if (request.getPathInfo != null) request.getPathInfo else request.getContextPath

    def receive = {
      case m: RequestMethod => {
        handle(m.request, m.response)
      }
    }

    def checkWithRequestPath(req: HttpServletRequest) = _request.withValue(req) {
      routes(Actions, requestPath).isEmpty
    }

  }
}

trait ScalatraMistRootApp extends Actor with Endpoint with ScalatraDsl {
  
}

abstract class ScalatraMistApp(basePath: String) extends Actor with Endpoint with ScalatraDsl {
  import ScalatraMistApp._

  self.faultHandler = OneForOneStrategy(List(classOf[Throwable]), 5, 5000)

  private val _request = new DynamicVariable[HttpServletRequest](null)
  protected def request = _request.value
  def requestPath = if (request.getPathInfo != null) request.getPathInfo else request.getContextPath
  def hasMatchingRoute(req: HttpServletRequest) = _request.withValue(req) {
    routes(Actions, requestPath).isEmpty
  }

  // TODO: STM this thing? it should already be thread-safe so don't really see the point
  val routes = new RouteRegistry
  private var _handler: Option[ActorRef] = None
  protected val handlerConcurrency = 1

  protected def hook(path: String) = hasMatchingRoute(path)
  protected def provide(path: String) = _handler getOrElse {
    val h = actorOf(new ScalatraRouteHandler(basePath, routes, handlerConcurrency > 1))
    if (handlerConcurrency > 1) {
      (1 to (handlerConcurrency - 1)) foreach { _ =>
        self startLink actorOf(new ScalatraRouteHandler(basePath, routes, handlerConcurrency > 1))
      }
    }
    self startLink h
    _handler = Some(h)
    h
  }

  override def preStart {
    registry.actorFor[RootEndpoint] foreach { _ ! Endpoint.Attach(hook, provide) }
  }

  protected def receive = handleHttpRequest

  override def postStop {
    self.shutdownLinkedActors()
  }
}
