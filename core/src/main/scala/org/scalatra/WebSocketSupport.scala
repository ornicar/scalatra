package org.scalatra

import org.eclipse.jetty.websocket.{WebSocket => ServletWebSocket, WebSocketFactory}
import java.io.UnsupportedEncodingException
import org.eclipse.jetty.websocket.WebSocket.Outbound
import collection.mutable.{ HashSet, SynchronizedSet }
import javax.servlet.http.HttpServletResponse

object WebSocket {

  var encoding = "UTF-8"
}

trait WebSocket {

  private type MessageHandler = (ScalatraWebSocket, String) => Unit
  private type ConnectingHandler = (ScalatraWebSocket) => Boolean
  private type DisconnectedHandler = (ScalatraWebSocket) => Unit

  private val _messageHandlers = new HashSet[MessageHandler] with SynchronizedSet[MessageHandler]
  private val _connectingHandlers = new HashSet[ConnectingHandler] with SynchronizedSet[ConnectingHandler]
  private val _disconnectedHandlers = new HashSet[DisconnectedHandler] with SynchronizedSet[DisconnectedHandler]

  def onMessage(handler: MessageHandler) {
    _messageHandlers += handler
  }

  def connecting(handler: ConnectingHandler) {
    _connectingHandlers += handler
  }

  def disconnect(handler: DisconnectedHandler) {
    _disconnectedHandlers += handler
  }

  def result = {
    if(_messageHandlers.isEmpty) throw new RuntimeException("You need to define at least 1 message handler")
    (new ScalatraWebSocket {

      def onDisconnect() {
        _disconnectedHandlers foreach { _(this) }
      }

      override def onConnect(outbound: Outbound) = {
        super.onConnect(outbound)
        _connectingHandlers foreach { h =>
          val r = h(this)
          if(!r) throw new RuntimeException("There was a problem connecting the websocket")
        }
      }

      def onMessage(opcode: Byte, data: String) {
        _messageHandlers foreach { _(this, data) }
      }
    }).asInstanceOf[ServletWebSocket]
  }
}
trait ScalatraWebSocket extends ServletWebSocket {
  private var _outbound: Outbound = null
  def outOption = {
    Option(_outbound)
  }

  def out = outOption getOrElse (throw new RuntimeException("Not connected"))

  def onDisconnect(): Unit

  def onMessage(opcode: Byte, data: Array[Byte], offset: Int, length: Int) = {
    try {
      onMessage(opcode, new String(data, offset, length, WebSocket.encoding))
    } catch {
      case e: UnsupportedEncodingException =>
    }
  }

  def onFragment(more: Boolean, opcode: Byte, data: Array[Byte], offset: Int, length: Int) = {

  }

  def onMessage(opcode: Byte, data: String)

  def onConnect(p1: Outbound) = {
    _outbound = p1
  }

  def sendMessage(data: String) {
    outOption foreach { _.sendMessage(data) }
  }
}

trait WebSocketSupport { self: ScalatraKernel =>

  private val wsFactory = new WebSocketFactory
  type WebSocketAction = WebSocket => Unit

  def ws(routeMatchers: RouteMatcher*)(action: WebSocket => Unit) = {
    addRoute("WS", routeMatchers, {
      try {
//        WebSocket.encoding = request.getCharacterEncoding
        doUpgrade { () =>
          val websocket = new WebSocket { }
          action(websocket)
          websocket.result
        }
        Unit
      } catch {
        case e => {
//          println(e.printStackTrace)
          webSocketError(e.getMessage)
          Unit
        }
      }
    })
  }

  private def isHixie = Option(request.getHeader("Sec-WebSocket-Key1")).isDefined
  private def webSocketError(msg: String = null) = {
    if(isHixie) response.setHeader("Connection", "close")
    if(msg == null) response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
    else response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, msg)
  }

  private def doUpgrade(matcher: () => ServletWebSocket) = {
    val ph = request.getHeader(if(isHixie) "Sec-WebSocket-Protocol" else "WebSocket-Protocol")
    val protocol = Option(ph) getOrElse request.getHeader("Sec-WebSocket-Protocol")
    val host = request.getHeader("Host")
    val origin = Option(request.getHeader("Origin")) getOrElse host

    val websocket = matcher()
    if(websocket == null) {
      webSocketError()
    } else {
      wsFactory.upgrade(request, response, websocket, origin, protocol)
    }
    websocket
  }
}
