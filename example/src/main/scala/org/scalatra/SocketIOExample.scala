package org.scalatra

class SocketIOExample extends ScalatraServlet with SocketIOSupport {

  // overriden because otherwise you need a trailing slash for the root url
  // prefer the freedom with or without root.
  override def requestPath = {
    val p = (Option(request.getPathInfo) getOrElse "").trim
    if(p.isEmpty) "/" else p
  }

  socketio("/?") { builder =>
    builder.onMessage { (webSocket, msg) =>
      webSocket.sendMessage("ECHO: %s" format msg)
    }
  }

  get("/?") {
    <html>
      <head><title>WebSocket connection</title></head>
      <body>
        <h1>Hello</h1>
        <p>In a javascript console</p>
        <pre>
          var ws = new WebSocket("ws://localhost:8080/websocket")
          ws.onmessage = { "function(m) { console.log(m.data); };" }
          ws.send("hello scalatra");
          // Some time passes
          ws.close()
        </pre>
      </body>
    </html>
  }

}