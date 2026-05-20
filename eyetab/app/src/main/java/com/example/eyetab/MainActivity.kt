package com.example.eyetab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val cameraPermRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled via poll */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                EyeTabScreen()
            }
        }
    }

    @Composable
    private fun EyeTabScreen() {
        var cameraOk by remember { mutableStateOf(false) }
        var overlayOk by remember { mutableStateOf(false) }
        var accessOk by remember { mutableStateOf(false) }
        var tracking by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                cameraOk = PermissionUtils.hasCameraPermission(this@MainActivity)
                overlayOk = PermissionUtils.hasOverlayPermission(this@MainActivity)
                accessOk = PermissionUtils.isAccessibilityServiceEnabled(this@MainActivity)
                delay(800)
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(32.dp))
                Text("EyeTab", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("눈으로 태블릿을 제어합니다", fontSize = 16.sp, color = Color.Gray)
                Divider()

                PermissionRow("카메라 권한", cameraOk)
                PermissionRow("오버레이 권한", overlayOk)
                PermissionRow("접근성 서비스", accessOk)

                Divider()

                Button(
                    onClick = { cameraPermRequest.launch(android.Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cameraOk
                ) { Text("1. 카메라 권한 요청") }

                Button(
                    onClick = { startActivity(PermissionUtils.overlaySettingsIntent(this@MainActivity)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !overlayOk
                ) { Text("2. 오버레이 권한 설정 열기") }

                Button(
                    onClick = { startActivity(PermissionUtils.accessibilitySettingsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !accessOk
                ) { Text("3. 접근성 서비스 설정 열기") }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (cameraOk && overlayOk && accessOk) {
                            val intent = Intent(this@MainActivity, OverlayCursorService::class.java)
                                .apply { action = OverlayCursorService.ACTION_START }
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                            tracking = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = cameraOk && overlayOk && accessOk && !tracking,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("▶ 추적 시작", fontSize = 18.sp) }

                Button(
                    onClick = {
                        val intent = Intent(this@MainActivity, OverlayCursorService::class.java)
                            .apply { action = OverlayCursorService.ACTION_STOP }
                        startService(intent)
                        tracking = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = tracking,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) { Text("■ 추적 중지", fontSize = 18.sp) }

                Divider()

                Text(
                    text = """
                        사용법:
                        • 짧은 눈 깜빡임(120~600ms) → 탭
                        • 긴 눈 감기(800ms+) → 스크롤
                        • 커서 위치 위쪽 25% → 위로 스크롤
                        • 커서 위치 아래쪽 25% → 아래로 스크롤

                        튜닝 값 (Constants.kt):
                        • MOVEMENT_GAIN = 2.2 (커서 감도)
                        • BLINK_THRESHOLD = 0.35 (깜빡임 민감도)
                        • SMOOTHING_WINDOW = 8 (커서 안정성)
                    """.trimIndent(),
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        }
    }

    @Composable
    private fun PermissionRow(label: String, granted: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 15.sp)
            Text(
                text = if (granted) "✓ OK" else "✗ 필요",
                color = if (granted) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
