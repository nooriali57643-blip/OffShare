package com.alinoori.offshare

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

enum class Screen { HOME, CREATE, JOIN_MENU, SEARCH, CONNECTED }

enum class JoinState { IDLE, CONNECTING, FAILED, INVALID_QR }

data class Peer(val endpointId: String, val name: String)

data class FoundGroup(
    val endpointId: String,
    val code: String,
    val groupName: String,
    val hostName: String,
)

/**
 * Group logic on top of Google Nearby Connections (offline: Wi-Fi Direct / Bluetooth).
 *
 * Host   -> advertises "code|groupName|deviceName" and shows a QR containing "OFFSHARE1|code|groupName".
 * Client -> either searches (lists every advertising group) or scans the QR
 *           (searches silently and connects to the group whose code matches).
 */
class ShareViewModel(app: Application) : AndroidViewModel(app) {

    private val client = Nearby.getConnectionsClient(app)
    private val prefs = app.getSharedPreferences("offshare", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val serviceId = "com.alinoori.offshare"

    var screen by mutableStateOf(Screen.HOME)
        private set
    var deviceName by mutableStateOf(prefs.getString("device_name", null) ?: (Build.MODEL ?: "Android"))
        private set
    var groupName by mutableStateOf(prefs.getString("group_name", "") ?: "")
        private set
    var qrPayload by mutableStateOf<String?>(null)
        private set
    var joinState by mutableStateOf(JoinState.IDLE)
        private set
    var hostFailed by mutableStateOf(false)
        private set
    var connectedHost by mutableStateOf("")
        private set

    val peers = mutableStateListOf<Peer>()
    val found = mutableStateListOf<FoundGroup>()

    private var hosting = false
    private var hostCode: String? = null
    private var pendingCode: String? = null
    private val remoteNames = mutableMapOf<String, String>()

    // ---------- names ----------

    fun updateDeviceName(value: String) {
        deviceName = value.take(NAME_MAX)
        prefs.edit().putString("device_name", deviceName).apply()
        if (hosting) scheduleAdvertiseRestart()
    }

    fun updateGroupName(value: String) {
        groupName = value.take(NAME_MAX)
        prefs.edit().putString("group_name", groupName).apply()
        if (hosting) scheduleAdvertiseRestart()
    }

    private fun clean(value: String) = value.replace('|', ' ').trim().take(NAME_MAX)
    private fun myDevice() = clean(deviceName).ifBlank { "Android" }
    private fun myGroup() = clean(groupName).ifBlank { myDevice() }

    // ---------- navigation ----------

    fun open(target: Screen) {
        when (target) {
            Screen.CREATE -> startHosting()
            Screen.SEARCH -> startSearching()
            else -> Unit
        }
        screen = target
    }

    fun back() {
        when (screen) {
            Screen.CREATE -> {
                stopAll()
                screen = Screen.HOME
            }
            Screen.SEARCH -> {
                stopSearching()
                screen = Screen.JOIN_MENU
            }
            Screen.JOIN_MENU -> {
                stopSearching()
                joinState = JoinState.IDLE
                screen = Screen.HOME
            }
            Screen.CONNECTED -> disconnect()
            Screen.HOME -> Unit
        }
    }

    fun disconnect() {
        stopAll()
        screen = Screen.HOME
    }

    // ---------- host ----------

    private val advertiseRestart = Runnable { if (hosting) advertise() }

    private fun scheduleAdvertiseRestart() {
        handler.removeCallbacks(advertiseRestart)
        handler.postDelayed(advertiseRestart, 700)
    }

    private fun startHosting() {
        if (hosting) return
        hosting = true
        hostFailed = false
        hostCode = newCode()
        advertise()
    }

    private fun advertise() {
        val code = hostCode ?: return
        val group = myGroup()
        qrPayload = "$QR_PREFIX|$code|$group"
        client.stopAdvertising()
        client.startAdvertising(
            "$code|$group|${myDevice()}",
            serviceId,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
        ).addOnFailureListener { hostFailed = true }
    }

    // ---------- client ----------

    fun startSearching() {
        found.clear()
        joinState = JoinState.IDLE
        client.stopDiscovery()
        client.startDiscovery(
            serviceId,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
        ).addOnFailureListener {
            pendingCode = null
            joinState = JoinState.FAILED
        }
    }

    private fun stopSearching() {
        handler.removeCallbacks(connectTimeout)
        pendingCode = null
        client.stopDiscovery()
        found.clear()
    }

    /** Called with the text of a scanned QR code. */
    fun onQrScanned(text: String) {
        val parts = text.split("|")
        if (parts.size < 3 || parts[0] != QR_PREFIX) {
            joinState = JoinState.INVALID_QR
            return
        }
        startSearching()
        pendingCode = parts[1]
        joinState = JoinState.CONNECTING
        handler.removeCallbacks(connectTimeout)
        handler.postDelayed(connectTimeout, 20_000)
    }

    fun connect(group: FoundGroup) {
        joinState = JoinState.CONNECTING
        client.requestConnection(myDevice(), group.endpointId, connectionCallback)
            .addOnFailureListener { joinState = JoinState.FAILED }
    }

    private val connectTimeout = Runnable {
        if (pendingCode != null) {
            pendingCode = null
            joinState = JoinState.FAILED
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val group = parse(endpointId, info.endpointName) ?: return
            found.removeAll { it.endpointId == endpointId }
            found.add(group)
            if (pendingCode == group.code) {
                pendingCode = null
                connect(group)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            found.removeAll { it.endpointId == endpointId }
        }
    }

    // ---------- shared connection handling ----------

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            remoteNames[endpointId] = info.endpointName
            // Auto-accept: a person who scanned the QR (or picked the group) already chose to connect.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (!result.status.isSuccess) {
                if (!hosting) joinState = JoinState.FAILED
                return
            }
            val remote = remoteNames[endpointId].orEmpty()
            if (hosting) {
                if (peers.none { it.endpointId == endpointId }) {
                    peers.add(Peer(endpointId, remote.ifBlank { endpointId }))
                }
            } else {
                handler.removeCallbacks(connectTimeout)
                client.stopDiscovery()
                connectedHost = parse(endpointId, remote)?.hostName ?: remote
                joinState = JoinState.IDLE
                screen = Screen.CONNECTED
            }
        }

        override fun onDisconnected(endpointId: String) {
            peers.removeAll { it.endpointId == endpointId }
            if (!hosting && screen == Screen.CONNECTED) {
                stopAll()
                screen = Screen.HOME
            }
        }
    }

    // File transfer will plug in here in the next step.
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) = Unit
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    // ---------- helpers ----------

    private fun stopAll() {
        handler.removeCallbacks(advertiseRestart)
        handler.removeCallbacks(connectTimeout)
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        hosting = false
        hostCode = null
        pendingCode = null
        qrPayload = null
        hostFailed = false
        joinState = JoinState.IDLE
        connectedHost = ""
        peers.clear()
        found.clear()
        remoteNames.clear()
    }

    private fun parse(endpointId: String, raw: String): FoundGroup? {
        val p = raw.split("|")
        if (p.size < 3) return null
        return FoundGroup(endpointId, p[0], p[1], p[2])
    }

    private fun newCode(): String = (1..6).map { CODE_ALPHABET.random() }.joinToString("")

    override fun onCleared() {
        stopAll()
        super.onCleared()
    }

    private companion object {
        const val QR_PREFIX = "OFFSHARE1"
        const val NAME_MAX = 20
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
