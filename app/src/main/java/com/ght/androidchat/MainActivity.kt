package com.ght.androidchat

import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var chatAdapter: ChatMessageAdapter
    private val messagesList = mutableListOf<String>()

    private var chatServer: ChatServer? = null
    private var chatClient: ChatClient? = null
    private var nsdHelper: NsdHelper? = null

    private var isHost = false
    private val uiHandler = Handler(Looper.getMainLooper())

    // Assume these buttons are in your activity_main.xml
    private lateinit var buttonHost: Button
    private lateinit var buttonJoin: Button
    private lateinit var inputLayout: LinearLayout // Assuming inputLayout contains EditText and Send button

    companion object {
        private const val TAG = "MainActivity"
        private const val SERVICE_NAME_PREFIX = "ChatApp"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI elements
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonHost = findViewById(R.id.buttonHost) // Assume ID buttonHost in XML
        buttonJoin = findViewById(R.id.buttonJoin) // Assume ID buttonJoin in XML
        inputLayout = findViewById(R.id.inputLayout) // Assume ID inputLayout in XML

        // Initially hide chat UI
        recyclerViewMessages.visibility = View.GONE
        inputLayout.visibility = View.GONE

        // Initialize chat adapter and RecyclerView
        chatAdapter = ChatMessageAdapter(messagesList)
        recyclerViewMessages.adapter = chatAdapter
        recyclerViewMessages.layoutManager = LinearLayoutManager(this)

        // Initialize NSD Helper
        nsdHelper = NsdHelper(this, serviceResolvedCallback = { serviceInfo ->
            if (!isHost) {
                Log.d(TAG, "Service resolved: ${serviceInfo.serviceName}, Host: ${serviceInfo.host}, Port: ${serviceInfo.port}")
                val hostAddress = serviceInfo.host?.hostAddress
                if (hostAddress != null) {
                    chatClient = ChatClient(hostAddress, serviceInfo.port, messageCallback)
                    chatClient?.connect()
                    nsdHelper?.stopDiscovery()
                    runOnUiThread {
                        buttonHost.isEnabled = false
                        buttonJoin.isEnabled = false
                        recyclerViewMessages.visibility = View.VISIBLE
                        inputLayout.visibility = View.VISIBLE
                        addMessageToUi("Connecting to ${serviceInfo.serviceName} at $hostAddress:${serviceInfo.port}...")
                    }
                } else {
                    Log.e(TAG, "Resolved service host is null.")
                    addMessageToUi("Error: Could not get host address for ${serviceInfo.serviceName}.")
                }
            }
        })

        buttonHost.setOnClickListener {
            isHost = true
            chatServer = ChatServer(messageCallback)
            chatServer?.start() // Server will find an available port

            // Wait a moment for the server to get its port, then register
            uiHandler.postDelayed({
                val actualPort = chatServer?.serverPort ?: -1
                if (actualPort != -1) {
                    nsdHelper?.registerService(actualPort, SERVICE_NAME_PREFIX)
                    addMessageToUi("Hosting on port $actualPort. Service registered.")
                } else {
                    addMessageToUi("Error: Could not start server or get port.")
                    Log.e(TAG, "Server port not available after start.")
                    isHost = false // Reset state
                    return@postDelayed
                }
                buttonHost.isEnabled = false
                buttonJoin.isEnabled = false
                recyclerViewMessages.visibility = View.VISIBLE
                inputLayout.visibility = View.VISIBLE
            }, 1000) // 1 second delay for server port to be available
        }

        buttonJoin.setOnClickListener {
            isHost = false
            nsdHelper?.discoverServices()
            addMessageToUi("Discovering services...")
            buttonHost.isEnabled = false
            buttonJoin.isEnabled = false
            // Chat UI will be made visible when a service is resolved and connection attempt begins
        }

        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                val displayMessage = "Me: $message"
                // Add to local UI immediately
                // addMessageToUi(displayMessage) // Server/client will echo it back via callback

                if (isHost) {
                    chatServer?.sendMessage(message)
                } else {
                    chatClient?.sendMessage(message)
                }
                editTextMessage.text.clear()
            }
        }
    }

    private val messageCallback: (String) -> Unit = { message ->
        runOnUiThread { // Ensure UI updates are on the main thread
           addMessageToUi(message)
        }
    }

    private fun addMessageToUi(message: String) {
        messagesList.add(message)
        chatAdapter.notifyItemInserted(messagesList.size - 1)
        recyclerViewMessages.scrollToPosition(messagesList.size - 1)
        Log.d(TAG, "Message to UI: $message")
    }


    override fun onResume() {
        super.onResume()
        // If not host, not connected (chatClient is null or not connected), and buttons are enabled (meaning no process started yet)
        if (!isHost && chatClient == null && buttonJoin.isEnabled) {
            // nsdHelper?.discoverServices() // Or trigger this with a refresh button
            // For now, discovery is manually started by "Join Chat" button.
        }
    }

    override fun onPause() {
        super.onPause()
        if (isHost) {
            // nsdHelper?.unregisterService() // Keep service registered while app is in background if hosting
        } else {
            // nsdHelper?.stopDiscovery() // Stop discovery if client is pausing
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatServer?.stop()
        nsdHelper?.unregisterService() // Always unregister on destroy if it was registered
        nsdHelper?.stopDiscovery() // Stop discovery
        // nsdHelper?.tearDown() // If you have a combined method
        chatClient?.disconnect()
        Log.d(TAG, "onDestroy called, services stopped/disconnected.")
    }
}