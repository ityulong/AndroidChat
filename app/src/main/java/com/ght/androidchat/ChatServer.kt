package com.ght.androidchat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class ChatServer(
    private val onMessageReceived: (String) -> Unit,
    private val requestedPort: Int = 0
) {
    private var serverSocket: ServerSocket? = null
    private val clientSockets = ConcurrentHashMap<Socket, PrintWriter>()
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    private var isRunning = false

    var serverPort: Int = -1
        private set

    fun start() {
        if (isRunning) {
            onMessageReceived("Server already running.")
            return
        }
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(requestedPort)
                serverPort = serverSocket!!.localPort // Get the actual port
                onMessageReceived("Server started on port $serverPort")

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        val writer = PrintWriter(clientSocket.getOutputStream(), true)
                        clientSockets[clientSocket] = writer
                        onMessageReceived("Client connected: ${clientSocket.inetAddress.hostAddress}")
                        launch { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (isRunning) { // Avoid error message if stopping
                            onMessageReceived("Error accepting client: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                onMessageReceived("Server error: ${e.message}")
                stop() // Ensure cleanup if server fails to start
            } finally {
                isRunning = false
                onMessageReceived("Server stopped listening.")
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
            val clientAddress = clientSocket.inetAddress.hostAddress
            var message: String?
            while (clientSocket.isConnected && reader.readLine().also { message = it } != null) {
                val displayMessage = "Client ($clientAddress): $message"
                onMessageReceived(displayMessage)
                broadcastMessage(displayMessage, clientSocket)
            }
        } catch (e: Exception) {
            // Log error or inform via callback
        } finally {
            clientSockets.remove(clientSocket)
            try {
                clientSocket.close()
            } catch (e: Exception) {
                // Log error
            }
            onMessageReceived("Client disconnected: ${clientSocket.inetAddress.hostAddress}")
        }
    }

    fun sendMessage(message: String) {
        if (!isRunning) {
            onMessageReceived("Server not running.")
            return
        }
        val displayMessage = "You: $message"
        onMessageReceived(displayMessage) // Show in host's UI
        broadcastMessage(displayMessage, null) // Send to all clients
    }

    private fun broadcastMessage(message: String, sender: Socket?) {
        clientSockets.forEach { (socket, writer) ->
            if (socket != sender) { // Don't send back to the original sender
                try {
                    writer.println(message)
                } catch (e: Exception) {
                    // Handle write error, maybe remove client
                    onMessageReceived("Error sending to ${socket.inetAddress.hostAddress}: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        isRunning = false // Signal loops to stop
        try {
            // Close all client sockets
            clientSockets.keys.forEach { socket ->
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Log error
                }
            }
            clientSockets.clear()

            // Close the server socket
            serverSocket?.close()
            serverSocket = null
            onMessageReceived("Server stopped.")
        } catch (e: Exception) {
            onMessageReceived("Error stopping server: ${e.message}")
        }
    }
}
