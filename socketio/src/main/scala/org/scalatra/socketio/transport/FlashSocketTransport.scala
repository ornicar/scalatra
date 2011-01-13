package org.scalatra
package socketio
package transport

import socketio.SocketIO.Transport
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import javax.servlet.ServletConfig
import socketio.SocketIOClient.ClientConfig

class FlashSocketTransport(bufferSize: Int, maxIdleTime: Int) extends Transport {

  def handle(config: ClientConfig) = null
}