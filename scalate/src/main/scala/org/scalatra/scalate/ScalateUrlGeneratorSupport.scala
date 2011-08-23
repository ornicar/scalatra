package org.scalatra
package scalate

import java.lang.reflect.Field
import java.lang.Class
import org.fusesource.scalate.servlet.{ServletRenderContext, ServletTemplateEngine}
import org.fusesource.scalate.{TemplateEngine, Binding}

trait ScalateUrlGeneratorSupport extends ScalateSupport {

  abstract override def initialize(config: Config) {
    GlobalRouteRegistry register this
    super.initialize(config)
  }

  lazy val reflectRoutes: Map[String, Route] =
    this.getClass.getDeclaredMethods
      .filter(_.getParameterTypes.isEmpty)
      .filter(f => classOf[Route].isAssignableFrom(f.getReturnType))
      .map(f => (f.getName, f.invoke(this).asInstanceOf[Route]))
      .toMap

  override protected def configureTemplateEngine(engine: TemplateEngine) = {
    val generatorBinding = Binding("urlGenerator", classOf[UrlGeneratorSupport].getName, true)
    val routeBindings = GlobalRouteRegistry.routes.keys map (Binding(_, classOf[Route].getName))
    engine.bindings = generatorBinding :: engine.bindings ::: routeBindings.toList
    engine
  }

  override protected def configureRenderContext(context: ServletRenderContext) = {
    for ((name, route) <- GlobalRouteRegistry.routes)
      context.attributes.update(name, route)
    context.attributes.update("urlGenerator", UrlGenerator)
    context
  }
}

object GlobalRouteRegistry {

  var kernels = List[ScalateUrlGeneratorSupport]()

  def register(kernel: ScalateUrlGeneratorSupport) {
    kernels = kernel :: kernels
  }

  def routes: Map[String, Route] =
    if (kernels.isEmpty) Map()
    else (kernels.head.reflectRoutes /: kernels)((acc, ker) => acc ++ ker.reflectRoutes)
}
