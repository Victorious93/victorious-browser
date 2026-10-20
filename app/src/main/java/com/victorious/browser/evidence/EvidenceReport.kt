package com.victorious.browser.evidence

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates the Markdown + HTML report that summarizes an evidence case for the
 * exported ZIP. Pure logic — no Android, no I/O — so it is unit-tested.
 */
object EvidenceReport {

    private fun fmt(ts: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ts))
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun markdown(case: EvidenceCase): String {
        val sb = StringBuilder()
        sb.append("# Evidence Case: ").append(case.name).append("\n\n")
        sb.append("- Case ID: `").append(case.id).append("`\n")
        sb.append("- Created: ").append(fmt(case.createdAt)).append("\n")
        sb.append("- Items: ").append(case.items.size).append("\n")
        sb.append("  - Screenshots: ").append(case.countByType(EvidenceType.SCREENSHOT)).append("\n")
        sb.append("  - Requests: ").append(case.countByType(EvidenceType.REQUEST)).append("\n")
        sb.append("  - Findings: ").append(case.countByType(EvidenceType.FINDING)).append("\n")
        sb.append("  - Notes: ").append(case.countByType(EvidenceType.NOTE)).append("\n\n")
        sb.append("> Secrets (cookies, auth, proxy creds, tokens, certs) are stripped from saved request/response evidence.\n\n")
        sb.append("---\n\n")
        case.items.sortedBy { it.timestamp }.forEachIndexed { i, item ->
            sb.append("## ").append(i + 1).append(". [").append(item.type.name).append("] ")
                .append(item.title).append("\n\n")
            sb.append("- Time: ").append(fmt(item.timestamp)).append("\n")
            if (item.host.isNotEmpty()) sb.append("- Host: ").append(item.host).append("\n")
            item.fileRef?.let { sb.append("- File: `").append(it).append("`\n") }
            item.note?.let { sb.append("- Note: ").append(it).append("\n") }
            if (item.type == EvidenceType.SCREENSHOT && item.fileRef != null) {
                sb.append("\n![").append(esc(item.title)).append("](").append(item.fileRef).append(")\n")
            }
            item.data?.let {
                sb.append("\n```\n").append(it).append("\n```\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun html(case: EvidenceCase): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n")
        sb.append("<title>Evidence Case: ").append(esc(case.name)).append("</title>\n")
        sb.append("<style>body{font-family:sans-serif;background:#1b1b1b;color:#eee;max-width:900px;margin:auto;padding:16px}")
        sb.append("h1,h2{color:#00e5ff}code{color:#ff00e5}pre{background:#000;padding:8px;overflow:auto}")
        sb.append("img{max-width:100%;border:1px solid #444}</style></head><body>\n")
        sb.append("<h1>Evidence Case: ").append(esc(case.name)).append("</h1>\n")
        sb.append("<ul>")
        sb.append("<li>Case ID: <code>").append(esc(case.id)).append("</code></li>")
        sb.append("<li>Created: ").append(esc(fmt(case.createdAt))).append("</li>")
        sb.append("<li>Items: ").append(case.items.size).append("</li>")
        sb.append("</ul>\n")
        sb.append("<p><em>Secrets (cookies, auth, proxy creds, tokens, certs) are stripped from saved request/response evidence.</em></p>\n")
        case.items.sortedBy { it.timestamp }.forEachIndexed { i, item ->
            sb.append("<h2>").append(i + 1).append(". [").append(item.type.name).append("] ")
                .append(esc(item.title)).append("</h2>\n")
            sb.append("<ul><li>Time: ").append(esc(fmt(item.timestamp))).append("</li>")
            if (item.host.isNotEmpty()) sb.append("<li>Host: ").append(esc(item.host)).append("</li>")
            item.note?.let { sb.append("<li>Note: ").append(esc(it)).append("</li>") }
            sb.append("</ul>\n")
            if (item.type == EvidenceType.SCREENSHOT && item.fileRef != null) {
                sb.append("<img src=\"").append(esc(item.fileRef)).append("\" alt=\"")
                    .append(esc(item.title)).append("\">\n")
            }
            item.data?.let { sb.append("<pre>").append(esc(it)).append("</pre>\n") }
        }
        sb.append("</body></html>\n")
        return sb.toString()
    }
}
