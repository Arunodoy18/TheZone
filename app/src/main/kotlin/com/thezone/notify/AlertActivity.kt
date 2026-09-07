package com.thezone.notify

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.core.AlertRecord
import com.thezone.packet.AlertText
import com.thezone.packet.PacketCodec

/**
 * Full-screen, screen-waking take-over shown for a WARNING / EXTREME mesh alert —
 * the closest an app gets to the cell-broadcast experience. Shows over the lock
 * screen; dismissed only by the acknowledge button.
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        runCatching {
            val v = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1))
            } else {
                @Suppress("DEPRECATION") v?.vibrate(longArrayOf(0, 500, 200, 500, 200, 800), -1)
            }
        }

        val e = intent
        val category = e.getIntExtra(X_CAT, PacketCodec.ALERT_WARNING)
        val phrase = e.getIntExtra(X_PHRASE, 0)
        val issued = e.getLongExtra(X_ISSUED, System.currentTimeMillis())
        val expires = e.getLongExtra(X_EXPIRES, System.currentTimeMillis())
        val radius = e.getIntExtra(X_RADIUS, 0)

        setContent { AlertScreen(category, phrase, issued, expires, radius) { finish() } }
    }

    @Composable
    private fun AlertScreen(
        category: Int, phrase: Int, issuedAt: Long, expiresAt: Long, radiusM: Int, onAck: () -> Unit,
    ) {
        val bg = if (category >= PacketCodec.ALERT_EXTREME) Color(0xFF7A1206) else Color(0xFFB5311B)
        val now = System.currentTimeMillis()
        val agoMin = ((now - issuedAt) / 60_000).coerceAtLeast(0)
        val leftMin = ((expiresAt - now) / 60_000).coerceAtLeast(0)
        Column(
            Modifier.fillMaxSize().background(bg).padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("EMERGENCY ALERT", color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text(AlertText.categoryName(category), color = Color.White,
                fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Text(AlertText.label(phrase), color = Color.White, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Spacer(Modifier.height(14.dp))
            Text(AlertText.full(phrase), color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp,
                textAlign = TextAlign.Center, lineHeight = 24.sp)
            Spacer(Modifier.height(24.dp))
            Text(
                buildString {
                    append("issued ${agoMin} min ago · valid ${leftMin} min")
                    if (radiusM > 0) append(" · within ~${radiusM} m")
                },
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onAck() }) }
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("I UNDERSTAND", color = bg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    companion object {
        private const val X_CAT = "cat"
        private const val X_PHRASE = "phrase"
        private const val X_ISSUED = "issued"
        private const val X_EXPIRES = "expires"
        private const val X_RADIUS = "radius"

        fun intent(context: Context, rec: AlertRecord): Intent =
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(X_CAT, rec.category)
                .putExtra(X_PHRASE, rec.phraseCode)
                .putExtra(X_ISSUED, rec.issuedAtMillis)
                .putExtra(X_EXPIRES, rec.expiresAtMillis)
                .putExtra(X_RADIUS, rec.radiusMeters)
    }
}
