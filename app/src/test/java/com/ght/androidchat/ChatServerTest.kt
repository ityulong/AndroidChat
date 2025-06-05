package com.ght.androidchat

import org.junit.Test
import org.junit.Assert.*
import java.net.Socket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ChatServerTest {

    @Test
    fun serverStartAndStop() {
        var serverPort = -1
        val server = ChatServer(onMessageReceived = {
            // The serverPort is set within the ChatServer's start method on a background thread
            // We need a way to wait for it or check it after a delay.
            // For this test, we will check it after starting and assume it's set quickly.
        }, requestedPort = 0) // Request port 0 for an available port

        server.start()
        // It takes a moment for the server to start and the port to be assigned.
        // In a real test environment, you might use a latch or a more robust mechanism.
        Thread.sleep(1000) // Simple delay, not ideal but works for this basic test.
        serverPort = server.serverPort

        assertTrue("Server port should be assigned and not -1 or 0", serverPort > 0)

        server.stop()
        // No specific assertion for stop, but if it throws an exception, the test fails.
        // We can also check if the port is released, but that's harder to verify quickly.
        // For now, successful execution of stop() is the main check.
    }

    @Test
    fun clientConnects_ServerReceivesMessage() {
        val latch = CountDownLatch(1)
        var receivedMessage: String? = null
        val testMessage = "Hello Server from Client"
        var serverPort = -1

        val server = ChatServer(onMessageReceived = { message ->
            // The server prefixes messages, e.g., "Client (127.0.0.1): Actual Message"
            // Or status messages like "Client connected", "Server started"
            if (message.contains(testMessage)) {
                receivedMessage = message
                latch.countDown()
            }
            if (message.startsWith("Server started on port")) {
                // serverPort is now known if not checked via server.serverPort directly
            }
        }, requestedPort = 0)
        server.start()
        Thread.sleep(500) // Give server time to start and set port
        serverPort = server.serverPort
        assertTrue("Server port must be available", serverPort > 0)

        var clientSocket: Socket? = null
        try {
            clientSocket = Socket("127.0.0.1", serverPort)
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            writer.println(testMessage)

            assertTrue("Latch should count down within timeout", latch.await(5, TimeUnit.SECONDS))
            assertNotNull("Received message should not be null", receivedMessage)
            assertTrue("Received message should contain the test message", receivedMessage!!.contains(testMessage))
        } catch (e: Exception) {
            fail("Test failed due to exception: ${e.message}")
        }
        finally {
            clientSocket?.close()
            server.stop()
        }
    }

    @Test
    fun serverSendsMessage_ClientReceives() {
        val latch = CountDownLatch(1)
        val testMessage = "Hello Client from Server"
        var clientReceivedMessage: String? = null
        var serverPort = -1

        val server = ChatServer(onMessageReceived = {
            // Server's own messages are also passed to its callback with "You: " prefix
            if (it == "You: $testMessage") {
                // This is the server echoing its own sent message to its callback.
                // The actual client reception is tested below.
            }
        }, requestedPort = 0)
        server.start()
        Thread.sleep(500) // Give server time to start
        serverPort = server.serverPort
        assertTrue("Server port must be available", serverPort > 0)

        var clientSocket: Socket? = null
        val clientThread = Thread {
            try {
                clientSocket = Socket("127.0.0.1", serverPort)
                val reader = BufferedReader(InputStreamReader(clientSocket!!.inputStream))
                val line = reader.readLine() // Server sends "You: message" to clients
                if (line != null && line.contains(testMessage)) {
                     // The server actually sends "You: Hello Client from Server"
                    clientReceivedMessage = line
                    latch.countDown()
                }
            } catch (e: Exception) {
                // Log error or fail test if appropriate
                println("Client error: ${e.message}")
            } finally {
                clientSocket?.close()
            }
        }
        clientThread.start()

        // Give client time to connect
        Thread.sleep(500)
        server.sendMessage(testMessage)

        assertTrue("Latch should count down for client receiving message", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("Client received message should not be null", clientReceivedMessage)
        // The ChatServer's sendMessage method calls onMessageReceived with "You: $message",
        // and broadcastMessage sends this prefixed message.
        assertEquals("Client should receive the exact message sent by server (with 'You:' prefix)", "You: $testMessage", clientReceivedMessage)

        clientThread.join()
        server.stop()
    }
}
