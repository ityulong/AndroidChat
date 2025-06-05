package com.ght.androidchat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class ChatClient(
    private val serverIp: String,
    private val serverPort: Int,
    private val onMessageReceived: (String) -> Unit
) {
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    private var isConnected = false

    fun connect() {
        if (isConnected) {
            onMessageReceived("Already connected.")
            return
        }
        scope.launch {
            try {
                clientSocket = Socket(serverIp, serverPort)
                writer = PrintWriter(clientSocket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(clientSocket!!.inputStream))
                isConnected = true
                onMessageReceived("Connected to server $serverIp:$serverPort")

                // Start listening for messages from the server
                listenForMessages()
            } catch (e: Exception) {
                onMessageReceived("Error connecting to server: ${e.message}")
                disconnect() // Ensure cleanup if connection fails
            }
        }
    }

    private suspend fun listenForMessages() {
        withContext(Dispatchers.IO) {
            try {
                var message: String?
                while (isConnected && clientSocket?.isConnected == true && reader?.readLine().also { message = it } != null) {
                    onMessageReceived("$message") // Server messages are already prefixed
                }
            } catch (e: Exception) {
                if (isConnected) { // Avoid error message if disconnecting normally
                    onMessageReceived("Error receiving message: ${e.message}")
                }
            } finally {
                // This block might be reached if the server closes the connection
                // or if an error occurs during reading.
                if (isConnected) { // If still marked as connected, means it was an unexpected disconnect
                     onMessageReceived("Disconnected from server.")
                }
                disconnect() // Ensure cleanup
            }
        }
    }

    fun sendMessage(message: String) {
        if (!isConnected || writer == null) {
            onMessageReceived("Not connected to server.")
            return
        }
        scope.launch {
            try {
                writer?.println(message)
                // The server will broadcast this message back, including to this client.
                // If you want to display "You: message" immediately:
                // onMessageReceived("You: $message")
                // However, it's often better to let the server confirm receipt and broadcast.
            } catch (e: Exception) {
                onMessageReceived("Error sending message: ${e.message}")
            }
        }
    }

    fun disconnect() {
        if (!isConnected && clientSocket == null) return // Already disconnected or never connected

        isConnected = false // Signal listening loop to stop
        try {
            writer?.close()
            reader?.close()
            clientSocket?.close()
            onMessageReceived("Disconnected.")
        } catch (e: Exception) {
            onMessageReceived("Error disconnecting: ${e.message}")
        } finally {
            writer = null
            reader = null
            clientSocket = null
        }
    }
}
