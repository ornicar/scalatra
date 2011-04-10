package org.scalatra
package akka

import _root_.akka.http.Mist
import javax.servlet.{FilterConfig, ServletConfig}

trait ScalatraMistKernel extends core.Initializable with Mist {

  abstract override def initialize(config: Config) {
    config match {
      case c: ServletConfig => initMist(c.getServletContext)
      case c: FilterConfig => initMist(c.getServletContext)
    }
    super.initialize(config)
  }

}