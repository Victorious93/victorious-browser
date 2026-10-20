package com.victorious.browser.scanner

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Polls the user-configured OOB collaborator endpoint for received interactions.
 * Inert unless [Collaborator.Config.configured]. Parsing is pure ([Collaborator]).
 */
object CollaboratorPoller {

    private val executor = Executors.newSingleThreadExecutor()

    fun poll(
        config: Collaborator.Config,
        onResult: (List<Collaborator.Interaction>, error: String?) -> Unit,
    ) {
        if (!config.configured) {
            onResult(emptyList(), "No collaborator configured.")
            return
        }
        executor.execute {
            try {
                val conn = URL(config.pollUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                val body = (if (code >= 400) conn.errorStream else conn.inputStream)
                    ?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
                conn.disconnect()
                if (code >= 400) {
                    onResult(emptyList(), "Poll endpoint returned HTTP $code.")
                } else {
                    onResult(Collaborator.parseInteractions(body), null)
                }
            } catch (t: Throwable) {
                Log.w("CollaboratorPoller", "poll failed", t)
                onResult(emptyList(), t.message ?: t.javaClass.simpleName)
            }
        }
    }
}
