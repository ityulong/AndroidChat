package com.ght.androidchat

import org.junit.Test
import org.junit.Assert.*
import java.net.ServerSocket
import java.net.Socket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ChatClientTest {

    private val testPort = 12345 // Fixed port for dummy server

    @Test
    fun clientConnectsAndDisconnects() {
        val connectLatch = CountDownLatch(1)
        val disconnectLatch = CountDownLatch(1)
        var connected = false
        var disconnected = false

        val dummyServerThread = Thread {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = ServerSocket(testPort)
                // Signal that server is ready (optional, client will retry or fail)
                clientSocket = serverSocket.accept() // Wait for client
                // Connection established
                // Keep connection open until client disconnects or test ends
                clientSocket.inputStream.read() // Wait for client to close or send data then close
            } catch (e: Exception) {
                // Silently ignore for this test, or log e.printStackTrace()
            } finally {
                clientSocket?.close()
                serverSocket?.close()
            }
        }
        dummyServerThread.start()
        Thread.sleep(100) // Give server a moment to start

        val client = ChatClient("127.0.0.1", testPort) { message ->
            if (message.startsWith("Connected to server")) {
                connected = true
                connectLatch.countDown()
            }
            if (message == "Disconnected.") { // Assuming this is the message on explicit disconnect
                disconnected = true
                disconnectLatch.countDown()
            }
        }

        client.connect()
        assertTrue("Client should connect within timeout", connectLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Connected flag should be true", connected)

        client.disconnect()
        assertTrue("Client should signal disconnect within timeout", disconnectLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Disconnected flag should be true", disconnected)

        dummyServerThread.join(2000) // Wait for server thread to finish
    }

    @Test
    fun clientSendsMessage_ServerReceives() {
        val latch = CountDownLatch(1)
        val testMessage = "Hello Server from Client"
        var serverReceivedMessage: String? = null

        val dummyServerThread = Thread {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = ServerSocket(testPort)
                clientSocket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                serverReceivedMessage = reader.readLine()
                latch.countDown()
            } catch (e: Exception) {
                // e.printStackTrace()
            } finally {
                clientSocket?.close()
                serverSocket?.close()
            }
        }
        dummyServerThread.start()
        Thread.sleep(100) // Server startup time

        val client = ChatClient("127.0.0.1", testPort) { /* No callback action needed for this test */ }
        client.connect()
        Thread.sleep(500) // Client connect time

        client.sendMessage(testMessage)
        assertTrue("Server should receive message within timeout", latch.await(5, TimeUnit.SECONDS))
        assertEquals("Server received message should match sent message", testMessage, serverReceivedMessage)

        client.disconnect()
        dummyServerThread.join(2000)
    }

    @Test
    fun serverSendsMessage_ClientReceives() {
        val latch = CountDownLatch(1)
        val testMessage = "Hello Client from Server"
        var clientReceivedMessage: String? = null

        val dummyServerThread = Thread {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = ServerSocket(testPort)
                clientSocket = serverSocket.accept()
                val writer = PrintWriter(clientSocket.getOutputStream(), true)
                writer.println(testMessage) // Server sends message
            } catch (e: Exception) {
                // e.printStackTrace()
            } finally {
                // Don't close clientSocket immediately, let client read then client disconnects
                // clientSocket?.close() // This might close before client reads
                serverSocket?.close()
            }
        }
        dummyServerThread.start()
        Thread.sleep(100) // Server startup

        val client = ChatClient("127.0.0.1", testPort) { message ->
            clientReceivedMessage = message
            latch.countDown()
        }
        client.connect() // Connect will start listening

        assertTrue("Client should receive message within timeout", latch.await(5, TimeUnit.SECONDS))
        assertEquals("Client received message should match sent message", testMessage, clientReceivedMessage)

        client.disconnect()
        dummyServerThread.join(2000)
    }
}
