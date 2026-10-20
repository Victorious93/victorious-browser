// ==UserScript==
// @name         Victorious Dark Mode
// @namespace    com.victorious.browser.examples
// @version      1.0.0
// @description  Soft dark theme for any page (invert + hue-rotate trick). Toggle with Alt+D.
// @match        *://*/*
// @run-at       document-idle
// @grant        GM_addStyle
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_log
// ==/UserScript==
(function () {
  'use strict';
  var KEY = 'vb-dark-on';
  var on = GM_getValue(KEY, false);
  var style = GM_addStyle(
    'html.vb-dark{filter:invert(1) hue-rotate(180deg)!important;}' +
    'html.vb-dark img,html.vb-dark video,html.vb-dark canvas,html.vb-dark iframe' +
    '{filter:invert(1) hue-rotate(180deg)!important;}'
  );
  function apply() {
    document.documentElement.classList.toggle('vb-dark', !!on);
    GM_setValue(KEY, !!on);
    GM_log('dark mode', on ? 'on' : 'off');
  }
  apply();
  window.addEventListener('keydown', function (e) {
    if (e.altKey && (e.key === 'd' || e.key === 'D')) {
      on = !on;
      apply();
    }
  });
})();
