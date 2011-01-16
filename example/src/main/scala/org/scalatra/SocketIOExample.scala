package org.scalatra

import socketio.{SocketIOServlet}

class SocketIOExample extends SocketIOServlet {

  // overriden because otherwise you need a trailing slash for the root url
  // prefer the freedom with or without root.
  override def requestPath = {
    val p = (Option(request.getPathInfo) getOrElse "").trim
    if(p.isEmpty) "/" else p
  }

  socketio { builder =>
    builder.connecting { connection =>
      println("Connecting client [%s]." format connection.id )
    }
    builder.onMessage { (connection, msg) =>
      connection.send("ECHO: %s" format msg)
    }
    builder.disconnect { (connection, reason) =>
      println("Client [%s] disconnected because of [%s]." format (connection.id, reason))
    }
  }

  get("/?") {
    <html>
      <head>
        <title>Socket.IO connection</title>
        <script type="text/javascript" src="/socket.io/socket.io.js"></script>
      </head>
      <body>
        <h1>Hello</h1>
        <p>In a javascript console</p>
        <pre>
          var socket = new io.Socket("localhost");
          socket.on('connect', { "function() { console.log('Connecting to socket') }" });
          socket.on('message', { "function(m) { console.log(m.data); }" });
          socket.send("hello scalatra");
          // Some time passes
          socket.close()
        </pre>
      </body>
    </html>
  }

}