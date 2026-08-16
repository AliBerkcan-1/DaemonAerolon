package com.aerolon.daemon

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {

    private var hasMicPermission by mutableStateOf(false)
    private var hasContactsPermission by mutableStateOf(false)
    private var hasCallPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var isDaemonRunning by mutableStateOf(false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasContactsPermission = permissions[Manifest.permission.READ_CONTACTS] == true
        hasCallPermission = permissions[Manifest.permission.CALL_PHONE] == true
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasMicPermission = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        hasContactsPermission = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED

                        hasCallPermission = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED

                        hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                        hasAccessibilityPermission = isAccessibilityServiceEnabled(this@MainActivity, KeyInterceptorService::class.java)
                        isDaemonRunning = DaemonService.isRunning
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            CyberDashboard(
                hasMicPermission = hasMicPermission,
                hasContactsPermission = hasContactsPermission,
                hasCallPermission = hasCallPermission,
                hasOverlayPermission = hasOverlayPermission,
                hasAccessibilityPermission = hasAccessibilityPermission,
                isDaemonRunning = isDaemonRunning,
                onGrantPermissions = {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.CALL_PHONE
                        )
                    )
                },
                onGrantOverlay = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri()
                    )
                    startActivity(intent)
                },
                onGrantAccessibility = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                },
                onToggleDaemon = { start ->
                    val intent = Intent(this@MainActivity, DaemonService::class.java)
                    if (start) {
                        startForegroundService(intent)
                        isDaemonRunning = true
                    } else {
                        stopService(intent)
                        isDaemonRunning = false
                    }
                }
            )
        }
    }
}

@Composable
fun CyberDashboard(
    hasMicPermission: Boolean,
    hasContactsPermission: Boolean,
    hasCallPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    isDaemonRunning: Boolean,
    onGrantPermissions: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onToggleDaemon: (Boolean) -> Unit
) {
    val bgColor = Color(0xFF0F0F13)
    val neonPurple = Color(0xFFD500F9)
    val neonCyan = Color(0xFF00E5FF)
    val darkGray = Color(0xFF1E1E24)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "SİBER ASİSTAN",
            color = neonCyan,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "KONTROL PANELİ",
            color = neonPurple,
            fontSize = 14.sp,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        PermissionCard(
            title = "Ana İzinler (Mik/Rehber/Arama)",
            isGranted = hasMicPermission && hasContactsPermission && hasCallPermission,
            onClick = onGrantPermissions,
            color = neonCyan
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "Ekran Çizim İzni",
            isGranted = hasOverlayPermission,
            onClick = onGrantOverlay,
            color = neonPurple
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "Tuş Okuyucu",
            isGranted = hasAccessibilityPermission,
            onClick = onGrantAccessibility,
            color = neonCyan
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (hasMicPermission && hasContactsPermission && hasCallPermission && hasOverlayPermission && hasAccessibilityPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, if (isDaemonRunning) neonCyan else darkGray, RoundedCornerShape(16.dp))
                    .background(Color(0xFF15151A), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isDaemonRunning) "DAEMON AKTİF" else "DAEMON UYKUDA",
                        color = if (isDaemonRunning) neonCyan else Color.Gray,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Switch(
                        checked = isDaemonRunning,
                        onCheckedChange = onToggleDaemon,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = neonCyan,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = darkGray,
                            checkedBorderColor = neonCyan,
                            uncheckedBorderColor = darkGray
                        )
                    )
                }
            }
        } else {
            Text(
                text = "Asistanı başlatmak için tüm izinleri verin.",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isGranted) color else Color.DarkGray, RoundedCornerShape(12.dp))
            .background(Color(0xFF15151A), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = if (isGranted) Color.White else Color.Gray,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Button(
            onClick = onClick,
            enabled = !isGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) Color.Transparent else color,
                disabledContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (isGranted) "VERİLDİ" else "İZİN VER",
                color = if (isGranted) color else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}