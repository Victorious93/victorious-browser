// ==UserScript==
// @name         Victorious Cookie Pop-up Protection (lite)
// @namespace    com.victorious.browser.privacy
// @version      1.0.0
// @description  Best-effort: clicks "reject"/"necessary only" on common consent banners, else hides them. Also run automatically by Privacy protections → Cookie pop-ups.
// @match        *://*/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==
// Honest limits: heuristic only. Does not read the IAB TCF API, does not handle
// banners inside cross-origin iframes, and may miss or mis-handle custom dialogs.
// Returns "rejected" | "hidden" | "none" for the first pass (used by the dashboard).
(function () {
  if (window.__vicCookiePop) return "none";
  window.__vicCookiePop = true;
  var REJECT = /^(reject( all)?|decline( all)?|deny( all)?|refuse( all)?|only (necessary|essential)( cookies)?|necessary (cookies )?only|use necessary cookies only|continue without accepting|alle ablehnen|ablehnen|tout refuser|refuser|rechazar( todo)?|rifiuta( tutto)?)$/i;
  var KNOWN_BUTTONS = [
    "#onetrust-reject-all-handler", ".ot-pc-refuse-all-handler",
    "#CybotCookiebotDialogBodyButtonDecline", "#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll",
    ".didomi-continue-without-agreeing", "#didomi-notice-disagree-button",
    ".qc-cmp2-summary-buttons button[mode='secondary']", ".fc-cta-do-not-consent",
    "[data-testid='uc-deny-all-button']", ".cmplz-deny", ".cky-btn-reject", "#truste-consent-required"
  ];
  var KNOWN_CONTAINERS = [
    "#onetrust-banner-sdk", "#onetrust-consent-sdk", "#CybotCookiebotDialog", "#didomi-host",
    ".qc-cmp2-container", ".fc-consent-root", "#usercentrics-root", ".cmplz-cookiebanner",
    ".cky-consent-container", "#truste-consent-track", "#cookie-banner", "#cookie-notice",
    ".cookie-banner", ".cookie-consent", "#gdpr-cookie-notice", "[aria-label*='cookie' i][role='dialog']"
  ];
  function visible(el) { return el && el.offsetParent !== null; }
  function tryReject() {
    for (var i = 0; i < KNOWN_BUTTONS.length; i++) {
      var b = document.querySelector(KNOWN_BUTTONS[i]);
      if (visible(b)) { b.click(); return true; }
    }
    var cands = document.querySelectorAll("button, [role='button'], a.button, input[type='button']");
    for (var j = 0; j < cands.length && j < 400; j++) {
      var t = (cands[j].innerText || cands[j].value || "").trim();
      if (t.length < 40 && REJECT.test(t) && visible(cands[j])) {
        var box = cands[j].closest("[class*='cookie' i],[id*='cookie' i],[class*='consent' i],[id*='consent' i],[class*='gdpr' i],[role='dialog']");
        if (box) { cands[j].click(); return true; }
      }
    }
    return false;
  }
  function tryHide() {
    var hid = false;
    for (var i = 0; i < KNOWN_CONTAINERS.length; i++) {
      document.querySelectorAll(KNOWN_CONTAINERS[i]).forEach(function (el) {
        if (visible(el)) { el.style.setProperty("display", "none", "important"); hid = true; }
      });
    }
    if (hid) {
      document.documentElement.style.removeProperty("overflow");
      if (document.body) document.body.style.removeProperty("overflow");
    }
    return hid;
  }
  function pass() { return tryReject() ? "rejected" : (tryHide() ? "hidden" : "none"); }
  var first = pass();
  if (first === "none" && window.MutationObserver) {
    var n = 0;
    var mo = new MutationObserver(function () {
      if (++n > 50) { mo.disconnect(); return; }
      if (pass() !== "none") mo.disconnect();
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });
    setTimeout(function () { mo.disconnect(); }, 8000);
  }
  return first;
})();
