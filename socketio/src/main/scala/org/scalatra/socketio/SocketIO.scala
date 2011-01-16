package org.scalatra
package socketio

import com.codahale.jerkson.{Json, ParsingException}
import transport.{FlashSocketTransport, WebSocketTransport}
import collection.mutable.Map
import socketio.SocketIO.Transport
import java.util.Locale
import socketio.SocketIOClient.ClientConfig
import java.util.concurrent._
import collection.JavaConversions._

object SocketIO {

  private[socketio] val FRAME = "~m~"
  private[socketio] val HEARTBEAT_FRAME = "~h~"
  private[socketio] val JSON_FRAME = "~j~"
  private[socketio] val FRAME_SEPARATOR = "~"

  var encoding = "UTF-8"
  val BUFFER_SIZE_INIT_PARAM = "bufferSize"
  val MAX_IDLE_TIME_INIT_PARAM: String = "maxIdleTime"
  val BUFFER_SIZE_DEFAULT: Int = 8192
  val MAX_IDLE_TIME_DEFAULT: Int = 300 * 1000
  val VERSION = "0.6.1"
  private val serialVersionUID: Long = 1L

  sealed trait DisconnectReason

  case object ConnectFailed extends DisconnectReason

  case object Disconnect extends DisconnectReason

  case object Timeout extends DisconnectReason

  case object CloseFailed extends DisconnectReason

  case object Error extends DisconnectReason

  case object ClosedRemotely extends DisconnectReason

  case object Closed extends DisconnectReason

  case object Unknown extends DisconnectReason

  def decode(data: String): List[String] = {
    val parts = data.split(FRAME).drop(1)
    val frames = if ((parts.length % 2) == 0) parts else parts.dropRight(1)
    frames.grouped(2).foldRight(List[String]()) {
      (frame, messages) =>
        val lenCand = frame(0)
        val len = if (lenCand.forall(_.isDigit)) lenCand.toInt else 0
        if (frame.length == 2 && len <= frame(1).length) frame(1).substring(0, len) :: messages
        else messages
    }
  }

  def encode(messages: String*): List[String] = {
    messages map {
      SocketIOData(_).toString
    } toList
  }

  def encodeData(messages: SocketIOData*): List[String] = {
    messages map {
      _.toString
    } toList
  }

  object SocketIOData {
    def apply(data: String) = {
      if (data.isNonBlank) {
        try {
          Json.parse[Map[String, Any]](data)
          JsonData(data)
        } catch {
          case e: ParsingException => {
            StringData(data)
          }
        }
      } else StringData("")
    }

    def unapply(data: String) = {
      try {
        Some(parse(data))
      } catch {
        case _ => None
      }
    }

    def parse(data: String) = {
      data.substring(0, 3) match {
        case HEARTBEAT_FRAME => HeartbeatData(data.substring(3).toInt)
        case JSON_FRAME => JsonData(data.substring(3))
        case _ => StringData(data)
      }

    }
  }

  sealed trait SocketIOData {
    protected def frame(v: String): String = FRAME + v

    protected def frame(v: Int): String = frame(v.toString)
  }

  case class HeartbeatData(data: Int) extends SocketIOData {
    override def toString = {
      val s = data.toString
      frame(s.length + HEARTBEAT_FRAME.length) + frame(HEARTBEAT_FRAME + s)
    }
  }

  case class JsonData(data: String) extends SocketIOData {
    override def toString = frame(data.length + JSON_FRAME.length) + frame(JSON_FRAME + data)
  }

  case class StringData(data: String) extends SocketIOData {
    override def toString = frame(data.length) + frame(data)
  }

  //  type ClientFactory = ConnectionConfig => ScalatraSocketIOConnection
  //  type SessionFactory = ClientConfig => SocketIOClient

  object DisconnectReason {
    def apply(v: Int) = v match {
      case 1 => ConnectFailed
      case 2 => Disconnect
      case 3 => Timeout
      case 4 => CloseFailed
      case 5 => Error
      case 6 => ClosedRemotely
      case 7 => Closed
      case _ => Unknown
    }
  }

  type MessageHandler = (SocketIOClient, SocketIOData) => Unit
  type ConnectingHandler = (SocketIOClient) => Unit
  type DisconnectedHandler = (SocketIOClient, DisconnectReason) => Unit

  trait Transport {
    /**
     * @return The name of the transport instance.
     */
    val name: Symbol = {
      val nm = getClass.getSimpleName
      Symbol(nm.substring(0, nm.length - 9).toLowerCase(Locale.ENGLISH))
    }

    def init(config: Initializable#Config) {}

    def destroy() {}

    def handle(config: ClientConfig): Option[SocketIOSession]

  }


}

class SocketIO(bufferSize: Int, maxIdleTime: Int) {
  import SocketIO._
  private val _transports: ConcurrentHashMap[Symbol, Transport] = new ConcurrentHashMap[Symbol, Transport]

  private val _sessions = new ConcurrentHashMap[String, SocketIOSession]

  _transports.getOrElseUpdate('websocket, new WebSocketTransport(bufferSize, maxIdleTime))
  _transports.getOrElseUpdate('flashsocket, new FlashSocketTransport(bufferSize, maxIdleTime))

  def init(cfg: Initializable#Config) {
    _transports foreach {
      case (_, transport) => transport.init(cfg)
    }
  }

  def destroy() {
    _transports foreach {
      case (_, transport) => transport.destroy
    }
    TaskScheduler.stop
  }

  def apply(sessionId: String) = {
    _sessions.get(sessionId)
  }

  def handle(transportName: String, config: ClientConfig) {
    println("handling transport with: %s" format config)
    Option(_transports.get(Symbol(transportName.toLowerCase(Locale.ENGLISH))).asInstanceOf[Transport]) flatMap {
      transport =>
        val sessionOpt = transport handle config.copy(
          transport = config.transport.copy(
            getSession = Some(sessId => if (_sessions.containsKey(sessId.toLowerCase(Locale.ENGLISH))) {
              Option(_sessions.get(sessId.toLowerCase(Locale.ENGLISH)))
            } else None),
            addSession = Some(sess => _sessions.put(sess.id.toLowerCase(Locale.ENGLISH), sess))),
          session = config.session.copy(
            broadcast = Some((id, message) => _sessions.filterKeys(_ != id).values.foreach(_.send(message))),
            sessionId = Some(config.session.sessionId getOrElse GenerateId())))
        sessionOpt foreach { sess =>
          _sessions.put(sess.id, sess)
          sess.send(StringData(sess.id).toString)
        }
        sessionOpt
    }
  }

  def isValidTransport(transportName: String) = _transports.exists {
    case (name, _) => name.name == transportName.toLowerCase(Locale.ENGLISH)
    case _ => false
  }
}

private[socketio] object TaskScheduler {

  private object TaskSchedulerThreadFactory extends ThreadFactory {
    private var count = 0
    val threadFactory = Executors.defaultThreadFactory()

    def newThread(r: Runnable): Thread = {
      val thread = threadFactory.newThread(r)
      thread.setName("scalatra:task-scheduler-" + count)
      count += 1
      thread.setDaemon(true)
      thread
    }
  }

  case class TaskSchedulerException(msg: String, e: Throwable) extends RuntimeException(msg, e)

  @volatile private var _executor = Executors.newSingleThreadScheduledExecutor(TaskSchedulerThreadFactory)

  def schedule(startDelay: Int, interval: Int, timeUnit: TimeUnit)(task: => Unit): ScheduledFuture[AnyRef] = {
    try {
      _executor.scheduleAtFixedRate(new Runnable {
        def run = {
          task
        }
      }, startDelay, interval, timeUnit).asInstanceOf[ScheduledFuture[AnyRef]]
    } catch {
      case e: Exception => throw new TaskSchedulerException("Failed to schedule a task", e)
    }
  }

  def scheduleOnce(startDelay: Int, timeUnit: TimeUnit)(task: => Unit): ScheduledFuture[AnyRef] = {
    try {
      _executor.schedule(new Runnable {
        def run = {
          task
        }
      }, startDelay, timeUnit).asInstanceOf[ScheduledFuture[AnyRef]]
    } catch {
      case e: Exception => throw TaskSchedulerException("Failed to schedule a task", e)
    }
  }

  def restart = synchronized {
    stop
    _executor = Executors.newSingleThreadScheduledExecutor(TaskSchedulerThreadFactory)
  }

  def stop = synchronized {
    _executor.shutdown
  }
}