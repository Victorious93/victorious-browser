package com.victorious.browser.scanner

import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.EditableRequest

/**
 * Header fuzzing: add/override a small built-in set of request headers commonly
 * used to probe for host-routing, access-control and spoofing issues, then diff
 * responses against a baseline. The mutation logic is pure and unit-tested in
 * [HeaderFuzzerTest]; sending reuses [com.victorious.browser.inspect.ReplayClient].
 */
object HeaderFuzzer {

    data class Mutation(val name: String, val value: String, val note: String)

    /** Built-in mutations. Values reference the target host at apply time. */
    val BUILT_IN: List<Mutation> = listOf(
        Mutation("X-Forwarded-For", "127.0.0.1", "IP spoof / ACL bypass probe"),
        Mutation("X-Forwarded-Host", "{evil}", "Host override via XFH"),
        Mutation("X-Forwarded-Proto", "http", "Scheme downgrade probe"),
        Mutation("X-Real-IP", "127.0.0.1", "Upstream IP spoof probe"),
        Mutation("X-Original-URL", "/admin", "Path override (reverse-proxy ACL)"),
        Mutation("X-Rewrite-URL", "/admin", "Path override (alt header)"),
        Mutation("X-Host", "{evil}", "Host override via X-Host"),
        Mutation("Host", "{evil}", "Host header override"),
        Mutation("X-Forwarded-Server", "{evil}", "Server override probe"),
        Mutation("X-HTTP-Method-Override", "PUT", "Method override probe"),
    )

    /** Placeholder host substituted into "{evil}" mutations. */
    const val EVIL_PLACEHOLDER = "{evil}"

    /**
     * Apply one mutation. Existing headers of the same name are replaced; new
     * headers are appended. [evilHost] fills the {evil} placeholder (use a value
     * the user controls, e.g. a collaborator domain).
     */
    fun apply(req: EditableRequest, mutation: Mutation, evilHost: String): EditableRequest {
        val value = mutation.value.replace(EVIL_PLACEHOLDER, evilHost)
        val existing = req.headers.any { it.name.equals(mutation.name, ignoreCase = true) }
        val headers = if (existing) {
            req.headers.map {
                if (it.name.equals(mutation.name, ignoreCase = true)) CapturedHeader(mutation.name, value) else it
            }
        } else {
            req.headers + CapturedHeader(mutation.name, value)
        }
        return req.copy(headers = headers)
    }

    data class Diff(
        val mutation: Mutation,
        val statusChanged: Boolean,
        val lengthDelta: Int,
        val baselineStatus: Int?,
        val mutatedStatus: Int?,
        val interesting: Boolean,
    )

    /** Compare a mutated response to baseline; flag interesting divergence. */
    fun diff(
        mutation: Mutation,
        baselineStatus: Int?,
        baselineBody: String?,
        mutatedStatus: Int?,
        mutatedBody: String?,
        minDelta: Int = 48,
    ): Diff {
        val statusChanged = baselineStatus != mutatedStatus
        val delta = ResponseDiff.lengthDelta(baselineBody, mutatedBody)
        return Diff(
            mutation = mutation,
            statusChanged = statusChanged,
            lengthDelta = delta,
            baselineStatus = baselineStatus,
            mutatedStatus = mutatedStatus,
            interesting = statusChanged || delta > minDelta,
        )
    }
}
