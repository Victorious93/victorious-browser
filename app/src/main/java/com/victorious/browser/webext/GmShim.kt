package com.victorious.browser.webext

import org.json.JSONObject

/**
 * Builds the injected wrapper + tiny GM API shim for one extension.
 *
 * Real: GM_getValue / GM_setValue / GM_deleteValue / GM_listValues (via the
 * [ExtensionJsBridge] JS interface, scoped to the extension id and its @grant list),
 * GM_addStyle, GM_log, GM_info, unsafeWindow (= window; no sandbox isolation).
 * Stubbed: GM_xmlhttpRequest (logs + calls onerror — no cross-origin fetch),
 * GM_openInTab (window.open), GM_setClipboard (navigator.clipboard best-effort),
 * GM_registerMenuCommand (no-op), GM_notification (console). @require is NOT fetched.
 */
object GmShim {
    const val BRIDGE_NAME = "__victoriousGM"

    val SUPPORTED_GRANTS = setOf(
        "GM_getValue", "GM_setValue", "GM_deleteValue", "GM_listValues",
        "GM_addStyle", "GM_log", "GM_info", "unsafeWindow",
    )

    fun needsBridge(ext: Extension): Boolean = ext.grants.any { it in BRIDGE_GRANTS }
    private val BRIDGE_GRANTS = setOf("GM_getValue", "GM_setValue", "GM_deleteValue", "GM_listValues")

    /** JS string literal (JSON-quoted, also escapes `</` and U+2028/9). */
    fun q(s: String): String = JSONObject.quote(s).replace("</", "<\\/")
        .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    fun wrap(ext: Extension): String {
        val id = q(ext.id)
        val run = when (ext.runAt) {
            RunAt.DOCUMENT_IDLE -> "setTimeout(__vbRun,0);"
            else -> "__vbRun();"
        }
        return """
(function(){
var __id=$id;var __g=window.__vbExtDone=window.__vbExtDone||{};
var __k=__id+'@'+location.href;if(__g[__k])return;__g[__k]=1;
var __B=window.$BRIDGE_NAME;var __N=${q(ext.name)};
function __vbRun(){
var unsafeWindow=window;
var GM_info={script:{name:__N,version:${q(ext.version)},namespace:${q(ext.namespace)}},scriptHandler:'Victorious',version:'1'};
function GM_log(){try{console.log.apply(console,['['+__N+']'].concat([].slice.call(arguments)));}catch(e){}}
function GM_getValue(k,d){try{var r=__B?__B.getValue(__id,String(k)):null;return r==null?d:JSON.parse(r);}catch(e){return d;}}
function GM_setValue(k,v){try{if(__B)__B.setValue(__id,String(k),JSON.stringify(v===undefined?null:v));}catch(e){}}
function GM_deleteValue(k){try{if(__B)__B.deleteValue(__id,String(k));}catch(e){}}
function GM_listValues(){try{return __B?JSON.parse(__B.listValues(__id)):[];}catch(e){return [];}}
function GM_addStyle(css){var s=document.createElement('style');s.textContent=String(css);(document.head||document.documentElement).appendChild(s);return s;}
function GM_openInTab(u){try{return window.open(u,'_blank');}catch(e){}}
function GM_setClipboard(t){try{navigator.clipboard.writeText(String(t));}catch(e){}}
function GM_registerMenuCommand(){return 0;}
function GM_notification(d){GM_log('notification',typeof d==='string'?d:(d&&d.text));}
function GM_xmlhttpRequest(o){GM_log('GM_xmlhttpRequest is not supported in Victorious');try{o&&o.onerror&&o.onerror({error:'unsupported'});}catch(e){}}
var GM={getValue:function(k,d){return Promise.resolve(GM_getValue(k,d));},setValue:function(k,v){return Promise.resolve(GM_setValue(k,v));},deleteValue:function(k){return Promise.resolve(GM_deleteValue(k));},listValues:function(){return Promise.resolve(GM_listValues());},addStyle:GM_addStyle,info:GM_info,log:GM_log};
try{
${ext.source}
}catch(e){try{console.error('['+__N+'] error',e);}catch(_){}}
}
$run
})();
""".trimStart()
    }
}
