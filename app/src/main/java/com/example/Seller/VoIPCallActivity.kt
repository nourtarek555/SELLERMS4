// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
// Import for AppCompatActivity.
import androidx.appcompat.app.AppCompatActivity
// Imports for handling permissions.
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
// Imports for Material Design components.
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
// Imports for WebSocket client and coroutines.
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
// Import for JSON handling.
import org.json.JSONObject

/**
 * An activity for handling Voice over IP (VoIP) calls.
 * This class manages the connection to a signaling server, the initiation and reception of calls,
 * and the transmission and reception of audio data.
 */
class VoIPCallActivity : AppCompatActivity() {

    // UI Components.
    private lateinit var etIpAddress: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etRemoteUserId: TextInputEditText
    private lateinit var btnConnect: Button
    private lateinit var btnCallHangup: MaterialButton
    private lateinit var btnMute: MaterialButton
    private lateinit var btnSpeaker: MaterialButton
    private lateinit var btnDisconnect: Button
    private lateinit var tvCallStatus: TextView
    private lateinit var tvConnectionInfo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var connectionSetupCard: androidx.cardview.widget.CardView
    private lateinit var callStatusCard: androidx.cardview.widget.CardView
    private lateinit var callControlsLayout: LinearLayout

    // Audio Components.
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var playingJob: Job? = null

    // Signaling and call state.
    private var webSocketClient: WebSocketClient? = null
    private var isConnected = false
    private var isCallActive = false
    private var isMuted = AtomicBoolean(false)
    private var isSpeakerOn = false
    private var remoteUserId: String = ""

    // Audio Manager and Coroutine Scope.
    private lateinit var audioManager: AudioManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Audio Configuration.
    private val sampleRate = 44100
    private val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Permissions required for VoIP.
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )
    private val PERMISSION_REQUEST_CODE = 100

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Set the content view for the activity.
        setContentView(R.layout.activity_voip_call)

        // Get the AudioManager system service.
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Initialize the views and set up the click listeners.
        initializeViews()
        setupClickListeners()
        
        // Check for the required permissions.
        if (!checkPermissions()) {
            requestPermissions()
        }
    }

    /**
     * Initializes all the UI elements from the layout.
     */
    private fun initializeViews() {
        etIpAddress = findViewById(R.id.etIpAddress)
        etPort = findViewById(R.id.etPort)
        etRemoteUserId = findViewById(R.id.etRemoteUserId)
        btnConnect = findViewById(R.id.btnConnect)
        btnCallHangup = findViewById(R.id.btnCallHangup)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvConnectionInfo = findViewById(R.id.tvConnectionInfo)
        progressBar = findViewById(R.id.progressBar)
        connectionSetupCard = findViewById(R.id.connectionSetupCard)
        callStatusCard = findViewById(R.id.callStatusCard)
        callControlsLayout = findViewById(R.id.callControlsLayout)
    }

    /**
     * Sets up the OnClickListeners for all the buttons.
     */
    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            connectToSignalingServer()
        }

        btnCallHangup.setOnClickListener {
            if (isCallActive) {
                hangupCall()
            } else {
                initiateCall()
            }
        }

        btnMute.setOnClickListener {
            toggleMute()
        }

        btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }

        btnDisconnect.setOnClickListener {
            disconnect()
        }
    }

    /**
     * Connects to the signaling server using a WebSocket.
     */
    private fun connectToSignalingServer() {
        val ipAddress = etIpAddress.text.toString().trim()
        val port = etPort.text.toString().trim()

        if (ipAddress.isEmpty() || port.isEmpty()) {
            Toast.makeText(this, "Please enter IP address and port", Toast.LENGTH_SHORT).show()
            return
        }

        val portNumber = port.toIntOrNull()
        if (portNumber == null || portNumber <= 0 || portNumber > 65535) {
            Toast.makeText(this, "Please enter a valid port number", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnConnect.isEnabled = false
        updateCallStatus("Connecting to signaling server...", "ws://$ipAddress:$port")

        try {
            val serverUri = URI("ws://$ipAddress:$port")
            
            // Create a new WebSocketClient.
            webSocketClient = object : WebSocketClient(serverUri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    runOnUiThread {
                        isConnected = true
                        progressBar.visibility = View.GONE
                        btnConnect.isEnabled = true
                        updateCallStatus("Connected to signaling server", "ws://$ipAddress:$port")
                        // Show the call controls and hide the connection setup.
                        connectionSetupCard.visibility = View.GONE
                        callStatusCard.visibility = View.VISIBLE
                        callControlsLayout.visibility = View.VISIBLE
                        btnDisconnect.visibility = View.VISIBLE
                        
                        // Register the user with the signaling server.
                        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        val userId = currentUser?.uid ?: "seller_${System.currentTimeMillis()}"
                        sendSignalingMessage("register", mapOf("userId" to userId, "type" to "seller"))
                    }
                    Log.d("VoIPCall", "WebSocket connected")
                }

                override fun onMessage(message: String?) {
                    runOnUiThread {
                        // Handle incoming signaling messages.
                        handleSignalingMessage(message ?: "")
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    runOnUiThread {
                        isConnected = false
                        updateCallStatus("Disconnected from signaling server", reason ?: "Connection closed")
                        if (!isFinishing) {
                            Toast.makeText(this@VoIPCallActivity, "Disconnected: $reason", Toast.LENGTH_SHORT).show()
                        }
                        stopAudio()
                    }
                    Log.d("VoIPCall", "WebSocket closed: $reason")
                }

                override fun onError(ex: Exception?) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        btnConnect.isEnabled = true
                        updateCallStatus("Connection failed", ex?.message ?: "Unknown error")
                        Toast.makeText(this@VoIPCallActivity, "Connection error: ${ex?.message}", Toast.LENGTH_LONG).show()
                    }
                    Log.e("VoIPCall", "WebSocket error: ${ex?.message}")
                }
            }

            // Connect the WebSocket client.
            webSocketClient?.connect()
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            btnConnect.isEnabled = true
            Toast.makeText(this, "Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("VoIPCall", "Connection error: ${e.message}")
        }
    }

    /**
     * Sends a signaling message to the server.
     * @param type The type of the message (e.g., "register", "call-request").
     * @param data A map of data to be sent with the message.
     */
    private fun sendSignalingMessage(type: String, data: Map<String, Any>) {
        try {
            // Create a JSON object for the message.
            val message = JSONObject().apply {
                put("type", type)
                val dataObj = JSONObject()
                data.forEach { (key, value) ->
                    dataObj.put(key, value)
                }
                put("data", dataObj)
            }
            // Send the message through the WebSocket.
            webSocketClient?.send(message.toString())
            Log.d("VoIPCall", "Sent signaling message: $type")
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to send signaling message: ${e.message}")
        }
    }

    /**
     * Handles incoming signaling messages from the server.
     * @param message The message received from the server.
     */
    private fun handleSignalingMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            val data = json.optJSONObject("data") ?: JSONObject()

            Log.d("VoIPCall", "Received signaling message: $type")

            // Handle the message based on its type.
            when (type) {
                "call-request" -> {
                    val fromUserId = data.optString("from", "")
                    remoteUserId = fromUserId
                    updateCallStatus("Incoming call from $fromUserId", "")
                    // Automatically accept the call.
                    acceptCall()
                }
                "call-accepted" -> {
                    val fromUserId = data.optString("from", "")
                    remoteUserId = fromUserId
                    updateCallStatus("Call accepted", "Establishing audio connection...")
                    startAudioCommunication()
                }
                "call-rejected" -> {
                    updateCallStatus("Call rejected", "")
                    isCallActive = false
                    updateCallButton()
                }
                "call-ended" -> {
                    updateCallStatus("Call ended", "")
                    hangupCall()
                }
                "audio-data" -> {
                    // Handle incoming audio data.
                    val audioData = data.optString("data", "")
                    if (audioData.isNotEmpty() && isCallActive) {
                        handleIncomingAudio(audioData)
                    }
                }
                "error" -> {
                    val errorMsg = data.optString("message", "Unknown error")
                    Toast.makeText(this, "Signaling error: $errorMsg", Toast.LENGTH_SHORT).show()
                }
                "ready" -> {
                    updateCallStatus("Ready to call", "Enter remote user ID and tap call")
                }
            }
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to handle signaling message: ${e.message}")
        }
    }

    /**
     * Initiates a call to a remote user.
     */
    private fun initiateCall() {
        val targetUserId = etRemoteUserId.text.toString().trim()
        
        if (targetUserId.isEmpty()) {
            Toast.makeText(this, "Please enter remote user ID to call", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        remoteUserId = targetUserId
        updateCallStatus("Calling $targetUserId...", "Waiting for answer")
        
        // Send a "call-request" message to the server.
        sendSignalingMessage("call-request", mapOf("to" to targetUserId))
    }

    /**
     * Accepts an incoming call.
     */
    private fun acceptCall() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        updateCallStatus("Accepting call...", "")
        // Send a "call-accepted" message to the server.
        sendSignalingMessage("call-accepted", mapOf("to" to remoteUserId))
        // Start the audio communication.
        startAudioCommunication()
    }

    /**
     * Starts the audio communication by initializing and starting the AudioRecord and AudioTrack.
     */
    private fun startAudioCommunication() {
        if (!checkPermissions()) {
            return
        }

        isCallActive = true
        updateCallButton()
        updateCallStatus("Call connected", "In call with $remoteUserId")
        
        // Start recording and playing audio.
        startRecording()
        startPlaying()
    }

    /**
     * Starts recording audio from the microphone and sending it to the remote user.
     */
    private fun startRecording() {
        if (isRecording.get()) return
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
            ) * 2

            // Create a new AudioRecord instance.
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("VoIPCall", "AudioRecord initialization failed")
                return
            }

            isRecording.set(true)
            audioRecord?.startRecording()

            // Start a coroutine to read audio data and send it over the WebSocket.
            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                
                while (isRecording.get() && !isMuted.get()) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0 && isConnected && isCallActive) {
                        // Encode the audio data to Base64 and send it.
                        val audioDataBase64 = android.util.Base64.encodeToString(buffer, 0, bytesRead, android.util.Base64.NO_WRAP)
                        sendSignalingMessage("audio-data", mapOf(
                            "to" to remoteUserId,
                            "data" to audioDataBase64,
                            "sampleRate" to sampleRate,
                            "channels" to 1
                        ))
                    }
                }
            }
            
            Log.d("VoIPCall", "Started recording audio")
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to start recording: ${e.message}")
            Toast.makeText(this, "Failed to start audio recording", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts playing incoming audio data.
     */
    private fun startPlaying() {
        if (isPlaying.get()) return
        
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                audioFormat
            ) * 2

            // Create a new AudioTrack instance.
            audioTrack = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(audioFormat)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    android.media.AudioManager.STREAM_VOICE_CALL,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("VoIPCall", "AudioTrack initialization failed")
                return
            }

            isPlaying.set(true)
            audioTrack?.play()

            // Start a coroutine for playing audio (audio is actually played in handleIncomingAudio).
            playingJob = scope.launch(Dispatchers.IO) {
                Log.d("VoIPCall", "Started audio playback")
            }
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to start playing: ${e.message}")
        }
    }

    /**
     * Handles incoming audio data by decoding it and writing it to the AudioTrack.
     * @param audioDataBase64 The Base64 encoded audio data.
     */
    private fun handleIncomingAudio(audioDataBase64: String) {
        try {
            // Decode the Base64 audio data and write it to the AudioTrack.
            val audioData = android.util.Base64.decode(audioDataBase64, android.util.Base64.NO_WRAP)
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to handle incoming audio: ${e.message}")
        }
    }

    /**
     * Stops recording and playing audio.
     */
    private fun stopAudio() {
        isRecording.set(false)
        isPlaying.set(false)
        
        // Cancel the recording and playing jobs.
        recordingJob?.cancel()
        playingJob?.cancel()
        
        // Stop and release the AudioRecord and AudioTrack.
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Hangs up the current call.
     */
    private fun hangupCall() {
        isCallActive = false
        updateCallStatus("Call ended", "")
        updateCallButton()
        
        // Send a "call-ended" message to the server.
        sendSignalingMessage("call-ended", mapOf("to" to remoteUserId))
        // Stop the audio.
        stopAudio()
        remoteUserId = ""
    }

    /**
     * Toggles the mute state of the microphone.
     */
    private fun toggleMute() {
        val newMuteState = !isMuted.get()
        isMuted.set(newMuteState)
        btnMute.text = if (newMuteState) "🔇" else "🎤"
        btnMute.alpha = if (newMuteState) 0.5f else 1.0f
        Toast.makeText(this, if (newMuteState) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    /**
     * Toggles the speakerphone on or off.
     */
    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        // Use the appropriate method for setting the speakerphone based on the Android version.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val devices = audioManager.availableCommunicationDevices
                if (devices.isNotEmpty()) {
                    val targetDevice = if (isSpeakerOn) {
                        devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                            ?: devices.first()
                    } else {
                        devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                            ?: devices.first()
                    }
                    targetDevice?.let { 
                        audioManager.setCommunicationDevice(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("VoIPCall", "Failed to set communication device: ${e.message}")
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = isSpeakerOn
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = isSpeakerOn
        }
        audioManager.mode = if (isSpeakerOn) AudioManager.MODE_NORMAL else AudioManager.MODE_IN_COMMUNICATION
        btnSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.5f
        Toast.makeText(this, if (isSpeakerOn) "Speaker on" else "Speaker off", Toast.LENGTH_SHORT).show()
    }

    /**
     * Disconnects from the signaling server and ends the call.
     */
    private fun disconnect() {
        hangupCall()
        webSocketClient?.close()
        webSocketClient = null
        isConnected = false
        
        // Reset the UI to the initial state.
        connectionSetupCard.visibility = View.VISIBLE
        callStatusCard.visibility = View.GONE
        callControlsLayout.visibility = View.GONE
        btnDisconnect.visibility = View.GONE
        updateCallStatus("", "")
    }

    /**
     * Updates the call status and connection info TextViews.
     * @param status The new call status.
     * @param info The new connection info.
     */
    private fun updateCallStatus(status: String, info: String) {
        tvCallStatus.text = status
        tvConnectionInfo.text = info
    }

    /**
     * Updates the text and appearance of the call/hangup button.
     */
    private fun updateCallButton() {
        btnCallHangup.text = if (isCallActive) "📞" else "📞"
        btnCallHangup.alpha = if (isCallActive) 1.0f else 0.7f
    }

    /**
     * Checks if the required permissions have been granted.
     * @return True if all permissions are granted, false otherwise.
     */
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Requests the required permissions from the user.
     */
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

    /**
     * Called when the user responds to the permission request.
     * @param requestCode The request code passed to requestPermissions().
     * @param permissions The requested permissions.
     * @param grantResults The grant results for the corresponding permissions.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions required for VoIP calling", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Called when the activity is being destroyed.
     */
    override fun onDestroy() {
        // Call the superclass implementation.
        super.onDestroy()
        // Disconnect from the server and clean up resources.
        disconnect()
        scope.cancel()
        stopAudio()
    }
}
