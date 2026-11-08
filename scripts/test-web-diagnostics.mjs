import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const source = fs.readFileSync(new URL('../app/src/main/assets/web-diagnostics.js', import.meta.url), 'utf8');
const logs = [], events = {};
const response = {status: 403, url: 'https://school.edu/api/tasks?token=private', redirected: false};
const originalPromise = Promise.resolve(response);
function XHR() { this.listeners = {}; this.status = 401; this.responseURL = 'https://school.edu/session?ticket=private'; }
XHR.prototype.open = function () { return 'original-open'; };
XHR.prototype.addEventListener = function (name, fn) { this.listeners[name] = fn; };
XHR.prototype.removeEventListener = function (name) { delete this.listeners[name]; };
const element = {tagName:'BODY', children:[], scrollWidth:1200, scrollHeight:1600, getBoundingClientRect() { return {x:0,y:0,width:1200,height:1600,top:0,left:0,bottom:1600,right:1200}; }};
const context = {console: {info: line => logs.push(line)}, URL, Date, JSON, XMLHttpRequest:XHR,
  location:{href:'https://school.edu/page?ticket=private#2026123456'},
  document:{readyState:'complete', documentElement:element, scripts:[], styleSheets:[], images:[], querySelectorAll:() => [element], querySelector: selector => selector.includes('viewport') ? null : element, addEventListener(name, fn) { events[name] = fn; }},
  getComputedStyle:() => ({display:'block', visibility:'visible',opacity:'1',overflowX:'visible',overflowY:'visible',position:'static'}),
  innerWidth:360,innerHeight:640,outerWidth:360,outerHeight:640,devicePixelRatio:3,scrollX:0,scrollY:0,
  fetch:() => originalPromise, addEventListener(name,fn) { events[name]=fn; }};
context.window=context; context.top=context;
vm.createContext(context); vm.runInContext(source,context);
const wrappedFetch = context.fetch;
assert.equal(context.fetch('https://school.edu/api/tasks?token=private'), originalPromise, 'fetch must return the original promise');
await originalPromise; await Promise.resolve();
const xhr = new context.XMLHttpRequest();
assert.equal(xhr.open('POST','https://school.edu/api/session?password=private'),'original-open');
xhr.listeners.loadend();
events.error({error:{name:'TypeError', message:'private user data'}, filename:'https://school.edu/app.js?token=private',lineno:12,colno:4});
events.unhandledrejection({reason:{name:'Error', message:'private response body'}});
context.__xatuDiagnosticSnapshot();
vm.runInContext(source,context);
assert.equal(context.fetch,wrappedFetch,'reinjection must not wrap twice');
const result = logs.join('\n');
assert.ok(!result.includes('private'));
assert.ok(!result.includes('2026123456'));
for (const expected of ['dom-snapshot','1200','fetch','403','xhr','401','TypeError','unhandled-rejection']) assert.ok(result.includes(expected), expected);
assert.equal(JSON.parse(logs.find(line=>line.includes('dom-snapshot')).slice('XATU_DIAG:'.length)).data.inner[0],360);
console.log('WebView diagnostic script: network status, layout, errors, redaction and reinjection checks passed.');
