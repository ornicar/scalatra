package org.scalatra.akka

import _root_.akka.http._
import akka.actor.Actor._
import akka.servlet.AkkaLoader
import akka.config.Supervision._
import javax.servlet.{ServletContextEvent, ServletContextListener}
import akka.remote.BootableRemoteActorService
import org.scalatra.core._
import javax.servlet.http.HttpServletRequest
import akka.stm._
import akka.actor.{ActorRef, BootableActorLoaderService, Actor}
import akka.transactor.Transactor
import akka.dispatch.Dispatchers
import org.scalatra.ssgi.core.HttpMethod

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

    def requestPath = null

    def receive = {
      case m: RequestMethod => {
        handle(m.request, m.response)
      }
    }

  }
}
abstract class ScalatraMistApp(basePath: String) extends Actor with Endpoint with ScalatraDsl {
  import ScalatraMistApp._

  self.faultHandler = OneForOneStrategy(List(classOf[Throwable]), 5, 5000)

  // TODO: STM this thing? it should already be thread-safe so don't really see the point
  val routes = new RouteRegistry
  private var _handler: Option[ActorRef] = None
  protected val handlerConcurrency = 1

  protected def hook(path: String) = !routes(Actions, path).isEmpty
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
