package org.scalatra
package socketio

import java.io.UnsupportedEncodingException
import org.eclipse.jetty.websocket.WebSocket.Outbound
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.websocket.{WebSocket, WebSocketFactory}
import java.lang.String
//import com.glines.socketio.common.DisconnectReason
import collection.mutable.{HashMap, HashSet, SynchronizedSet}
import org.eclipse.jetty.util.IO

object SocketIOClient {

  var encoding = "UTF-8"
  val BUFFER_SIZE_INIT_PARAM = "bufferSize"
  val MAX_IDLE_TIME_INIT_PARAM: String = "maxIdleTime"
  val BUFFER_SIZE_DEFAULT: Int = 8192
  val MAX_IDLE_TIME_DEFAULT: Int = 300 * 1000
  private val serialVersionUID: Long = 1L
}

//trait SocketIOClient {
//
//  private type MessageHandler = (ScalatraWebSocket, String) => Unit
//  private type ConnectingHandler = (ScalatraWebSocket) => Boolean
//  private type DisconnectedHandler = (ScalatraWebSocket) => Unit
//
//  private val _messageHandlers = new HashSet[MessageHandler] with SynchronizedSet[MessageHandler]
//  private val _connectingHandlers = new HashSet[ConnectingHandler] with SynchronizedSet[ConnectingHandler]
//  private val _disconnectedHandlers = new HashSet[DisconnectedHandler] with SynchronizedSet[DisconnectedHandler]
//
//  def onMessage(handler: MessageHandler) {
//    _messageHandlers += handler
//  }
//
//  def connecting(handler: ConnectingHandler) {
//    _connectingHandlers += handler
//  }
//
//  def disconnect(handler: DisconnectedHandler) {
//    _disconnectedHandlers += handler
//  }
//
//  def result = {
//    if(_messageHandlers.isEmpty) throw new RuntimeException("You need to define at least 1 message handler")
//    (new ScalatraWebSocket {
//
//      def onDisconnect() {
//        _disconnectedHandlers foreach { _(this) }
//      }
//
//      override def onConnect(outbound: Outbound) = {
//        super.onConnect(outbound)
//        _connectingHandlers foreach { h =>
//          val r = h(this)
//          if(!r) throw new RuntimeException("There was a problem connecting the websocket")
//        }
//      }
//
//      def onMessage(opcode: Byte, data: String) {
//        _messageHandlers foreach { _(this, data) }
//      }
//    }).asInstanceOf[WebSocket]
//  }
//}
////trait ScalatraSocketIOClient extends SocketIOInbound {
////  def onMessage(`type`: Int, data: String): Unit
////
////  def onDisconnect(reason: DisconnectReason, message: String): Unit
////
////  def onConnect(outbound: SocketIOOutbound): Unit
////
////  def getProtocol: String
////}
//trait ScalatraWebSocket extends WebSocket {
//  private var _outbound: Outbound = null
//  def outOption = {
//    Option(_outbound)
//  }
//
//  def out = outOption getOrElse (throw new RuntimeException("Not connected"))
//
//  def onDisconnect(): Unit
//
//  def onMessage(opcode: Byte, data: Array[Byte], offset: Int, length: Int) = {
//    try {
//      onMessage(opcode, new String(data, offset, length, SocketIOClient.encoding))
//    } catch {
//      case e: UnsupportedEncodingException =>
//    }
//  }
//
//  def onFragment(more: Boolean, opcode: Byte, data: Array[Byte], offset: Int, length: Int) = {
//
//  }
//
//  def onMessage(opcode: Byte, data: String)
//
//  def onConnect(p1: Outbound) = {
//    _outbound = p1
//  }
//
//  def sendMessage(data: String) {
//    outOption foreach { _.sendMessage(data) }
//  }
//}
//
//trait SocketIOSupport extends Initializable { self: ScalatraKernel =>
//  import SocketIOClient._
////  private var sessionManager: SocketIOSessionManager = null
////  private var transports: Map[String, Transport] = new HashMap[String, Transport]
//
//
//  abstract override def initialize(config: Config) = {
//    val bufferSize = Option(config.getInitParameter(BUFFER_SIZE_INIT_PARAM)) getOrElse BUFFER_SIZE_DEFAULT
//    val maxIdleTime = Option(config.getInitParameter(MAX_IDLE_TIME_INIT_PARAM)) getOrElse MAX_IDLE_TIME_DEFAULT
//  }
//
//  private val wsFactory = new WebSocketFactory
//  type WebSocketAction = SocketIOClient => Unit
//
//  def socketio(routeMatchers: RouteMatcher*)(action: SocketIOClient => Unit) = {
//    addRoute("SocketIO", routeMatchers, {
//      try {
////        SocketIOClient.encoding = request.getCharacterEncoding
//        doUpgrade { () =>
//          val websocket = new SocketIOClient { }
//          action(websocket)
//          websocket.result
//        }
//        Unit
//      } catch {
//        case e => {
////          println(e.printStackTrace)
//          webSocketError(e.getMessage)
//          Unit
//        }
//      }
//    })
//  }
//
//  get("/socket.io.js") {
//    contentType = "text/javascript"
//    val is = this.getClass().getClassLoader().getResourceAsStream("com/glines/socketio/socket.io.js");
//    val os = response.getOutputStream
//    IO.copy(is, os);
//  }
//
//  private def isHixie = Option(request.getHeader("Sec-WebSocket-Key1")).isDefined
//  private def webSocketError(msg: String = null) = {
//    if(isHixie) response.setHeader("Connection", "close")
//    if(msg == null) response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
//    else response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, msg)
//  }
//
//  private def doUpgrade(matcher: () => SocketIOClient) = {
//    val ph = request.getHeader(if(isHixie) "Sec-WebSocket-Protocol" else "WebSocket-Protocol")
//    val protocol = Option(ph) getOrElse request.getHeader("Sec-WebSocket-Protocol")
//    val host = request.getHeader("Host")
//    val origin = Option(request.getHeader("Origin")) getOrElse host
//
//    val websocket = matcher()
//    if(websocket == null) {
//      webSocketError()
//    } else {
////      wsFactory.upgrade(request, response, websocket, origin, protocol)
//    }
//    websocket
//  }
//}
