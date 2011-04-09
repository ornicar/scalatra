package org.scalatra.core

import ScalatraKernel.MultiParams
import org.scalatra.ssgi.core.HttpMethod
import java.util.concurrent.{ConcurrentSkipListSet}
import collection.JavaConverters._
import annotation.tailrec
import collection.immutable.HashSet

trait ScalatraAction {
  val action: () => Any

  def apply(params: MultiParams) = {
    try {
      Some(action())
    } catch {
      case e: ScalatraKernel#PassException => {
        None
      }
    }
  }
}
case class Action(method: HttpMethod, action: () => Any) extends ScalatraAction
case class BeforeFilter(action: () => Any) extends ScalatraAction
case class AfterFilter(action: () => Any) extends ScalatraAction

case class MatchedRoute[ActionType <: ScalatraAction](routeParams: MultiParams, actions: List[ActionType])

sealed trait LifeCycle
sealed trait Filtering extends LifeCycle
case object BeforeActions extends Filtering
case object Actions extends LifeCycle
case object AfterActions extends Filtering

object ScalatraRoute {
  def apply(routeMatchers: Iterable[RouteMatcher], action: ScalatraAction): ScalatraRoute = {
    val r = new ScalatraRoute(routeMatchers)
    r += action
  }
}
class ScalatraRoute(val routeMatchers: Iterable[RouteMatcher]) { // deliberately not a case class because this one is mutable

  private lazy val matchResult = RouteMatcher.matchRoute(routeMatchers)
  private var _actions = new HashSet[ScalatraAction]
  def actions = _actions

  def isDefined = matchResult.isDefined
  def isEmpty = matchResult.isEmpty

  def isDefinedAt(matchers: Iterable[RouteMatcher]) = matchers.toList == routeMatchers.toList
  
  def apply(lifeCycle: LifeCycle) = lifeCycle match {
    case BeforeActions => {
      matchResult map { MatchedRoute(_, actions filter { _.isInstanceOf[BeforeFilter] } toList) }
    }
    case Actions => {
      matchResult map { MatchedRoute(_, actions filter { _.isInstanceOf[Action] } toList) }
    }
    case AfterActions => {
      matchResult map { MatchedRoute(_, actions filter { _.isInstanceOf[AfterFilter] } toList) }
    }
  }

  def +=(action: ScalatraAction) = {
    _actions += action
    this
  }

  def -=(method: HttpMethod) = {
    _actions = actions filterNot {
      case m: Action => m.method == method
      case _ => false
    }
  }

  override def equals(other: Any) = other match {
    case r: ScalatraRoute => r.routeMatchers == routeMatchers && r.actions == actions
    case _ => false
  }

  override def hashCode() = 41 * ( 41 + routeMatchers.toList.hashCode ) + actions.hashCode

  override def toString =
    "ScalatraRoute(matchers=[%s], actionCount=[%s], actions=[%s])".format(
      routeMatchers.mkString(", "), actions.size, actions.mkString(", "))
}
class RouteRegistry {

  private[scalatra] var routes = new HashSet[ScalatraRoute]

  def +=(kv: (Iterable[RouteMatcher], ScalatraAction)) = {
    val (routeMatchers, action) = kv
    routes find { _.isDefinedAt(routeMatchers) } map { _ += action } getOrElse {
      val r = ScalatraRoute(routeMatchers, action)
      routes += r
      r
    }
  }

  def -=(route: ScalatraRoute) = routes -= route

  def apply(lifeCycleStage: LifeCycle) = {
    routes filter { _.isDefined } flatMap { _(lifeCycleStage) } toStream
  }

}