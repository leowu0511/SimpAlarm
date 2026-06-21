package com.example.simpalarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.simpalarm.ui.theme.SimpAlarmTheme

private val AlarmBackground = Color(0xFFFBE4E2)
private val AlarmText = Color(0xFF4A1111)
private val AlarmSubText = Color(0xFF7B5151)
private val AlarmPrimary = Color(0xFF55689E)

class AlarmDismissActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val returnToAppOnDismiss =
            intent.getBooleanExtra(SimpAlarmService.EXTRA_RETURN_TO_APP_ON_DISMISS, false)
        setContent {
            SimpAlarmTheme(dynamicColor = false) {
                AlarmDismissScreen(
                    sender = intent.getStringExtra(SimpAlarmService.EXTRA_SENDER_NAME).orEmpty(),
                    message = intent.getStringExtra(SimpAlarmService.EXTRA_MESSAGE_TEXT).orEmpty(),
                    sourceAppLabel = intent.getStringExtra(SimpAlarmService.EXTRA_SOURCE_APP_LABEL).orEmpty(),
                    onDismiss = {
                        stopService(Intent(this, SimpAlarmService::class.java))
                        returnToAppOrFinish(returnToAppOnDismiss)
                    },
                    onOpenInstagram = {
                        stopService(Intent(this, SimpAlarmService::class.java))
                        openSourceApp(intent.getStringExtra(SimpAlarmService.EXTRA_SOURCE_PACKAGE).orEmpty())
                        finish()
                    }
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun openSourceApp(packageName: String) {
        val launchIntent = packageManager
            .getLaunchIntentForPackage(packageName.ifBlank { SimpNotificationListener.INSTAGRAM_PACKAGE })
            ?: return
        startActivity(launchIntent)
    }

    private fun returnToAppOrFinish(returnToApp: Boolean) {
        if (returnToApp) {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(launchIntent)
        }
        finish()
    }
}

@Composable
private fun AlarmDismissScreen(
    sender: String,
    message: String,
    sourceAppLabel: String,
    onDismiss: () -> Unit,
    onOpenInstagram: () -> Unit
) {
    val appLabel = sourceAppLabel.ifBlank { "監聽 App" }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AlarmBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Simp Alarm",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AlarmText
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (sender.isBlank()) "$appLabel 通知來了" else "$sender 傳訊息了",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = AlarmText
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "你設定的對象剛剛傳來 $appLabel 通知。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = AlarmSubText
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = AlarmSubText
                )
            }
            Spacer(modifier = Modifier.height(36.dp))
            SlideToDismiss(
                modifier = Modifier.fillMaxWidth(),
                onDismiss = onDismiss
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenInstagram
            ) {
                Text("打開 $appLabel 回覆")
            }
        }
    }
}

@Composable
private fun SlideToDismiss(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    var trackWidth by remember { mutableFloatStateOf(0f) }
    var thumbOffset by remember { mutableFloatStateOf(0f) }
    val thumbWidth = trackWidth * 0.18f
    val maxOffset = (trackWidth - thumbWidth).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .height(58.dp)
            .background(AlarmPrimary.copy(alpha = 0.18f), shape = MaterialTheme.shapes.extraLarge)
            .onSizeChanged { size ->
                trackWidth = size.width.toFloat()
                thumbOffset = thumbOffset.coerceIn(0f, maxOffset)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "向右滑動關閉鬧鐘",
            color = AlarmText,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { androidx.compose.ui.unit.IntOffset(thumbOffset.roundToInt(), 0) }
                .height(58.dp)
                .fillMaxWidth(0.18f)
                .background(AlarmPrimary, shape = MaterialTheme.shapes.extraLarge)
                .pointerInput(maxOffset) {
                    detectDragGestures(
                        onDragEnd = {
                            if (thumbOffset >= maxOffset * 0.82f) {
                                onDismiss()
                            } else {
                                thumbOffset = 0f
                            }
                        },
                        onDragCancel = {
                            thumbOffset = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        thumbOffset = (thumbOffset + dragAmount.x).coerceIn(0f, maxOffset)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("醒", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
