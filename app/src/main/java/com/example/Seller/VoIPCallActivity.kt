package com.example.Seller

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

class VoIPCallActivity : AppCompatActivity() {

    // UI Components
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

    // Audio Components
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var playingJob: Job? = null

    // Signaling
    private var webSocketClient: WebSocketClient? = null
    private var isConnected = false
    private var isCallActive = false
    private var isMuted = AtomicBoolean(false)
    private var isSpeakerOn = false
    private var remoteUserId: String = ""

    // Audio Manager
    private lateinit var audioManager: AudioManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Audio Configuration
    private val sampleRate = 44100
    private val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Permissions
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voip_call)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        initializeViews()
        setupClickListeners()
        
        // Check permissions
        if (!checkPermissions()) {
            requestPermissions()
        }
    }

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
            
            webSocketClient = object : WebSocketClient(serverUri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    runOnUiThread {
                        isConnected = true
                        progressBar.visibility = View.GONE
                        btnConnect.isEnabled = true
                        updateCallStatus("Connected to signaling server", "ws://$ipAddress:$port")
                        connectionSetupCard.visibility = View.GONE
                        callStatusCard.visibility = View.VISIBLE
                        callControlsLayout.visibility = View.VISIBLE
                        btnDisconnect.visibility = View.VISIBLE
                        
                        // Send user info to server
                        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        val userId = currentUser?.uid ?: "seller_${System.currentTimeMillis()}"
                        sendSignalingMessage("register", mapOf("userId" to userId, "type" to "seller"))
                    }
                    Log.d("VoIPCall", "WebSocket connected")
                }

                override fun onMessage(message: String?) {
                    runOnUiThread {
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

            webSocketClient?.connect()
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            btnConnect.isEnabled = true
            Toast.makeText(this, "Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("VoIPCall", "Connection error: ${e.message}")
        }
    }

    private fun sendSignalingMessage(type: String, data: Map<String, Any>) {
        try {
            val message = JSONObject().apply {
                put("type", type)
                val dataObj = JSONObject()
                data.forEach { (key, value) ->
                    dataObj.put(key, value)
                }
                put("data", dataObj)
            }
            webSocketClient?.send(message.toString())
            Log.d("VoIPCall", "Sent signaling message: $type")
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to send signaling message: ${e.message}")
        }
    }

    private fun handleSignalingMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            val data = json.optJSONObject("data") ?: JSONObject()

            Log.d("VoIPCall", "Received signaling message: $type")

            when (type) {
                "call-request" -> {
                    val fromUserId = data.optString("from", "")
                    remoteUserId = fromUserId
                    updateCallStatus("Incoming call from $fromUserId", "")
                    // Show accept/reject UI or auto-answer
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
                    // Handle incoming audio data
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
        
        sendSignalingMessage("call-request", mapOf("to" to targetUserId))
    }

    private fun acceptCall() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        updateCallStatus("Accepting call...", "")
        sendSignalingMessage("call-accepted", mapOf("to" to remoteUserId))
        startAudioCommunication()
    }

    private fun startAudioCommunication() {
        if (!checkPermissions()) {
            return
        }

        isCallActive = true
        updateCallButton()
        updateCallStatus("Call connected", "In call with $remoteUserId")
        
        // Start recording and playing audio
        startRecording()
        startPlaying()
    }

    private fun startRecording() {
        if (isRecording.get()) return
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
            ) * 2

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

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                
                while (isRecording.get() && !isMuted.get()) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0 && isConnected && isCallActive) {
                        // Send audio data via WebSocket
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

    private fun startPlaying() {
        if (isPlaying.get()) return
        
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                audioFormat
            ) * 2

            // Use AudioTrack.Builder for API 23+, fallback to constructor for older versions
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

            playingJob = scope.launch(Dispatchers.IO) {
                // Audio playback will be handled when we receive audio data
                Log.d("VoIPCall", "Started audio playback")
            }
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to start playing: ${e.message}")
        }
    }

    private fun handleIncomingAudio(audioDataBase64: String) {
        try {
            val audioData = android.util.Base64.decode(audioDataBase64, android.util.Base64.NO_WRAP)
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e("VoIPCall", "Failed to handle incoming audio: ${e.message}")
        }
    }

    private fun stopAudio() {
        isRecording.set(false)
        isPlaying.set(false)
        
        recordingJob?.cancel()
        playingJob?.cancel()
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun hangupCall() {
        isCallActive = false
        updateCallStatus("Call ended", "")
        updateCallButton()
        
        sendSignalingMessage("call-ended", mapOf("to" to remoteUserId))
        stopAudio()
        remoteUserId = ""
    }

    private fun toggleMute() {
        val newMuteState = !isMuted.get()
        isMuted.set(newMuteState)
        btnMute.text = if (newMuteState) "🔇" else "🎤"
        btnMute.alpha = if (newMuteState) 0.5f else 1.0f
        Toast.makeText(this, if (newMuteState) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        // Use setCommunicationDevice for API 31+, fallback to setSpeakerphoneOn
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

    private fun disconnect() {
        hangupCall()
        webSocketClient?.close()
        webSocketClient = null
        isConnected = false
        
        connectionSetupCard.visibility = View.VISIBLE
        callStatusCard.visibility = View.GONE
        callControlsLayout.visibility = View.GONE
        btnDisconnect.visibility = View.GONE
        updateCallStatus("", "")
    }

    private fun updateCallStatus(status: String, info: String) {
        tvCallStatus.text = status
        tvConnectionInfo.text = info
    }

    private fun updateCallButton() {
        btnCallHangup.text = if (isCallActive) "📞" else "📞"
        btnCallHangup.alpha = if (isCallActive) 1.0f else 0.7f
    }

    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

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

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        scope.cancel()
        stopAudio()
    }
}
