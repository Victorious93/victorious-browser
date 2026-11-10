package com.victorious.browser.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Android glue that actually opens a non-http(s) link in another app, applying the
 * safety rules [ExternalScheme] / [IntentUri] describe. Kept thin; all the pure
 * classification is tested separately.
 */
object ExternalLaunch {

    sealed interface Result {
        data object Launched : Result
        data object Failed : Result
        /** No app handled an `intent:`; load this http(s) URL back in the WebView instead. */
        data class Fallback(val url: String) : Result
    }

    fun launch(context: Context, url: String): Result {
        val scheme = ExternalScheme.schemeOf(url)
        return if (scheme == "intent") launchIntentUri(context, url) else launchView(context, url)
    }

    private fun launchView(context: Context, url: String): Result {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                // Never carry a page-chosen component/selector; strip to the bare action.
                component = null
                selector = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.Launched
        } catch (_: ActivityNotFoundException) {
            Result.Failed
        } catch (_: Exception) {
            Result.Failed
        }
    }

    private fun launchIntentUri(context: Context, url: String): Result {
        val fallback = IntentUri.browserFallbackUrl(url)
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            return fallback?.let { Result.Fallback(it) } ?: Result.Failed
        }
        // Harden: drop anything the page could use to target a specific component or to
        // smuggle a dangerous data URI back to us.
        intent.component = null
        intent.selector = null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (IntentUri.isUnsafeTarget(intent.dataString)) {
            return fallback?.let { Result.Fallback(it) } ?: Result.Failed
        }
        return try {
            context.startActivity(intent)
            Result.Launched
        } catch (_: ActivityNotFoundException) {
            fallback?.let { Result.Fallback(it) } ?: Result.Failed
        } catch (_: Exception) {
            fallback?.let { Result.Fallback(it) } ?: Result.Failed
        }
    }
}
