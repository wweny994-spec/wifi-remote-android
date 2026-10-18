package com.danglas.wifiremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

private val Bg = Color(0xFF0B1015)
private val Panel = Color(0xFF151D24)
private val PanelRaised = Color(0xFF202B34)
private val Teal = Color(0xFF00C2AD)
private val Yellow = Color(0xFFFFC928)
private val TextMain = Color(0xFFF4F7F8)
private val TextMuted = Color(0xFFAAB7BF)

private data class TvDevice(val name: String, val host: String, val port: Int, val serviceType: String)

class MainActivity : ComponentActivity() {
    private lateinit var nsdManager: NsdManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val foundDevices = mutableStateListOf<TvDevice>()
    private var status by mutableStateOf("Ready to search your local Wi-Fi network.")
    private var scanning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        setContent {
            RemoteScreen(
                devices = foundDevices,
                status = status,
                scanning = scanning,
                onDiscover = { startDiscovery() },
                onCheck = { ip -> checkTvPorts(ip) }
            )
        }
    }

    private fun startDiscovery() {
        stopDiscovery()
        foundDevices.clear()
        scanning = true
        status = "Searching for Android TV remote services… Keep the TV awake and on the same Wi-Fi."
        val serviceTypes = listOf("_androidtvremote2._tcp.", "_androidtvremote._tcp.")
        serviceTypes.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    try {
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                runOnUiThread {
                                    status = "Found a network service but couldn't resolve its address. You can try entering the TV IP manually."
                                }
                            }
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val host = info.host?.hostAddress ?: return
                                val device = TvDevice(
                                    name = info.serviceName.ifBlank { "Android TV" },
                                    host = host,
                                    port = info.port,
                                    serviceType = info.serviceType ?: type
                                )
                                runOnUiThread {
                                    if (foundDevices.none { it.host == device.host && it.port == device.port }) {
                                        foundDevices.add(device)
                                    }
                                    status = "Found ${foundDevices.size} possible TV service(s). Select one to check its remote ports."
                                }
                            }
                        })
                    } catch (_: Exception) {
                        runOnUiThread { status = "A TV service was found, but Android couldn't resolve it. Try manual IP entry." }
                    }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    runOnUiThread { foundDevices.removeAll { it.host == host } }
                }
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runOnUiThread { status = "Network scan couldn't start (error $errorCode). Try again or enter the IP manually." }
                    try { nsdManager.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { nsdManager.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
            }
            listeners.add(listener)
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: Exception) {
                status = "Discovery failed to start. Try manual IP entry; some routers block local discovery."
            }
        }
        // Keep discovery open for 12 seconds so slower TVs can advertise themselves.
        android.os.Handler(mainLooper).postDelayed({
            if (scanning) {
                scanning = false
                status = if (foundDevices.isEmpty()) {
                    "No Android TV remote service found. Keep the TV awake, check same Wi-Fi, or try its IP address."
                } else {
                    "Scan finished: ${foundDevices.size} possible TV service(s) found. A service result alone doesn't mean pairing succeeded."
                }
            }
        }, 12_000)
    }

    private fun stopDiscovery() {
        listeners.toList().forEach { listener ->
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        listeners.clear()
    }

    private suspend fun checkTvPorts(ip: String): String = withContext(Dispatchers.IO) {
        val normalized = ip.trim()
        if (!normalized.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))) {
            return@withContext "Enter a valid IPv4 address, such as 192.168.1.50."
        }
        val octets = normalized.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) {
            return@withContext "That IPv4 address is not valid."
        }
        fun isOpen(port: Int): Boolean = try {
            Socket().use { socket -> socket.connect(InetSocketAddress(normalized, port), 1200); true }
        } catch (_: Exception) { false }
        val pairing = isOpen(6467)
        val remote = isOpen(6466)
        when {
            pairing && remote -> "Android TV Remote v2 ports 6467 (pairing) and 6466 (remote) are reachable. Pairing/control code is the next step; this build doesn't send remote commands yet."
            pairing -> "Port 6467 is reachable, but 6466 isn't. The TV may be starting up or its remote service may be limited. This build doesn't pair or send commands yet."
            remote -> "Port 6466 is reachable, but pairing port 6467 isn't. Check that the TV is awake and Android TV Remote Service is enabled. This build doesn't send commands yet."
            else -> "No response on ports 6466/6467. Confirm the TV is awake, both devices use the same Wi-Fi, and the router isn't isolating devices. This is a connectivity check, not proof the TV is incompatible."
        }
    }

    override fun onDestroy() {
        stopDiscovery()
        super.onDestroy()
    }
}

@Composable
private fun RemoteScreen(
    devices: List<TvDevice>,
    status: String,
    scanning: Boolean,
    onDiscover: () -> Unit,
    onCheck: suspend (String) -> String
) {
    var ip by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var showKeyboardInfo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, secondary = Yellow, background = Bg, surface = Panel)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(52.dp).background(Teal.copy(alpha = 0.13f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = Teal, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("WiFi TV Remote", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Walton Android TV · local network", color = TextMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextMuted)
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Teal)
                            Spacer(Modifier.width(8.dp))
                            Text("Find your TV", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.weight(1f))
                            if (scanning) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Teal)
                        }
                        Text("Turn the TV on and connect your phone to the same Wi-Fi network.", color = TextMuted, fontSize = 13.sp)
                        Button(onClick = onDiscover, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color(0xFF001D19)), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (scanning) "Scan again" else "Scan for Android TVs", fontWeight = FontWeight.Bold)
                        }
                        Text(status, color = TextMuted, fontSize = 12.sp)
                        if (devices.isNotEmpty()) {
                            Divider(color = PanelRaised)
                            devices.forEach { device ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = Teal)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(device.name, color = TextMain, fontWeight = FontWeight.SemiBold)
                                        Text("${device.host}:${device.port}", color = TextMuted, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { ip = device.host }) { Text("Use IP", color = Yellow) }
                                }
                            }
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Manual connection check", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("If discovery doesn't find your TV, enter its local IP address. Find it in TV Settings → Network, or your router's device list.", color = TextMuted, fontSize = 12.sp)
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it; checkResult = "" },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("TV IP address") },
                            placeholder = { Text("192.168.1.50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = { scope.launch { checking = true; checkResult = onCheck(ip); checking = false } },
                            enabled = !checking && ip.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color(0xFF201700)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else { Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Check TV connection", fontWeight = FontWeight.Bold) }
                        }
                        if (checkResult.isNotBlank()) Text(checkResult, color = TextMuted, fontSize = 12.sp)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Remote layout preview", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.weight(1f))
                            Text("NOT CONNECTED", color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("These controls are visual previews only in this build. They will be enabled after secure Android TV Remote v2 pairing and command handling are implemented.", color = TextMuted, fontSize = 12.sp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PreviewButton("↑", Modifier.size(62.dp), enabled = false) {}
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PreviewButton("←", Modifier.size(62.dp), enabled = false) {}
                                PreviewButton("OK", Modifier.size(72.dp), enabled = false, accent = true) {}
                                PreviewButton("→", Modifier.size(62.dp), enabled = false) {}
                            }
                            PreviewButton("↓", Modifier.size(62.dp), enabled = false) {}
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PreviewButton("⌂  Home", Modifier.weight(1f).height(46.dp), enabled = false) {}
                            PreviewButton("↩  Back", Modifier.weight(1f).height(46.dp), enabled = false) {}
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PreviewButton("−", Modifier.weight(1f).height(46.dp), enabled = false) {}
                            PreviewButton("Mute", Modifier.weight(1f).height(46.dp), enabled = false) {}
                            PreviewButton("+", Modifier.weight(1f).height(46.dp), enabled = false) {}
                        }
                        Surface(onClick = { showKeyboardInfo = !showKeyboardInfo }, color = PanelRaised, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Keyboard, contentDescription = null, tint = Teal)
                                Spacer(Modifier.width(8.dp))
                                Text("Keyboard input", color = TextMain, modifier = Modifier.weight(1f))
                                Text(if (showKeyboardInfo) "−" else "+", color = Yellow, fontSize = 20.sp)
                            }
                        }
                        if (showKeyboardInfo) Text("Text entry will require the paired Android TV Remote v2 session and is not sent by this build.", color = TextMuted, fontSize = 12.sp)
                    }
                }
                Text("Version 0.2 · Local network only · No analytics", color = TextMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
            }
        }
    }
}

@Composable
private fun PreviewButton(label: String, modifier: Modifier = Modifier, enabled: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (accent) Teal.copy(alpha = 0.22f) else PanelRaised,
        contentColor = if (accent) Teal else TextMain,
        border = BorderStroke(1.dp, if (accent) Teal.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.04f))
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = if (label.length < 3) 22.sp else 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}
