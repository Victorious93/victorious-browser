// ==UserScript==
// @name         Victorious Storage Viewer
// @namespace    com.victorious.browser.examples
// @version      1.0.0
// @description  Floating panel listing document.cookie and localStorage keys (recon helper).
// @match        *://*/*
// @run-at       document-idle
// @grant        GM_addStyle
// @grant        GM_log
// ==/UserScript==
(function () {
  'use strict';
  if (document.getElementById('vb-storage-viewer')) return;
  GM_addStyle(
    '#vb-storage-viewer{position:fixed;z-index:2147483646;right:12px;bottom:12px;max-width:340px;' +
    'max-height:45vh;overflow:auto;background:#0b1220ee;color:#e6f1ff;font:12px/1.4 monospace;' +
    'border:1px solid #00e5ff;border-radius:8px;padding:8px 10px;box-shadow:0 0 12px #00e5ff55;}' +
    '#vb-storage-viewer h3{margin:0 0 6px;color:#00e5ff;font-size:12px;}' +
    '#vb-storage-viewer button{background:#111827;color:#00e5ff;border:1px solid #00e5ff;border-radius:4px;' +
    'padding:2px 8px;margin:0 4px 6px 0;cursor:pointer;}' +
    '#vb-storage-viewer pre{white-space:pre-wrap;word-break:break-all;margin:0;}'
  );
  var box = document.createElement('div');
  box.id = 'vb-storage-viewer';
  function refresh() {
    var ls = [];
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        ls.push(k + '=' + String(localStorage.getItem(k)).slice(0, 120));
      }
    } catch (e) { ls.push('(localStorage blocked)'); }
    var cookie = '';
    try { cookie = document.cookie || '(empty)'; } catch (e) { cookie = '(blocked)'; }
    box.innerHTML =
      '<h3>Victorious Storage Viewer</h3>' +
      '<button type="button" id="vb-sv-refresh">Refresh</button>' +
      '<button type="button" id="vb-sv-close">Close</button>' +
      '<div><b>cookie</b></div><pre>' + cookie.replace(/</g, '&lt;') + '</pre>' +
      '<div><b>localStorage</b> (' + ls.length + ')</div><pre>' +
      (ls.length ? ls.join('\\n') : '(empty)').replace(/</g, '&lt;') + '</pre>';
    var r = document.getElementById('vb-sv-refresh');
    var c = document.getElementById('vb-sv-close');
    if (r) r.onclick = refresh;
    if (c) c.onclick = function () { box.remove(); };
  }
  (document.body || document.documentElement).appendChild(box);
  refresh();
  GM_log('storage viewer mounted');
})();
