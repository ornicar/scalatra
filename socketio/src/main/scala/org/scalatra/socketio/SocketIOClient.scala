package org.scalatra
package socketio

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.util.Locale
import SocketIO._
import socketio.SocketIOClient.{ConnectionConfig, ClientConfig}
import java.util.concurrent.{ScheduledFuture, TimeUnit}

object SocketIOClient {

  case class ConnectionConfig( messageHandler: MessageHandler,
                               connectingHandler: ConnectingHandler,
                               disconnectedHandler: DisconnectedHandler)

  case class SessionConfig(sessionId: Option[String], broadcast: Option[(String, String) => Unit] = None)
  case class TransportConfig(
               request: HttpServletRequest,
               response: HttpServletResponse,
               getSession: Option[String => Option[SocketIOSession]] = None,
               addSession: Option[SocketIOSession => Unit] = None)

  case class ClientConfig(
               transport: TransportConfig,
               session: SessionConfig,
               connection: ConnectionConfig)



  trait SocketIOConnection {

    private var _messageHandler: MessageHandler = _
    private var _connectingHandler: ConnectingHandler = _
    private var _disconnectedHandler: DisconnectedHandler = _

    def onMessage(handler: MessageHandler) {
      _messageHandler = handler
    }

    def connecting(handler: ConnectingHandler) {
      _connectingHandler = handler
    }

    def disconnect(handler: DisconnectedHandler) {
      _disconnectedHandler = handler
    }

    def result = {
      ConnectionConfig(_messageHandler, _connectingHandler, _disconnectedHandler)
    }
  }

}
trait SocketIOClient {

  val id: String
  protected val config: ConnectionConfig

  protected[socketio] def onMessage(data: SocketIOData) { config.messageHandler(this, data) }
  protected[socketio] def onDisconnect(reason: DisconnectReason) { config.disconnectedHandler(this, reason)}
  protected[socketio] def onConnect() { config.connectingHandler(this) }

  def send(message: String): Unit
  def broadcast(message: String): Unit

}
trait SocketIOOutbound {
  def send(message: String): Unit
  def isOpen: Boolean
  def disconnect: Unit
}

trait SocketIOSession {

  protected val config: ClientConfig
  protected val heartbeatTimeout: Int
  protected val heartbeatInterval: Int
  private var _heartbeat: ScheduledFuture[AnyRef] = null
  private var _heartbeatTimeout: ScheduledFuture[AnyRef] = null
  protected var _out: SocketIOOutbound = null
  private var heartbeats = 0
  private var _open = false

  def isOpen = outOpt.map(_.isOpen) getOrElse false

  protected def outOpt = Option(_out)

  lazy val id = config.session.sessionId getOrElse GenerateId()

  def send(message: String) { outOpt foreach { _.send(message) } }
  def broadcast(message: String) {
    config.session.broadcast foreach { _(id, message) }
  }

  def messageReceived(message: String): Unit = {
    val decoded = decode(message)
    if (decoded.isEmpty) println("Bad message received from client [%s]." format id)
    decoded map { SocketIOData.parse _ } foreach {
      case m: HeartbeatData => onHeartbeat(m)
      case m: SocketIOData => client.onMessage(m)
      case m => println("Bad message [%s] received from client [%s].".format(m, id))
    }
  }

  def onHeartbeat(message: HeartbeatData) {
    if(message.data == heartbeats) {
      stopHeartbeatTimeout
      startHeartbeat
    }
  }

  def onConnect(outbound: SocketIOOutbound) {
    _out = outbound
    client.onConnect
  }


  def onDisconnect(reason: DisconnectReason) {
    stopHeartbeatTimeout
    stopHeartbeat
    client.onDisconnect(reason)
    outOpt foreach { _.disconnect }
    _out = null
  }

  def startHeartbeat() {
    stopHeartbeat
    TaskScheduler.scheduleOnce(heartbeatInterval, TimeUnit.MILLISECONDS) {
      stopHeartbeatTimeout
      _heartbeatTimeout = TaskScheduler.scheduleOnce(heartbeatTimeout, TimeUnit.MILLISECONDS) {
        onDisconnect(Timeout)
      }
      heartbeat
    }
  }

  def stopHeartbeatTimeout() {
    if(_heartbeatTimeout != null && !_heartbeatTimeout.isCancelled && !_heartbeatTimeout.isDone){
      _heartbeatTimeout.cancel(true)
      _heartbeatTimeout = null
    }
  }

  def stopHeartbeat() {
    if(_heartbeat != null && !_heartbeat.isCancelled && !_heartbeat.isDone){
      _heartbeat.cancel(true)
      _heartbeat = null
    }
  }

  def heartbeat {
    heartbeats += 1
    send(HeartbeatData(heartbeats).toString)
  }

  protected lazy val client = {
    val cfg = config
    val sender = send _
    val broadcaster = broadcast _
    val sessionId = id
    new SocketIOClient {
      val id = sessionId
      protected val config = cfg.connection

      def broadcast(message: String) { broadcaster(message) }

      def send(message: String) { encode(message) foreach { sender } }
    }
  }

}
