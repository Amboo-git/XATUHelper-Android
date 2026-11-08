(function () {
  if (window.__xatuDiagnosticSnapshot) return;
  var count = 0, sendConsole = console.info.bind(console);
  function path(value) {
    try { var u = new URL(String(value), location.href); return u.protocol === 'http:' || u.protocol === 'https:' ? u.origin + u.pathname.replace(/[0-9]{6,}|[A-Za-z0-9_-]{32,}/g, '[redacted]') : '[' + u.protocol + ']'; }
    catch (_) { return '[invalid-url]'; }
  }
  function send(type, fields) {
    if (count++ > 1800) return;
    try { sendConsole('XATU_DIAG:' + JSON.stringify({type: type, frame: path(location.href), main: window === window.top, data: fields})); } catch (_) {}
  }
  function errorClass(value) {
    var name = value && value.name;
    return /^(TypeError|ReferenceError|SyntaxError|RangeError|SecurityError|NetworkError|Error)$/.test(name) ? name : 'unknown';
  }
  window.addEventListener('error', function (e) {
    if (e.target && e.target !== window) send('resource-error', {tag: e.target.tagName, url: path(e.target.src || e.target.href || '')});
    else send('js-error', {kind: errorClass(e.error), source: path(e.filename || ''), line: e.lineno, column: e.colno});
  }, true);
  window.addEventListener('unhandledrejection', function (e) { send('unhandled-rejection', {kind: errorClass(e.reason)}); });
  document.addEventListener('securitypolicyviolation', function (e) { send('csp', {directive: e.effectiveDirective, blocked: path(e.blockedURI)}); });
  function dimensions(el) {
    if (!el) return null;
    var r = el.getBoundingClientRect(), s = getComputedStyle(el);
    return {tag: el.tagName, x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height), children: el.children.length, display: s.display, visibility: s.visibility, opacity: s.opacity, overflowX: s.overflowX, overflowY: s.overflowY, position: s.position};
  }
  window.__xatuDiagnosticSnapshot = function () {
    try {
      var nodes = document.querySelectorAll('body *'), visible = 0, zero = 0, offscreen = 0;
      for (var i = 0; i < Math.min(nodes.length, 2000); i++) {
        var r = nodes[i].getBoundingClientRect();
        if (!r.width || !r.height) zero++; else { visible++; if (r.top >= innerHeight || r.left >= innerWidth || r.bottom < 0 || r.right < 0) offscreen++; }
      }
      var roots = {};
      ['html', 'body', '#app', '#root', 'main', '[role="main"]', 'nav'].forEach(function (s) { roots[s] = dimensions(document.querySelector(s)); });
      var meta = document.querySelector('meta[name="viewport"]');
      send('dom-snapshot', {ready: document.readyState, inner: [innerWidth, innerHeight], outer: [outerWidth, outerHeight], dpr: devicePixelRatio, scroll: [scrollX, scrollY], document: [document.documentElement.scrollWidth, document.documentElement.scrollHeight], visual: window.visualViewport ? [visualViewport.width, visualViewport.height, visualViewport.scale] : null, viewport: meta ? String(meta.content).replace(/[^a-zA-Z0-9=.,\- ]/g, '').slice(0,160) : null, elements: nodes.length, sampled: Math.min(nodes.length,2000), visible: visible, zero: zero, offscreen: offscreen, scripts: document.scripts.length, styles: document.styleSheets.length, frames: document.querySelectorAll('iframe').length, images: document.images.length, brokenImages: Array.prototype.filter.call(document.images, function(im) { return im.complete && !im.naturalWidth; }).length, roots: roots});
    } catch (_) { send('snapshot-error', {}); }
  };
  if (window.fetch) {
    var fetchOriginal = window.fetch;
    window.fetch = function () {
      var url = path(arguments[0] && arguments[0].url || arguments[0]), started = Date.now();
      var result = fetchOriginal.apply(this, arguments);
      result.then(function (r) { send('fetch', {url: url, final: path(r.url), status: r.status, redirected: r.redirected, ms: Date.now()-started}); }, function (e) { send('fetch-error', {url: url, kind: errorClass(e), ms: Date.now()-started}); });
      return result;
    };
  }
  var openOriginal = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    var xhr = this, url = path(arguments[1]), started = Date.now();
    xhr.addEventListener('loadend', function done() { xhr.removeEventListener('loadend', done); send('xhr', {url: url, final: path(xhr.responseURL), status: xhr.status, ms: Date.now()-started}); });
    return openOriginal.apply(this, arguments);
  };
  ['DOMContentLoaded', 'load', 'pageshow', 'pagehide', 'resize', 'hashchange'].forEach(function (name) { window.addEventListener(name, function () { send(name, {}); if (name !== 'pagehide') window.__xatuDiagnosticSnapshot(); }); });
  send('installed', {ready: document.readyState});
})();
