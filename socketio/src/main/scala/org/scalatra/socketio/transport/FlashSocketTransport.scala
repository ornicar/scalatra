package org.scalatra
package socketio
package transport

import java.net.InetSocketAddress
import java.nio.channels.{ClosedChannelException, ServerSocketChannel}
import java.util.concurrent.{ExecutionException, Future, Executors}
import java.util.Locale
import java.io.{PrintWriter, IOException}

object FlashSocketTransport {
  val FLASHPOLICY_SERVER_HOST_KEY= "flashPolicyServerHost"
  val FLASHPOLICY_SERVER_PORT_KEY= "flashPolicyServerPort"
  val FLASHPOLICY_DOMAIN_KEY= "flashPolicyDomain"
  val FLASHPOLICY_PORTS_KEY= "flashPolicyPorts"
//  private val FLASHFILE_NAME= "WebSocketMain.swf"
//  private val FLASHFILE_PATH= TRANSPORT_NAME + "/" + FLASHFILE_NAME

  case class FlashPolicyServerConfig(host: String = "localhost", port: Int = 843, domain: String = "localhost", policyPorts: List[Int] = Nil)

  class FlashPolicyServer(config: FlashPolicyServerConfig) {

    import config._

    private var policyServer: ServerSocketChannel = null
    private val executor = Executors.newCachedThreadPool
    private val policyServerRequest = "<policy-file-request/>"
    private var policyAcceptor: Future[_] = null

    def start {
      policyServer = ServerSocketChannel.open
      policyServer.socket.setReuseAddress(true)
      policyServer.socket.bind(new InetSocketAddress(host, port))
      policyServer.configureBlocking(true)

      policyAcceptor = executor.submit(new Runnable {
        def run = {
          try {
            while(true) {
              val serverSocket = policyServer.accept
              executor.submit(new Runnable {
                def run = {
                  try {
                    serverSocket.configureBlocking(true)
                    val s = serverSocket.socket
                    val request = new StringBuilder
                    val in = s.getInputStream
                    var c = in.read
                    while(c != 0 && request.length <= policyServerRequest.length) {
                      request.append(c.toChar)
                      c = in.read
                    }
                    if(policyServerRequest == request.toString.toLowerCase(Locale.ENGLISH) ||
                         (domain.isNonBlank && domain != "localhost" && domain != "127.0.0.1" && domain != "::1" &&
                           !policyPorts.isEmpty)) {
                      val pw = new PrintWriter(s.getOutputStream)
                      pw.println(policyResponse)
                      pw.write(0)
                      pw.flush
                    }
                    serverSocket.close
                  } catch {
                    case e: IOException =>
                  } finally {
                    try { serverSocket.close } catch { case _ => } // just close already
                  }
                }
              })
            }
          } catch {
            case e: ClosedChannelException =>
            case e: IOException => throw new IllegalStateException("Server should not throw an unhandled IO exception")
          }
        }
      })
    }

    private def policyResponse() = {
      "<cross-domain-policy><allow-access-from domain=\""+ domain +"\" to-ports=\""+ policyPorts.mkString(",") +"\" /></cross-domain-policy>"
    }

    def stop {
      if(policyServer != null) {
        try { policyServer.close } catch { case _ => } // STFU, don't get your knickers in a twist just stop
      }
      if(policyAcceptor != null) {
        try {
          policyAcceptor.get
        } catch {
          case e: InterruptedException => throw new IllegalStateException
          case e: ExecutionException => throw new IllegalStateException("Server threw an exception", e.getCause)
        }
        if(!policyAcceptor.isDone) throw new IllegalStateException("Server acceptor didn't stop")
      }
    }
  }
  object FlashPolicyServer {
    def apply(config: FlashPolicyServerConfig) = new FlashPolicyServer(config)
  }
}
class FlashSocketTransport(bufferSize: Int, maxIdleTime: Int) extends WebSocketTransport(bufferSize, maxIdleTime) {

  import FlashSocketTransport._

  private var flashPolicyServer: Option[FlashPolicyServer] = None

  override def destroy() = {
    flashPolicyServer foreach { _.stop }
  }

  override def init(config: Initializable#Config) = {
    val host = Option(config.getInitParameter(FLASHPOLICY_SERVER_HOST_KEY)) getOrElse "0.0.0.0"
    val port = Option(config.getInitParameter(FLASHPOLICY_SERVER_PORT_KEY)).map(_.toInt) getOrElse 843
    val domain = config.getInitParameter(FLASHPOLICY_DOMAIN_KEY)
    val ports = (Option(config.getInitParameter(FLASHPOLICY_PORTS_KEY)).map(_.split(',').map(_.toInt)) getOrElse Nil).asInstanceOf[List[Int]]
    flashPolicyServer = Some(FlashPolicyServer(FlashPolicyServerConfig(host, port, domain, ports)))
    flashPolicyServer foreach { _.start }
  }
}