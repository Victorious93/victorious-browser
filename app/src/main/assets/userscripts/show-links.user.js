// ==UserScript==
// @name         Victorious Show All Links
// @namespace    com.victorious.browser.examples
// @version      1.0.0
// @description  Overlay every <a href> on the page for quick recon enumeration.
// @match        *://*/*
// @run-at       document-idle
// @grant        GM_addStyle
// @grant        GM_log
// ==/UserScript==
(function () {
  'use strict';
  if (document.getElementById('vb-show-links')) return;
  GM_addStyle(
    '#vb-show-links{position:fixed;z-index:2147483646;left:12px;bottom:12px;max-width:420px;' +
    'max-height:50vh;overflow:auto;background:#0b1220ee;color:#e6f1ff;font:11px/1.35 monospace;' +
    'border:1px solid #ff00aa;border-radius:8px;padding:8px 10px;box-shadow:0 0 12px #ff00aa55;}' +
    '#vb-show-links h3{margin:0 0 6px;color:#ff00aa;font-size:12px;}' +
    '#vb-show-links a{color:#00e5ff;display:block;text-decoration:none;margin:2px 0;}' +
    '#vb-show-links button{background:#111827;color:#ff00aa;border:1px solid #ff00aa;border-radius:4px;' +
    'padding:2px 8px;margin:0 4px 6px 0;cursor:pointer;}'
  );
  var box = document.createElement('div');
  box.id = 'vb-show-links';
  function collect() {
    var seen = {};
    var out = [];
    Array.prototype.forEach.call(document.querySelectorAll('a[href]'), function (a) {
      try {
        var u = a.href;
        if (!u || seen[u]) return;
        seen[u] = 1;
        out.push(u);
      } catch (e) {}
    });
    out.sort();
    return out;
  }
  function render() {
    var links = collect();
    box.innerHTML =
      '<h3>Show All Links (' + links.length + ')</h3>' +
      '<button type="button" id="vb-sl-refresh">Refresh</button>' +
      '<button type="button" id="vb-sl-close">Close</button>' +
      links.map(function (u) {
        return '<a href="' + u.replace(/"/g, '&quot;') + '" target="_blank" rel="noreferrer">' +
          u.replace(/</g, '&lt;') + '</a>';
      }).join('');
    var r = document.getElementById('vb-sl-refresh');
    var c = document.getElementById('vb-sl-close');
    if (r) r.onclick = render;
    if (c) c.onclick = function () { box.remove(); };
  }
  (document.body || document.documentElement).appendChild(box);
  render();
  GM_log('show-links mounted', collect().length);
})();
