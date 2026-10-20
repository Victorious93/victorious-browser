package com.victorious.browser.antiphish

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Passive DOM probe: extracts form / brand-hint metadata from a loaded page
 * WITHOUT reading any field values or credentials.
 *
 * Runs once per navigation (onPageFinished). Results feed [PhishingScorer] only.
 */
object PageProbe {

    /**
     * JS that returns a JSON object:
     * {
     *   "title": "...",
     *   "https": true/false,
     *   "hints": ["logo alt", ...],
     *   "forms": [{ action, actionHost, hasPassword, hasUsername, hasHiddenCred, passwordCount }]
     * }
     * Never includes input values.
     */
    val PROBE_JS: String = """
        (function(){
          try {
            var forms = [];
            var nodes = document.querySelectorAll('form');
            for (var i = 0; i < nodes.length; i++) {
              var f = nodes[i];
              var action = f.getAttribute('action') || '';
              var actionAbs = '';
              try { actionAbs = (new URL(action || location.href, location.href)).href; } catch(e) { actionAbs = action; }
              var actionHost = '';
              try { actionHost = (new URL(actionAbs)).hostname; } catch(e) {}
              var inputs = f.querySelectorAll('input');
              var hasPw = false, hasUser = false, hasHidden = false, pwCount = 0;
              for (var j = 0; j < inputs.length; j++) {
                var inp = inputs[j];
                var type = (inp.getAttribute('type') || 'text').toLowerCase();
                var name = ((inp.getAttribute('name') || '') + ' ' + (inp.getAttribute('id') || '') + ' ' + (inp.getAttribute('autocomplete') || '')).toLowerCase();
                var isCredName = /pass|pwd|user|email|login|account|credential/.test(name);
                if (type === 'password') { hasPw = true; pwCount++; }
                if (type === 'email' || type === 'tel' || /user|email|login|account/.test(name)) hasUser = true;
                var style = window.getComputedStyle(inp);
                var off = style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0'
                  || inp.offsetParent === null || (inp.getBoundingClientRect && (inp.getBoundingClientRect().width < 2 || inp.getBoundingClientRect().height < 2)));
                if ((type === 'password' || isCredName) && (off || type === 'hidden')) hasHidden = true;
              }
              forms.push({
                action: actionAbs,
                actionHost: actionHost,
                hasPassword: hasPw,
                hasUsername: hasUser,
                hasHiddenCred: hasHidden,
                passwordCount: pwCount
              });
            }
            var hints = [];
            var imgs = document.querySelectorAll('img[alt], img[title], [aria-label]');
            for (var k = 0; k < Math.min(imgs.length, 20); k++) {
              var el = imgs[k];
              var t = el.getAttribute('alt') || el.getAttribute('title') || el.getAttribute('aria-label') || '';
              if (t && t.length >= 3 && t.length < 80) hints.push(t);
            }
            return JSON.stringify({
              title: document.title || '',
              https: location.protocol === 'https:',
              hints: hints,
              forms: forms
            });
          } catch (e) {
            return JSON.stringify({ title: '', https: location.protocol === 'https:', hints: [], forms: [], error: String(e) });
          }
        })();
    """.trimIndent()

    data class Snapshot(
        val title: String,
        val https: Boolean,
        val forms: List<PageFormSnapshot>,
        val brandHints: List<String>,
    )

    fun parse(json: String?): Snapshot? {
        if (json.isNullOrBlank()) return null
        // evaluateJavascript wraps string results in JSON quotes
        val unquoted = unwrapJsString(json) ?: return null
        return runCatching {
            val o = JSONObject(unquoted)
            val formsArr = o.optJSONArray("forms") ?: JSONArray()
            val forms = (0 until formsArr.length()).map { i ->
                val f = formsArr.getJSONObject(i)
                PageFormSnapshot(
                    actionUrl = f.optString("action").ifBlank { null },
                    actionHost = f.optString("actionHost").ifBlank { null },
                    hasPasswordField = f.optBoolean("hasPassword"),
                    hasUsernameField = f.optBoolean("hasUsername"),
                    hasHiddenCredentialField = f.optBoolean("hasHiddenCred"),
                    passwordFieldCount = f.optInt("passwordCount"),
                )
            }
            val hintsArr = o.optJSONArray("hints") ?: JSONArray()
            val hints = (0 until hintsArr.length()).map { hintsArr.getString(it) }
            Snapshot(
                title = o.optString("title"),
                https = o.optBoolean("https", true),
                forms = forms,
                brandHints = hints,
            )
        }.getOrNull()
    }

    /** Strip the extra JSON-string quoting evaluateJavascript applies to string returns. */
    fun unwrapJsString(raw: String): String? {
        val t = raw.trim()
        if (t == "null" || t.isEmpty()) return null
        if (t.startsWith("\"") && t.endsWith("\"")) {
            return runCatching {
                // Prefer org.json string unescape
                JSONObject("{\"v\":$t}").getString("v")
            }.getOrElse {
                t.substring(1, t.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
            }
        }
        return t
    }

    fun evaluate(webView: WebView, callback: (Snapshot?) -> Unit) {
        webView.evaluateJavascript(PROBE_JS) { raw ->
            callback(parse(raw))
        }
    }
}
