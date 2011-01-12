package org.scalatra
package socketio

import com.codahale.jerkson.{Json, ParsingException}
import Frame.FRAME
import util.RicherString._

object SocketIO {


  object SocketIOData {
    def apply(data: String) = {
      if(data.isNonBlank) {
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

    def parse(data: String) = {
      data.substring(0, 3) match {
        case "~h~" => HeartbeatData
        case "~j~" => JsonData(data.substring(3))
        case _ => StringData(data)
      }

    }
  }
  sealed trait SocketIOData {
    protected def frame(v: String): String = FRAME + v
    protected def frame(v: Int): String = frame(v.toString)
  }
  case object HeartbeatData extends SocketIOData {
    override def toString = "~h~"
  }
  case class JsonData(json: String) extends SocketIOData {
    override def toString = frame(json.length + 3) +  frame("~j~" + json)
  }
  case class StringData(data: String) extends SocketIOData {
    override def toString = frame(data.length) + frame(data)
  }

}