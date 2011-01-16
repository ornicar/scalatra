package org.scalatra
package socketio

import SocketIOClient.{ SocketIOConnection, ClientConfig, SessionConfig, TransportConfig }
import SocketIO.{BUFFER_SIZE_DEFAULT, BUFFER_SIZE_INIT_PARAM, MAX_IDLE_TIME_DEFAULT, MAX_IDLE_TIME_INIT_PARAM, Transport}
import scala.io.Source
import org.eclipse.jetty.util.IO

trait SocketIOSupport extends Initializable { self: ScalatraKernel =>

  private var _socketIO: SocketIO = _
  private var _builder: SocketIOConnection = null

  before {
    println(request.getRequestURI)
  }

  abstract override def initialize(config: Config) = {
    val bufferSize = (Option(config.getInitParameter(BUFFER_SIZE_INIT_PARAM)) getOrElse BUFFER_SIZE_DEFAULT.toString).toInt
    val maxIdleTime = (Option(config.getInitParameter(MAX_IDLE_TIME_INIT_PARAM)) getOrElse MAX_IDLE_TIME_DEFAULT.toString).toInt
    _socketIO = new SocketIO(bufferSize, maxIdleTime)
    _socketIO.init(config)
  }

//  get("/?") {
//    halt(400, "Missing SocketIO transport")
//  }

  get("/:transport") {
    val transport = params.get('transport) getOrElse "unknown"
    println("selected transport: " + transport)
    if (! _socketIO.isValidTransport(transport)) halt(400, "Unknown SocketIO transport")
    else {
      println("handling transport [%s]" format transport)
      _socketIO.handle(transport,
        ClientConfig(TransportConfig(request, response), SessionConfig(None), _builder.result))
    }
  }

  get("/:transport/:sessionId?") {
    val transport = params.get('transport) getOrElse "unknown"
    println("selected transport: " + transport)
    if (! _socketIO.isValidTransport(transport)) halt(400, "Unknown SocketIO transport")
    else {
      println("handling transport [%s]" format transport)
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
    val p = request.getServletPath.substring(1)
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("socket.io.js")).getLines foreach { line =>
      response.getWriter.println(
        line.replace("'socket.io'", "'%s'" format p).replace("socket.io/WebSocketMain", "%s/WebSocketMain" format p))
    }
  }

  get("/WebSocketMain.swf") {
    contentType = "application/x-shockwave-flash"
    val is = getClass.getClassLoader.getResourceAsStream("WebSocketMain.swf")
    val os = response.getOutputStream
    IO.copy(is, os)
  }
}

trait SocketIOServlet extends ScalatraServlet with SocketIOSupport
