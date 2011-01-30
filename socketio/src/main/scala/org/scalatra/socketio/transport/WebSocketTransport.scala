package org.scalatra
package socketio
package transport

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import socketio.SocketIOClient.ClientConfig
import org.eclipse.jetty.websocket.{WebSocket, WebSocketFactory}
import java.lang.String
import org.eclipse.jetty.websocket.WebSocket.Outbound
import SocketIO._
import java.io.UnsupportedEncodingException
import java.util.Locale

object WebSocketTransport {

  trait ScalatraWebSocket extends WebSocket {
    self: SocketIOSession =>

    private var initiated = false

    //    private var _outbound: Outbound = null
    //    def outboundOption = Option(_outbound)
    //    def outbound = outboundOption getOrElse (throw new RuntimeException("Not connected"))

    def onMessage(opcode: Byte, data: Array[Byte], offset: Int, length: Int) {
      try {
        onMessage(opcode, new String(data, offset, length, SocketIO.encoding))
      } catch {
        case e: UnsupportedEncodingException =>
      }
    }

    def onFragment(more: Boolean, opcode: Byte, data: Array[Byte], offset: Int, length: Int) = {

    }

    def onMessage(opcode: Byte, data: String) {
      startHeartbeat
      messageReceived(data)
    }

    def onConnect(outbound: Outbound) = {
      self.onConnect(new SocketIOOutbound {
        def send(message: String) = outbound.sendMessage(message)

        def isOpen() = outbound.isOpen

        def disconnect() = if (isOpen) outbound.disconnect
      })
    }

    def onDisconnect = {
      onDisconnect(Disconnect)
    }
  }

  private implicit def request2WSRequest(request: HttpServletRequest): WebSocketRequest = new WebSocketRequest(request)

  private class WebSocketRequest(request: HttpServletRequest) {

    def isHixie() = Option(request.getHeader("Sec-WebSocket-Key1")).isDefined

    def isWebSocketUpgrade() = {
      request.getMethod == "GET" && request.getHeader("Upgrade").toUpperCase(Locale.ENGLISH) == "WEBSOCKET"
    }
  }

}

class WebSocketTransport(bufferSize: Int, maxIdleTime: Int) extends Transport {

  import WebSocketTransport._

  private var request: HttpServletRequest = null
  private var response: HttpServletResponse = null

  private val wsFactory = new WebSocketFactory
  wsFactory.setMaxIdleTime(maxIdleTime)
  wsFactory.setBufferSize(bufferSize)

  def handle(config: ClientConfig) = {
    request = config.transport.request
    response = config.transport.response
    val connection = if (config.transport.request.isWebSocketUpgrade && config.session.sessionId.isDefined) {
      println("upgrading transport request")
      Option(doUpgrade(config))
    } else {
      println("Invalid websocket transport request")
      webSocketError("Invalid %s transport request" format name.name)
      None
    }
    println("Created connection with: %s" format connection)
    connection
  }


  private def webSocketError(msg: String = null) = {
    if (request.isHixie) response.setHeader("Connection", "close")
    if (msg == null) response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
    else response.sendError(HttpServletResponse.SC_BAD_REQUEST, msg)
  }

  private def doUpgrade(config: ClientConfig) = {
    val ph = request.getHeader(if (request.isHixie) "Sec-WebSocket-Protocol" else "WebSocket-Protocol")
    val protocol = Option(ph) getOrElse request.getHeader("Sec-WebSocket-Protocol")
    val host = request.getHeader("Host")
    val origin = Option(request.getHeader("Origin")) getOrElse host
    val cfg = config
    val websocket = new SocketIOSession with ScalatraWebSocket {
      protected val heartbeatInterval = 10000
      protected val heartbeatTimeout = 8000
      protected val config = cfg

    }
    if (websocket == null) {
      println("websocket couldn't be created")
      webSocketError()
    } else {
      println("wsfactory doing upgrade of request")
      wsFactory.upgrade(request, response, websocket, origin, protocol)
    }
    websocket
  }
}