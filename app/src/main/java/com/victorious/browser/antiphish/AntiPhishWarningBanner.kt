package com.victorious.browser.antiphish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Non-blocking advisory banner shown when [PhishingScorer.Result.isRisky].
 * Does not interrupt navigation; user can dismiss or report.
 */
@Composable
fun AntiPhishWarningBanner(
    result: PhishingScorer.Result,
    onDismiss: () -> Unit,
    onReport: () -> Unit,
    onOpenModule: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NeonMagenta.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            "Possible phishing — score ${result.score}/100 (${result.level.name})",
            style = MaterialTheme.typography.titleSmall,
            color = NeonMagenta,
        )
        Text(
            "Host: ${result.host}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        result.reasons.take(4).forEach { r ->
            Text(
                "• ${r.detail}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (result.reasons.size > 4) {
            Text(
                "… +${result.reasons.size - 4} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpenModule != null) {
                TextButton(onClick = onOpenModule) {
                    Text("Details", color = NeonCyan)
                }
            }
            TextButton(onClick = onReport) {
                Text("Report", color = NeonCyan)
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = NeonMagenta)
            }
        }
    }
}
