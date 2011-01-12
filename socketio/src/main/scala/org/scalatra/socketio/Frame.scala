package org.scalatra
package socketio

import util.RicherString._
import SocketIO._

object Frame {

  private[socketio] val FRAME = "~m~"
  private[socketio] val HEARTBEAT_FRAME = "~h~"
  private[socketio] val JSON_FRAME = "~j~"
  private[socketio] val FRAME_SEPARATOR = "~"

  def decode(data: String): List[String] = {
    val parts = data.split(FRAME).drop(1)
    val frames = if((parts.length % 2) == 0) parts else parts.dropRight(1)
    frames.grouped(2).foldRight(List[String]()) { (frame, messages) =>
      val lenCand = frame(0)
      val len = if (lenCand.forall(_.isDigit)) lenCand.toInt else 0
      if (frame.length == 2 && len <= frame(1).length) frame(1).substring(0, len) :: messages
      else messages
    }
  }

  def encode(messages: String*): List[String] = {
    messages map { SocketIOData(_).toString } toList
  }
}
class Frame