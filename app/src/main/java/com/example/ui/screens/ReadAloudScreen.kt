package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.readaloud.ReadAloudController
import com.example.readaloud.ReaderState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Read Aloud control center: permission wiring (accessibility, overlay,
 * mic), feature toggles, and a captured-text preview with test speak.
 */
@Composable
fun ReadAloudScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val bubbleEnabled by ReadAloudController.bubbleEnabled.collectAsStateWithLifecycle()
    val voiceTrigger by ReadAloudController.voiceTriggerEnabled.collectAsStateWithLifecycle()
    val autoScroll by ReadAloudController.autoScrollEnabled.collectAsStateWithLifecycle()
    val readerState by ReadAloudController.state.collectAsStateWithLifecycle()
    val lastText by ReadAloudController.lastText.collectAsStateWithLifecycle()

    // Refresh permission chips while this screen is open.
    LaunchedEffect(Unit) {
        while (true) { tick++; delay(1200) }
    }

    val a11yOn = ReadAloudController.isAccessibilityEnabled(context)
    val overlayOn = ReadAloudController.canDrawOverlays(context)
    val serviceUp = ReadAloudController.serviceConnected()
    val micOn = Build.VERSION.SDK_INT < 23 ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) ReadAloudController.setVoiceTriggerEnabled(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Read Aloud", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (serviceUp) "Service running" else "Enable accessibility to start",
                    color = if (serviceUp) TerminalGreen else TextMuted,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (readerState) {
                            ReaderState.IDLE -> TextMuted
                            ReaderState.LISTENING -> CyberCyan
                            ReaderState.CAPTURING -> CyberCyan
                            ReaderState.SPEAKING -> TerminalGreen
                        }
                    )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Permission checklist ────────────────────────────────────
        SectionRow("PERMISSIONS")
        PermissionRow(
            title = "Accessibility service",
            subtitle = "Lets Spark see and read text in other apps",
            granted = a11yOn,
            action = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        )
        PermissionRow(
            title = "Display over other apps",
            subtitle = "Shows the floating read bubble on top",
            granted = overlayOn,
            action = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        )
        PermissionRow(
            title = "Microphone",
            subtitle = "Needed only for the voice trigger",
            granted = micOn,
            action = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Toggles ─────────────────────────────────────────────────
        SectionRow("BEHAVIOUR")
        if (a11yOn) {
            ToggleRow(
                title = "Floating bubble",
                subtitle = "Draggable button — tap to read, tap again to stop",
                checked = bubbleEnabled,
                onToggle = { ReadAloudController.setBubbleEnabled(it) }
            )
            ToggleRow(
                title = "Voice trigger",
                subtitle = "\"read it\" starts, \"stop\" halts. Drains battery — the mic stays open.",
                checked = voiceTrigger,
                onToggle = { want ->
                    if (want && !micOn) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else ReadAloudController.setVoiceTriggerEnabled(want)
                }
            )
            ToggleRow(
                title = "Auto-scroll capture",
                subtitle = "Scroll the page and stitch the whole article, not just the visible part",
                checked = autoScroll,
                onToggle = { ReadAloudController.setAutoScrollEnabled(it) }
            )
        } else {
            Text(
                text = "Turn on the accessibility service above to unlock these options.",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Test + preview ──────────────────────────────────────────
        SectionRow("TEST")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionPill("Speak test", Icons.Filled.GraphicEq) {
                ReadAloudController.testSpeak(
                    "This is a voice test. If you can hear this clearly, read aloud is ready."
                )
            }
            ActionPill("Read screen", Icons.Filled.VolumeUp) {
                ReadAloudController.readScreen()
            }
            ActionPill("Stop", Icons.Filled.Stop) {
                ReadAloudController.stop()
            }
        }

        if (lastText.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            SectionRow("LAST CAPTURED (${lastText.length} chars)")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = lastText,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 14
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Tip: open any app — WhatsApp, a browser article — then tap the bubble. " +
                "Password fields and secure screens (banks, streaming) can't be read by any app.",
            color = TextMuted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(40.dp))
        // consume tick so the compiler keeps the refresh loop referenced
        if (tick < 0) Text("", color = TextMuted)
    }
}

@Composable
private fun SectionRow(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    action: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .clickable { action() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) TerminalGreen else TerminalRed)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            Text(subtitle, color = TextMuted, fontSize = 11.5.sp)
        }
        Text(
            text = if (granted) "ON" else "GRANT",
            color = if (granted) TerminalGreen else SparkBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            Text(subtitle, color = TextMuted, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SparkBlue,
                checkedTrackColor = DarkSurfaceHighCompat,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

private val DarkSurfaceHighCompat = com.example.ui.theme.DarkSurfaceHigh

@Composable
private fun ActionPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(DarkSurfaceVariant.copy(alpha = 0.8f))
            .border(1.dp, DarkBorder, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}
