package com.fahadh.notifyme.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fahadh.notifyme.AppPrefs
import com.fahadh.notifyme.MainViewModel
import com.fahadh.notifyme.MirrorConnection
import com.fahadh.notifyme.QrCode

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenNotificationSettings: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onScanQr: () -> Unit,
) {
    var showFilter by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showBatteryDialog by remember { mutableStateOf(!isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !isIgnoringBatteryOptimizations(context)) {
                showBatteryDialog = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("Allow background activity") },
            text = {
                Text(
                    "To keep mirroring reliable while the screen is off, allow Notifyme to ignore battery optimization.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        onRequestBatteryOptimization()
                    },
                ) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) { Text("Not now") }
            },
        )
    }

    if (showFilter) {
        BackHandler { showFilter = false }
        FilterScreen(onBack = { showFilter = false })
    } else {
        MainScreen(
            viewModel = viewModel,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenFilter = { showFilter = true },
            onScanQr = onScanQr,
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    onOpenNotificationSettings: () -> Unit,
    onOpenFilter: () -> Unit,
    onScanQr: () -> Unit,
) {
    val context = LocalContext.current
    val role by viewModel.role.collectAsStateWithLifecycle()
    val room by viewModel.room.collectAsStateWithLifecycle()
    val channelId by viewModel.channelId.collectAsStateWithLifecycle()
    val secretKey by viewModel.secretKey.collectAsStateWithLifecycle()
    val e2eSecret by viewModel.e2eSecret.collectAsStateWithLifecycle()
    val connState by viewModel.connectionState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showQr by remember { mutableStateOf(false) }

    val isSender = role == AppPrefs.ROLE_SENDER
    val isActive = connState == MirrorConnection.State.CONNECTED ||
        connState == MirrorConnection.State.CONNECTING

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Notifyme") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("This device", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoleCard(
                    title = "Send",
                    subtitle = "Mirror notifications from this phone",
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    selected = isSender,
                    onClick = { viewModel.setRole(AppPrefs.ROLE_SENDER) },
                    modifier = Modifier.weight(1f),
                )
                RoleCard(
                    title = "Receive",
                    subtitle = "Show your other phone's notifications",
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    selected = !isSender,
                    onClick = { viewModel.setRole(AppPrefs.ROLE_RECEIVER) },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = channelId,
                onValueChange = viewModel::setChannelId,
                label = { Text("Scaledrone channel ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = secretKey,
                onValueChange = viewModel::setSecretKey,
                label = { Text("Scaledrone secret key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = room,
                onValueChange = viewModel::setRoom,
                label = { Text("Room code") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::generateRoom) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Generate code")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (room.isBlank()) viewModel.generateRoom()
                        if (e2eSecret.isBlank()) viewModel.generateE2eSecret()
                        showQr = true
                    },
                    enabled = channelId.isNotBlank() && secretKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Show QR")
                }
                OutlinedButton(
                    onClick = onScanQr,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Scan QR")
                }
            }

            if (isSender) {
                NotificationAccessCard(context, onOpenNotificationSettings)
                OutlinedButton(onClick = onOpenFilter, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose apps to mirror")
                }
            }

            ConnectionStatus(connState, errorMessage)

            if (isActive) {
                Button(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (isSender) "Stop broadcasting" else "Stop receiving")
                }
            } else {
                Button(
                    onClick = viewModel::connect,
                    enabled = room.isNotBlank() && channelId.isNotBlank() && secretKey.isNotBlank() && e2eSecret.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (connState == MirrorConnection.State.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSender) "Start broadcasting" else "Start receiving")
                }
            }
        }
    }

    if (showQr) {
        val payload = viewModel.pairingPayload()
        val qrBitmap = remember(payload) { QrCode.encode(payload) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Pairing QR") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Pairing QR",
                        modifier = Modifier.size(260.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Scan this on the other phone", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NotificationAccessCard(context: Context, onOpen: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(isNotificationAccessEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = isNotificationAccessEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (enabled) "Notification access granted" else "Notification access required",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Notifyme reads notifications through the Android Notification Listener.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpen) { Text(if (enabled) "Open" else "Grant") }
        }
    }
}

@Composable
private fun ConnectionStatus(state: MirrorConnection.State, errorMessage: String?) {
    val (color, text) = when (state) {
        MirrorConnection.State.CONNECTED ->
            MaterialTheme.colorScheme.primary to "Connected"

        MirrorConnection.State.CONNECTING ->
            MaterialTheme.colorScheme.tertiary to "Connecting…"

        MirrorConnection.State.ERROR ->
            MaterialTheme.colorScheme.error to
                (errorMessage?.let { "Connection failed: $it" } ?: "Connection failed")

        MirrorConnection.State.DISCONNECTED ->
            MaterialTheme.colorScheme.onSurfaceVariant to "Not connected"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private data class AppInfo(
    val key: String,
    val packageName: String,
    val userId: Int,
    val label: String,
    val icon: Bitmap?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState(initialValue = emptyList<AppInfo>()) {
        value = loadApps(context)
    }
    var excluded by remember { mutableStateOf(AppPrefs.excludedApps(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose apps to mirror") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                "Unchecked apps are not mirrored. All apps are mirrored by default.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn {
                items(apps, key = { it.key }) { app ->
                    val checked = app.key !in excluded
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                        } else {
                            Box(Modifier.size(36.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (app.userId != 0) "Secure Folder (user ${app.userId})" else app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { on ->
                                val next = if (on) excluded - app.key
                                else excluded + app.key
                                excluded = next
                                AppPrefs.setExcludedApps(context, next)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun isNotificationAccessEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false
    return enabled.contains(context.packageName)
}

private fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val result = LinkedHashMap<String, AppInfo>()

    // Launcher apps in the main profile.
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    for (ri in pm.queryIntentActivities(intent, 0)) {
        val pkg = ri.activityInfo.packageName
        try {
            val ai = pm.getApplicationInfo(pkg, 0)
            val key = "0|$pkg"
            result[key] = AppInfo(
                key = key,
                packageName = pkg,
                userId = 0,
                label = pm.getApplicationLabel(ai).toString(),
                icon = (pm.getApplicationIcon(pkg) as? BitmapDrawable)?.bitmap,
            )
        } catch (_: Exception) {
            // Skip.
        }
    }

    // Apps seen posting notifications (any profile, e.g. Secure Folder).
    for (entry in AppPrefs.seenApps(context)) {
        if (entry in result) continue
        val parts = entry.split("|", limit = 2)
        if (parts.size != 2) continue
        val userId = parts[0].toIntOrNull() ?: continue
        val pkg = parts[1]
        val label = if (userId == 0) {
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                pkg
            }
        } else {
            pkg
        }
        result[entry] = AppInfo(
            key = entry,
            packageName = pkg,
            userId = userId,
            label = label,
            icon = null,
        )
    }

    return result.values.sortedBy { it.label.lowercase() }
}
