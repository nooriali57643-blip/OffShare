package com.alinoori.offshare

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

const val WATERMARK = "علی نوری / Ali Noori"

// ---------------------------------------------------------------- root

@Composable
fun App(vm: ShareViewModel, languageLabel: String, onToggleLanguage: () -> Unit) {
    BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm, languageLabel, onToggleLanguage)
            Box(Modifier.weight(1f)) {
                when (vm.screen) {
                    Screen.HOME -> HomeScreen(vm)
                    Screen.CREATE -> CreateScreen(vm)
                    Screen.JOIN_MENU -> JoinMenuScreen(vm)
                    Screen.SEARCH -> SearchScreen(vm)
                    Screen.CONNECTED -> ConnectedScreen(vm)
                }
            }
            Watermark()
        }
    }
}

@Composable
private fun TopBar(vm: ShareViewModel, languageLabel: String, onToggleLanguage: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (vm.screen != Screen.HOME) {
            TextButton(onClick = { vm.back() }) { Text(stringResource(R.string.back)) }
        } else {
            Spacer(Modifier.size(1.dp))
        }
        TextButton(onClick = onToggleLanguage) { Text(languageLabel, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Watermark() {
    Text(
        text = WATERMARK,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
    )
}

// ---------------------------------------------------------------- home

@Composable
private fun HomeScreen(vm: ShareViewModel) {
    val gate = rememberPermissionGate()
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_name),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))
        BigButton(stringResource(R.string.create_group), filled = true) {
            gate { vm.open(Screen.CREATE) }
        }
        Spacer(Modifier.height(16.dp))
        BigButton(stringResource(R.string.join_group), filled = false) {
            vm.open(Screen.JOIN_MENU)
        }
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
    if (filled) {
        Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
            Text(text, fontSize = 18.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
            Text(text, fontSize = 18.sp)
        }
    }
}

// ---------------------------------------------------------------- create group

@Composable
private fun CreateScreen(vm: ShareViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = vm.deviceName,
            onValueChange = vm::updateDeviceName,
            label = { Text(stringResource(R.string.device_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.groupName,
            onValueChange = vm::updateGroupName,
            label = { Text(stringResource(R.string.group_name)) },
            placeholder = { Text(vm.deviceName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.rename_hint),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(20.dp))

        if (vm.hostFailed) {
            Text(
                stringResource(R.string.connection_failed),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                stringResource(R.string.group_active),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            val payload = vm.qrPayload
            if (payload != null) {
                val bitmap = remember(payload) { qrBitmap(payload) }
                Box(
                    Modifier
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(240.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.scan_to_join), textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.or_search),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.connected_devices),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (vm.peers.isEmpty()) {
            Text(
                stringResource(R.string.no_devices_yet),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        } else {
            vm.peers.forEach { peer ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(peer.name, modifier = Modifier.padding(16.dp), fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.stop_group))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- join group menu

@Composable
private fun JoinMenuScreen(vm: ShareViewModel) {
    val gate = rememberPermissionGate()
    val prompt = stringResource(R.string.scan_prompt)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.onQrScanned(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.choose_how), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        BigButton(stringResource(R.string.scan_qr), filled = true) {
            gate {
                val options = ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .setPrompt(prompt)
                scanLauncher.launch(options)
            }
        }
        Spacer(Modifier.height(16.dp))
        BigButton(stringResource(R.string.search_group), filled = false) {
            gate { vm.open(Screen.SEARCH) }
        }
        Spacer(Modifier.height(24.dp))
        JoinStatus(vm.joinState)
    }
}

@Composable
private fun JoinStatus(state: JoinState) {
    when (state) {
        JoinState.CONNECTING -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.connecting))
        }
        JoinState.FAILED -> Text(
            stringResource(R.string.connection_failed),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        JoinState.INVALID_QR -> Text(
            stringResource(R.string.qr_invalid),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        JoinState.IDLE -> Unit
    }
}

// ---------------------------------------------------------------- search

@Composable
private fun SearchScreen(vm: ShareViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.searching), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.keep_close),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))

        if (vm.found.isEmpty()) {
            Text(stringResource(R.string.no_groups))
        } else {
            vm.found.forEach { group ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.groupName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.host_label, group.hostName),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Button(onClick = { vm.connect(group) }) {
                            Text(stringResource(R.string.connect))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        JoinStatus(vm.joinState)
    }
}

// ---------------------------------------------------------------- connected (client)

@Composable
private fun ConnectedScreen(vm: ShareViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.connected_to, vm.connectedHost),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.disconnect))
        }
    }
}

// ---------------------------------------------------------------- permissions + QR

private fun requiredPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= 33 -> listOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.NEARBY_WIFI_DEVICES,
    )
    Build.VERSION.SDK_INT >= 31 -> listOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

/** Returns a function that runs an action only after the Nearby permissions are granted. */
@Composable
private fun rememberPermissionGate(): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    val deniedText = stringResource(R.string.permission_needed)
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            pending?.invoke()
        } else {
            Toast.makeText(ctx, deniedText, Toast.LENGTH_LONG).show()
        }
        pending = null
    }
    return { action ->
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            launcher.launch(missing.toTypedArray())
        }
    }
}

private fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
