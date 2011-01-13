package org.scalatra
package socketio

import SocketIOClient.{ SocketIOConnection, ClientConfig, SessionConfig, TransportConfig }
import SocketIO.{BUFFER_SIZE_DEFAULT, BUFFER_SIZE_INIT_PARAM, MAX_IDLE_TIME_DEFAULT, MAX_IDLE_TIME_INIT_PARAM, Transport}
import scala.io.Source
import org.eclipse.jetty.util.IO

trait SocketIOSupport extends Initializable { self: ScalatraKernel =>

  private var _socketIO: SocketIO = _
  private var _builder: SocketIOConnection = null

  abstract override def initialize(config: Config) = {
    val bufferSize = Option(config.getInitParameter(BUFFER_SIZE_INIT_PARAM).toInt) getOrElse BUFFER_SIZE_DEFAULT
    val maxIdleTime = Option(config.getInitParameter(MAX_IDLE_TIME_INIT_PARAM).toInt) getOrElse MAX_IDLE_TIME_DEFAULT
    _socketIO = new SocketIO(bufferSize, maxIdleTime)
    _socketIO.init(config)
  }

  get("/?") {
    halt(400, "Missing SocketIO transport")
  }

  get("/:transport(/:sessionId)?") {
    val transport = params.get('transport) getOrElse "unknown"
    if (! _socketIO.isValidTransport(transport)) halt(400, "Unknown SocketIO transport")
    else {
      _socketIO.handle(transport,
        ClientConfig(TransportConfig(request, response), SessionConfig(params.get('sessionId)), _builder.result))
    }
  }

  def socketio(action: SocketIOConnection => Unit) {
    if(_builder != null) throw new RuntimeException("You can only use 1 socketio method per application")
    _builder = new SocketIOConnection { }
    action(_builder)
  }

  get("/socket.io.js") {
    contentType = "text/javascript"
    val is = getClass.getClassLoader.getResourceAsStream("org/scalatra/socketio/socket.io.js")
    Source.fromInputStream(is).getLines foreach { line =>
      response.getWriter.println(line.replace("'socket.io'", "'%'" format request.getServletPath.substring(1)))
    }
    Unit
  }

  get("/WebSocketMain.swf") {
    contentType = "application/x-shockwave-flash"
    val is = getClass.getClassLoader.getResourceAsStream("org/scalatra/socketio/WebSocketMain.swf")
    val os = response.getOutputStream
    IO.copy(is, os)
  }
}

trait SocketIOServlet extends ScalatraServlet with SocketIOSupport
