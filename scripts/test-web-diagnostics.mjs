import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const source = fs.readFileSync(new URL('../app/src/main/assets/web-diagnostics.js', import.meta.url), 'utf8');
const logs = [], events = {};
const defaults = {display:'block',visibility:'visible',opacity:'1',overflowX:'visible',overflowY:'visible',position:'static',height:'0px',minHeight:'0px',maxHeight:'none',flex:'0 1 auto',zIndex:'auto',transform:'none'};
function element(tag='DIV',rect={x:0,y:0,width:360,height:640},style={}) {
  return {tagName:tag,nodeType:1,children:[],parentElement:null,clientWidth:rect.width,clientHeight:rect.height,scrollWidth:1200,scrollHeight:1600,style:{},classList:[],computed:{...defaults,...style},
    getBoundingClientRect(){return {...rect,top:rect.y,left:rect.x,bottom:rect.y+rect.height,right:rect.x+rect.width};},
    appendChild(el){this.children.push(el);el.parentElement=this;},remove(){if(this.parentElement)this.parentElement.children=this.parentElement.children.filter(x=>x!==this);},
    contains(el){return el===this||this.children.some(child=>child.contains(el));},hasAttribute(){return false;}};
}
const html=element('HTML'),body=element('BODY'),app=element('DIV',{x:0,y:0,width:360,height:0},{overflowY:'hidden'}),card=element('DIV',{x:10,y:40,width:320,height:100}),input=element('INPUT',{x:0,y:0,width:320,height:30}),overlay=element('DIV');
html.appendChild(body);body.appendChild(app);app.appendChild(card);app.appendChild(input);
const selectors={'html':[html],'body':[body],'#app':[app],'body *':[app,card,input],'input':[input],'input[type="search"],.mint-search,.mint-searchbar,.mint-search-list':[input],'.mint-cell,.mint-cell-wrapper,.mint-cell-title,[role="listitem"]':[card],'div.task-item':[card]};
const templateCard=element();templateCard.classList=['task-item','student-private'];templateCard.hasAttribute=(name)=>name==='v-for';
class Parser { parseFromString(){return {querySelector:()=>null,querySelectorAll:s=>s==='[v-for]'?[templateCard]:s==='input'?[]:[templateCard]};} }
function XHR(){this.listeners={};this.status=200;this.responseURL='https://ehall.xatu.edu.cn/api?ticket=private';this.responseType='';this.responseText='';}
XHR.prototype.open=function(){return 'original-open';};
XHR.prototype.addEventListener=function(name,fn){this.listeners[name]=fn;};
XHR.prototype.removeEventListener=function(name,fn){if(this.listeners[name]===fn)delete this.listeners[name];};
const payload=JSON.stringify({code:0,data:[{studentName:'private',studentNo:'2026123456',nested:{email:'private@example.com'}}]});
let cloneCalls=0;
const response={status:200,url:'https://ehall.xatu.edu.cn/xsfw/sys/swmqdzsapp/MobileJrqdController/getJrqdList.do?token=private',redirected:false,bodyUsed:false,
  clone(){cloneCalls++;let delivered=false;return {body:{getReader(){return {read(){if(delivered)return Promise.resolve({done:true});delivered=true;return Promise.resolve({done:false,value:new TextEncoder().encode(payload)});},cancel(){return Promise.resolve();}};}}};}};
const originalPromise=Promise.resolve(response);
const context={console:{info:line=>logs.push(line)},URL,Date,JSON,XMLHttpRequest:XHR,DOMParser:Parser,TextDecoder,setTimeout,clearTimeout,
  location:{href:'https://ehall.xatu.edu.cn/xsfw/sys/swmqdzsapp/*default/index.do?ticket=private#jrqd'},
  document:{readyState:'complete',documentElement:html,body,scripts:[{src:'https://school.edu/app.js?token=private'}],styleSheets:[],images:[],
    querySelectorAll:s=>selectors[s]||[],querySelector:s=>(selectors[s]||[])[0]||null,elementFromPoint:()=>overlay,
    createElement:()=>element(),addEventListener(name,fn){events[name]=fn;}},
  getComputedStyle:el=>el.computed,innerWidth:360,innerHeight:640,outerWidth:360,outerHeight:640,devicePixelRatio:3,scrollX:0,scrollY:0,
  visualViewport:{width:390.5084745762712,height:751.186440677966,scale:1},fetch:()=>originalPromise,addEventListener(name,fn){events[name]=fn;}};
context.window=context;context.top=context;
vm.createContext(context);vm.runInContext(source,context);
const wrappedFetch=context.fetch;
assert.equal(context.fetch(response.url),originalPromise,'fetch returns the original promise');
await new Promise(resolve=>setTimeout(resolve,5));
assert.equal(response.bodyUsed,false,'diagnostics must not consume the original fetch body');
assert.equal(cloneCalls,1);
function xhr(url,text,type=''){const x=new context.XMLHttpRequest();assert.equal(x.open('POST',url),'original-open');x.responseText=text;x.responseType=type;x.listeners.loadend();return x;}
xhr('/xsfw/sys/swmqdzsapp/MobileJrqdController/getJrqdList.do',payload);
xhr('/xsfw/sys/swmqdzsapp/MobileRwlbController/getRwlbList.do','<html><input type="password" value="private"></html>');
xhr('/xsfw/sys/swmqdzsapp/*default/modules/jrqd/jrqd.html','<div class="task-item" v-for="private"></div>');
xhr('https://evil.example/xsfw/sys/swmqdzsapp/MobileJrqdController/getJrqdList.do',payload);
xhr('/xsfw/sys/swpubapp/internal-endpoint.do',payload);
const reused=new context.XMLHttpRequest();reused.open('GET','/old');const oldListener=reused.listeners.loadend;reused.open('GET','/new');assert.notEqual(reused.listeners.loadend,oldListener);reused.listeners.loadend();
events.error({error:{name:'ReferenceError',message:'jQuery is not defined'},filename:'https://school.edu/app.js?token=private',lineno:12,colno:4});
events.error({error:{name:'Error',message:"Bootstrap's JavaScript requires jQuery"},filename:'https://school.edu/bootstrap.js',lineno:6,colno:31});
events.unhandledrejection({reason:{name:'Error',message:'private response body'}});
context.__xatuDiagnosticSnapshot();
vm.runInContext(source,context);assert.equal(context.fetch,wrappedFetch,'reinjection must not wrap twice');
const parsed=logs.map(line=>JSON.parse(line.slice('XATU_DIAG:'.length)));
const summaries=parsed.filter(x=>x.type==='response-summary');
assert.equal(summaries.length,3,'only allowlisted responses are inspected');
assert.equal(summaries.filter(x=>x.data.kind==='json').length,2);
assert.ok(JSON.stringify(summaries).includes('"length":1'));
assert.ok(JSON.stringify(summaries).includes('"code":0'));
assert.ok(summaries.some(x=>x.data.kind==='html'&&x.data.loginForm));
assert.ok(parsed.some(x=>x.type==='template-summary'&&x.data.selectors[0].selector==='div.task-item'));
const snapshot=parsed.find(x=>x.type==='dom-snapshot').data;
assert.equal(snapshot.visual[0],390.51);assert.equal(snapshot.vh100,640);assert.equal(snapshot.nonzeroRects,2);assert.equal(snapshot.visible,undefined);
const cards=parsed.find(x=>x.type==='components'&&x.data.label==='cards').data;
assert.ok(cards.samples[0].outsideClipAt.includes(1),'zero-height ancestor clipping is detected');
assert.equal(cards.samples[0].centerHit,'other','a different hit target is recorded without assuming proof of occlusion');
assert.ok(parsed.some(x=>x.type==='dom-tree'));
assert.ok(parsed.some(x=>x.type==='js-error'&&x.data.detail.symbol==='jQuery'));
assert.ok(parsed.some(x=>x.type==='js-error'&&x.data.detail.reason==='bootstrap-requires-jquery'));
const result=logs.join('\n');
for(const secret of ['private','2026123456','studentName','studentNo','nested','example.com'])assert.ok(!result.includes(secret),secret);
console.log('Diagnostic v2 checks passed: response shape/allowlist/privacy, original fetch and XHR behavior, templates, clipping, viewport, dependency errors, reinjection.');
