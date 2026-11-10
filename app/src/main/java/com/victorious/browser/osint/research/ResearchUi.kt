package com.victorious.browser.osint.research

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import org.json.JSONArray

/** Shared neon building blocks + small Android helpers for the Deep Research hub. */

internal val ResearchBg = Color(0xFF0B0F1A)

@Composable
internal fun RCard(title: String? = null, accent: Color = NeonCyan, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
                Spacer(Modifier.height(6.dp))
            }
            content()
        }
    }
}

@Composable
internal fun KV(k: String, v: String?, mono: Boolean = false) {
    if (v.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("$k  ", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
        Text(
            v,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun PivotList(pivots: List<Pivot>, onOpenUrl: (String) -> Unit) {
    pivots.forEach { p ->
        Row(
            Modifier.fillMaxWidth().clickable { onOpenUrl(p.url) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("↗ ", color = NeonMagenta)
            Column(Modifier.weight(1f)) {
                Text(p.label, style = MaterialTheme.typography.bodyMedium)
                Text(p.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SaveRow(onSave: () -> Unit, onCopy: (() -> Unit)? = null) {
    Row {
        TextButton(onClick = onSave) { Text("Save to case", color = NeonMagenta) }
        if (onCopy != null) TextButton(onClick = onCopy) { Text("Copy", color = NeonCyan) }
    }
}

internal fun Context.copyToClipboard(text: String, toast: String = "Copied") {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("research", text))
    Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
}

internal fun Context.shareText(text: String, subject: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, subject)
    startActivity(Intent.createChooser(i, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

/** Small non-secret preferences (favorites, custom engine templates). */
internal class ResearchPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("research_prefs", Context.MODE_PRIVATE)

    var customSearchTemplate: String
        get() = p.getString("custom_search", "") ?: ""
        set(v) = p.edit().putString("custom_search", v).apply()

    var customImageTemplate: String
        get() = p.getString("custom_image", "") ?: ""
        set(v) = p.edit().putString("custom_image", v).apply()

    var favorites: List<String>
        get() = runCatching { JSONArray(p.getString("dork_favorites", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
        set(v) = p.edit().putString("dork_favorites", JSONArray(v.distinct().take(200)).toString()).apply()
}
