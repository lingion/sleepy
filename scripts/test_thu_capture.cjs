const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwThuSemesterCapture.kt', 'utf8');
const script = source.match(/LIVE_HTML_JS = """([\s\S]*?)"""/)[1];
function input(value) {
  return {tagName:'INPUT', value, attrs:{value:'2025-2026-1'}, setAttribute(k,v){this.attrs[k]=v;}};
}
function select(selected) {
  return {tagName:'SELECT', options:['2025-2026-1','2026-2027-1'].map((value,i)=>({
    value, selected:i===selected, attrs:i===0?{selected:'selected'}:{},
    setAttribute(k,v){this.attrs[k]=v;}, removeAttribute(k){delete this.attrs[k];}
  }))};
}
function snapshot(live, saved) {
  const root={querySelectorAll(){return saved;},get outerHTML(){
    return saved.map(el=>el.tagName==='SELECT'?'<select name="p_xnxq">'+el.options.map(o=>'<option value="'+o.value+'"'+('selected' in o.attrs?' selected':'')+'>'+o.value+'</option>').join('')+'</select>':'<input name="p_xnxq" value="'+el.attrs.value+'">').join('');
  }};
  return vm.runInNewContext(script,{win:{document:{querySelectorAll(){return live;},documentElement:{cloneNode(deep){assert.equal(deep,true);return root;}}}}});
}
const hidden=input('2026-2027-1');
assert.equal(snapshot([hidden],[input('old')]),'<input name="p_xnxq" value="2026-2027-1">');
assert.equal(hidden.attrs.value,'2025-2026-1');
const current=select(1);
assert.equal(snapshot([current],[select(0)]),'<select name="p_xnxq"><option value="2025-2026-1">2025-2026-1</option><option value="2026-2027-1" selected>2026-2027-1</option></select>');
assert.deepEqual(current.options.map(o=>o.attrs),[{selected:'selected'},{}]);
assert.equal(snapshot([input('')],[input('old')]),'<input name="p_xnxq" value="">');
assert.equal(snapshot([],[]),'');
assert.throws(()=>snapshot([input('2026-2027-1')],[]),/snapshot mismatch/);
const both=snapshot([input('2026-2027-1'),select(1)],[input('old'),select(0)]);
assert.equal((both.match(/2026-2027-1/g)||[]).length,3);
console.log('THU current-semester capture: 8 assertions passed');
