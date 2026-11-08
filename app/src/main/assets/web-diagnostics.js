(function () {
  if (window.__xatuDiagnosticSnapshot) return;
  var count = 0, sendConsole = console.info.bind(console), templateSelectors = [];
  function path(value) {
    try { var u = new URL(String(value), location.href); return u.protocol === 'http:' || u.protocol === 'https:' ? u.origin + u.pathname.replace(/[0-9]{6,}|[A-Za-z0-9_-]{32,}/g, '[redacted]') : '[' + u.protocol + ']'; }
    catch (_) { return '[invalid-url]'; }
  }
  function send(type, fields) {
    if (count++ > 1800) return;
    try { sendConsole('XATU_DIAG:' + JSON.stringify({type: type, frame: path(location.href), main: window === window.top, route:/^#\/?(jrqd|rwlb)(?:$|[?&])/.test(location.hash || '') ? location.hash.split(/[?&]/)[0] : '[other]', data: fields})); } catch (_) {}
  }
  function errorClass(value) {
    var name = value && value.name;
    return /^(TypeError|ReferenceError|SyntaxError|RangeError|SecurityError|NetworkError|Error)$/.test(name) ? name : 'unknown';
  }
  // Only known dependency errors are retained; never copy arbitrary exception text.
  function errorDetail(value) {
    var message = String(value && value.message || value || ''), missing = message.match(/\b([A-Za-z_$][\w$]{0,40}) is not defined/);
    if (missing) return {reason:'undefined-variable', symbol:missing[1]};
    if (/Bootstrap.*requires.*jQuery/i.test(message)) return {reason:'bootstrap-requires-jquery'};
    var property = message.match(/Cannot read propert(?:y|ies) of (null|undefined)/);
    return {reason:property ? 'read-property-of-' + property[1] : 'omitted'};
  }
  window.addEventListener('error', function (e) {
    if (e.target && e.target !== window) send('resource-error', {tag:e.target.tagName, url:path(e.target.src || e.target.href || '')});
    else send('js-error', {kind:errorClass(e.error), detail:errorDetail(e.error || e.message), source:path(e.filename || ''), line:e.lineno, column:e.colno});
  }, true);
  window.addEventListener('unhandledrejection', function (e) { send('unhandled-rejection', {kind:errorClass(e.reason), detail:errorDetail(e.reason)}); });
  document.addEventListener('securitypolicyviolation', function (e) { send('csp', {directive:e.effectiveDirective, blocked:path(e.blockedURI)}); });
  function rounded(n) { return Math.round(n * 100) / 100; }
  function dimensions(el) {
    if (!el) return null;
    var r = el.getBoundingClientRect(), s = getComputedStyle(el);
    return {tag:el.tagName, x:rounded(r.x), y:rounded(r.y), width:rounded(r.width), height:rounded(r.height), client:[el.clientWidth,el.clientHeight], scroll:[el.scrollWidth,el.scrollHeight], children:el.children.length,
      display:s.display, visibility:s.visibility, opacity:s.opacity, overflowX:s.overflowX, overflowY:s.overflowY, position:s.position, heightCss:s.height, minHeight:s.minHeight, maxHeight:s.maxHeight, flex:s.flex, zIndex:/^-?\d+$/.test(s.zIndex) ? Number(s.zIndex) : 'auto', transform:s.transform !== 'none'};
  }
  function inspect(el) {
    var box = dimensions(el), hidden = [], clips = [], ancestors = [], parent = el, depth = 0;
    var rect = el.getBoundingClientRect();
    while (parent && parent.nodeType === 1 && depth < 8) {
      var s = getComputedStyle(parent), r = parent.getBoundingClientRect();
      if (s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse' || Number(s.opacity) === 0) hidden.push(depth);
      if (depth > 0 && ((/hidden|clip|scroll|auto/.test(s.overflowX) && (rect.left < r.left || rect.right > r.right)) || (/hidden|clip|scroll|auto/.test(s.overflowY) && (rect.top < r.top || rect.bottom > r.bottom)))) clips.push(depth);
      if (depth > 0 && depth <= 5) ancestors.push(dimensions(parent));
      parent = parent.parentElement; depth++;
    }
    var x = (Math.max(0,rect.left) + Math.min(innerWidth,rect.right))/2, y = (Math.max(0,rect.top) + Math.min(innerHeight,rect.bottom))/2;
    var intersects = rect.right > 0 && rect.bottom > 0 && rect.left < innerWidth && rect.top < innerHeight && rect.width > 0 && rect.height > 0;
    var hit = intersects ? document.elementFromPoint(x,y) : null;
    return {box:box, cssHiddenAt:hidden, outsideClipAt:clips, intersectsViewport:intersects, centerHit:hit ? (hit === el || el.contains(hit) ? 'self-or-child' : 'other') : 'none', hitTag:hit && hit.tagName, ancestors:ancestors};
  }
  function componentGroup(label, selector) {
    var nodes;
    try { nodes = document.querySelectorAll(selector); } catch (_) { return; }
    var samples = [];
    for (var i=0; i<Math.min(nodes.length,2); i++) samples.push(inspect(nodes[i]));
    send('components', {label:label, selector:selector, count:nodes.length, samples:samples});
  }
  window.__xatuDiagnosticSnapshot = function () {
    try {
      var nodes = document.querySelectorAll('body *'), nonzero = 0, zero = 0, offscreen = 0, cssHidden = 0;
      for (var i=0; i<Math.min(nodes.length,2000); i++) {
        var r = nodes[i].getBoundingClientRect(), s = getComputedStyle(nodes[i]);
        if (!r.width || !r.height) zero++; else { nonzero++; if (r.top >= innerHeight || r.left >= innerWidth || r.bottom < 0 || r.right < 0) offscreen++; }
        if (s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse' || Number(s.opacity) === 0) cssHidden++;
      }
      var roots = {};
      ['html','body','#app','#root','main','[role="main"]','nav'].forEach(function (s) { roots[s] = dimensions(document.querySelector(s)); });
      var probe = document.createElement('div');
      probe.style.cssText = 'position:fixed;left:-10000px;top:0;width:1px;height:100vh;visibility:hidden;pointer-events:none;contain:strict';
      document.documentElement.appendChild(probe);
      var vh = rounded(probe.getBoundingClientRect().height); probe.remove();
      var meta = document.querySelector('meta[name="viewport"]');
      send('dom-snapshot', {ready:document.readyState, inner:[innerWidth,innerHeight], outer:[outerWidth,outerHeight], dpr:devicePixelRatio, scroll:[scrollX,scrollY], document:[document.documentElement.scrollWidth,document.documentElement.scrollHeight], client:[document.documentElement.clientWidth,document.documentElement.clientHeight], vh100:vh,
        visual:window.visualViewport ? [rounded(visualViewport.width),rounded(visualViewport.height),rounded(visualViewport.scale)] : null, viewport:meta ? String(meta.content).replace(/[^a-zA-Z0-9=.,\- ]/g,'').slice(0,160) : null,
        elements:nodes.length, sampled:Math.min(nodes.length,2000), nonzeroRects:nonzero, zeroRects:zero, offscreen:offscreen, cssHiddenSelf:cssHidden, scripts:document.scripts.length, styles:document.styleSheets.length, frames:document.querySelectorAll('iframe').length, images:document.images.length,
        brokenImages:Array.prototype.filter.call(document.images,function(im) { return im.complete && !im.naturalWidth; }).length, roots:roots,
        dependencies:{jquery:typeof window.jQuery, jqueryVersion:window.jQuery && window.jQuery.fn && window.jQuery.fn.jquery, bootstrapPlugin:!!(window.jQuery && window.jQuery.fn && window.jQuery.fn.modal), vue:typeof window.Vue, requirejs:typeof window.requirejs}});
      [['inputs','input'],['search','input[type="search"],.mint-search,.mint-searchbar,.mint-search-list'],['buttons','button'],['cards','.mint-cell,.mint-cell-wrapper,.mint-cell-title,[role="listitem"]'],['list-containers','ul,[role="list"],.mint-loadmore,.mint-loadmore-content'],['routes','.mint-tab-container,.mint-tab-container-item,.mint-tabbar']].forEach(function(x) { componentGroup(x[0],x[1]); });
      templateSelectors.forEach(function(x) { componentGroup(x.label,x.selector); });
      var structural = [];
      var app = document.querySelector('#app') || document.body;
      if (app) {
        var queue = [{el:app,parent:-1,depth:0}];
        while (queue.length && structural.length < 100) {
          var item = queue.shift(), index = structural.length;
          structural.push({parent:item.parent,depth:item.depth,box:dimensions(item.el)});
          if (item.depth < 5) for (var j=0;j<Math.min(item.el.children.length,30);j++) queue.push({el:item.el.children[j],parent:index,depth:item.depth+1});
        }
      }
      for (var start=0;start<structural.length;start+=12) send('dom-tree',{start:start,nodes:structural.slice(start,start+12)});
      send('script-resources',{items:Array.prototype.slice.call(document.scripts,0,30).map(function(s) { return s.src ? path(s.src) : '[inline]'; })});
    } catch (e) { send('snapshot-error', {kind:errorClass(e)}); }
  };
  function allowed(value) {
    try {
      var u = new URL(String(value),location.href);
      if (u.origin === 'https://ehall.xatu.edu.cn') {
        if (/^\/xsfw\/sys\/swmqdzsapp\/\*default\/modules\/(jrqd|rwlb)\/\1\.html$/.test(u.pathname)) return 'template';
        if (/^\/xsfw\/sys\/swmqdzsapp\/(MobileJrqdController\/getJrqdList|MobileRwlbController\/getRwlbList|MobileSignInPubController\/getSignInDictionaries)\.do$/.test(u.pathname)) return 'business';
        if (/^\/xsfw\/sys\/swmxsqjapp\/MobileServiceStudentApply\/(getStudentLeaveRecords|getSettingInfo)\.do$/.test(u.pathname)) return 'business';
      }
      if (u.origin === 'https://ywtb.xatu.edu.cn' && /^\/affairs\/(sysuser\/selectUserInfo|sysservicecommon\/(getServiceCommonListByRole|selectServiceByCollection)|task\/getFlowInfo)$/.test(u.pathname)) return 'business';
    } catch (_) {}
    return null;
  }
  // Retain shape and lengths only. Unknown field names and all row values stay in memory.
  function shape(value, depth, budget) {
    if (--budget.left < 0) return {type:'truncated'};
    if (value === null) return {type:'null'};
    if (Array.isArray(value)) return {type:'array',length:value.length, item:value.length && depth < 4 ? shape(value[0],depth+1,budget) : undefined};
    if (typeof value !== 'object') return {type:typeof value};
    var keys = Object.keys(value), fields = [];
    if (depth < 4) keys.slice(0,16).forEach(function(key,index) {
      var known = /^(data|datas|rows|list|records|result|results|items|total|count|totalSize|totalCount|page|code|status|success|state|msg|message|jrqdList|rwlbList)$/i.test(key);
      var entry = {field:known ? key : 'field-' + index, shape:shape(value[key],depth+1,budget)};
      if (/^(code|status|state)$/i.test(key)) {
        var v=value[key];
        if ((typeof v === 'number' && Number.isInteger(v) && Math.abs(v) < 100000) || (typeof v === 'string' && /^(?:-?\d{1,5}|success|error|ok|fail|failed|unauthorized|login)$/i.test(v))) entry.code=v;
      }
      if (/^success$/i.test(key) && typeof value[key] === 'boolean') entry.success=value[key];
      fields.push(entry);
    });
    return {type:'object',keyCount:keys.length,fields:fields};
  }
  function summarize(url, value, type) {
    try {
      if (typeof value === 'string' && value.length > 262144) { send('response-summary',{url:url,kind:'too-large'});return; }
      if (type === 'template') {
        var doc = new DOMParser().parseFromString(String(value),'text/html');
        var login = !!doc.querySelector('input[type="password"]');
        var candidates = doc.querySelectorAll('[v-for],input,.mint-search,.mint-cell');
        var added = [];
        if (!login) for(var i=0;i<Math.min(candidates.length,12);i++) {
          var el=candidates[i], classes=Array.prototype.filter.call(el.classList,function(c) { return /^[a-zA-Z][a-zA-Z0-9_-]{0,40}$/.test(c) && /jrqd|rwlb|card|list|search|mint|item|task|sign/i.test(c); }).slice(0,3);
          if (!classes.length) continue;
          var selector=el.tagName.toLowerCase()+'.'+classes.join('.');
          if (templateSelectors.some(function(x) { return x.selector === selector; })) continue;
          var definition={label:'template-'+templateSelectors.length,selector:selector,repeat:el.hasAttribute('v-for')};
          if(templateSelectors.length < 12) { templateSelectors.push(definition);added.push(definition); }
        }
        send('template-summary',{url:url,loginForm:login,repeated:doc.querySelectorAll('[v-for]').length,inputs:doc.querySelectorAll('input').length,selectors:added});
        return;
      }
      if (typeof value === 'string') {
        var text=value.trim();
        if (/^</.test(text)) { send('response-summary',{url:url,kind:'html',loginForm:/type\s*=\s*["']?password/i.test(text)});return; }
        try { value=JSON.parse(text); } catch (_) { send('response-summary',{url:url,kind:'non-json'});return; }
      }
      send('response-summary',{url:url,kind:'json',shape:shape(value,0,{left:70})});
    } catch(e) { send('response-summary',{url:url,kind:'inspection-error',error:errorClass(e)}); }
  }
  function inspectFetch(response, url, type) {
    if (!type) return;
    try {
      var clone=response.clone();
      if (clone.body && clone.body.getReader && window.TextDecoder) {
        var reader=clone.body.getReader(), decoder=new TextDecoder(), size=0, pieces=[];
        function read() { return reader.read().then(function(r) {
          if (r.done) { pieces.push(decoder.decode());summarize(url,pieces.join(''),type);return; }
          size+=r.value.byteLength;
          if(size>262144) { reader.cancel().catch(function(){});send('response-summary',{url:url,kind:'too-large'});return; }
          pieces.push(decoder.decode(r.value,{stream:true})); return read();
        }); }
        read().catch(function() { send('response-summary',{url:url,kind:'read-error'}); });
      } else send('response-summary',{url:url,kind:'stream-unavailable'});
    } catch (_) { send('response-summary',{url:url,kind:'clone-error'}); }
  }
  if(window.fetch) {
    var fetchOriginal=window.fetch;
    window.fetch=function() {
      var raw=arguments[0] && arguments[0].url || arguments[0],url=path(raw),type=allowed(raw),started=Date.now();
      var result=fetchOriginal.apply(this,arguments);
      result.then(function(r) { try { send('fetch',{url:url,final:path(r.url),status:r.status,redirected:r.redirected,ms:Date.now()-started});inspectFetch(r,url,type); } catch (_) {} },function(e) { send('fetch-error',{url:url,kind:errorClass(e),ms:Date.now()-started}); });
      return result;
    };
  }
  var openOriginal=XMLHttpRequest.prototype.open, xhrRequests=new WeakMap();
  XMLHttpRequest.prototype.open=function() {
    var xhr=this,raw=arguments[1],metadata={url:path(raw),type:allowed(raw),started:Date.now()},previous=xhrRequests.get(xhr);
    if(previous) xhr.removeEventListener('loadend',previous.listener);
    var result=openOriginal.apply(this,arguments);
    metadata.listener=function done() {
      xhr.removeEventListener('loadend',done);xhrRequests.delete(xhr);
      try {
        send('xhr',{url:metadata.url,final:path(xhr.responseURL),status:xhr.status,ms:Date.now()-metadata.started});
        if(metadata.type) {
          if(!xhr.responseType || xhr.responseType==='text') summarize(metadata.url,xhr.responseText,metadata.type);
          else if(xhr.responseType==='json') summarize(metadata.url,xhr.response,metadata.type);
          else send('response-summary',{url:metadata.url,kind:'unsupported-response-type'});
        }
      } catch (_) { send('response-summary',{url:metadata.url,kind:'inspection-error'}); }
    };
    xhrRequests.set(xhr,metadata);xhr.addEventListener('loadend',metadata.listener);
    return result;
  };
  var resizeTimer;
  ['DOMContentLoaded','load','pageshow','pagehide','resize','hashchange'].forEach(function(name) { window.addEventListener(name,function() {
    if(name==='resize') { clearTimeout(resizeTimer);resizeTimer=setTimeout(function(){send(name,{});window.__xatuDiagnosticSnapshot();},300);return; }
    send(name,{});if(name!=='pagehide') window.__xatuDiagnosticSnapshot();
  }); });
  send('installed',{version:2,ready:document.readyState});
})();
