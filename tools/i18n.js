#!/usr/bin/env node
/* Собирает английскую версию игры: web/index.html + i18n/en.json → web/en.html.
   Переводятся все строки с кириллицей: JS-литералы, части шаблонов `...`, текст и атрибуты HTML.
   Нет перевода — строка остаётся русской (список: node tools/i18n.js --missing).
   Нужны acorn и acorn-walk (npm i --no-save acorn acorn-walk). */
const fs=require('fs'),path=require('path');
const req=m=>{try{return require(m)}catch(e){return require(path.join(process.cwd(),'node_modules',m))}};
const acorn=req('acorn'),walk=req('acorn-walk');
const args=process.argv.slice(2),flag=k=>args.includes(k),opt=(k,d)=>{const i=args.indexOf(k);return i>=0?args[i+1]:d};
const IN=opt('--in','web/index.html'),OUT=opt('--out','web/en.html'),DICT=opt('--dict','i18n/en.json');
const src=fs.readFileSync(IN,'utf8'),CY=/[А-Яа-яЁё]/;
function segments(src){
  const OPEN='<script type="module">',a=src.indexOf(OPEN)+OPEN.length,b=src.lastIndexOf('</script>'),js=src.slice(a,b),segs=[];
  walk.full(acorn.parse(js,{ecmaVersion:'latest',sourceType:'module'}),n=>{
    if(n.type==='Literal'&&typeof n.value==='string'&&CY.test(n.value))segs.push({s:a+n.start+1,e:a+n.end-1,kind:'js',q:js[n.start],raw:js.slice(n.start+1,n.end-1)});
    if(n.type==='TemplateElement'&&CY.test(n.value.raw))segs.push({s:a+n.start,e:a+n.end,kind:'tpl',raw:n.value.raw})});
  for(const [x,y] of [[0,a],[b,src.length]]){const part=src.slice(x,y);let m;
    const re=/>([^<>]*[А-Яа-яЁё][^<>]*)</g;while(m=re.exec(part))segs.push({s:x+m.index+1,e:x+m.index+1+m[1].length,kind:'html',raw:m[1]});
    const re2=/(title|placeholder|aria-label|content)="([^"]*[А-Яа-яЁё][^"]*)"/g;while(m=re2.exec(part)){const o=m.index+m[0].indexOf('"')+1;segs.push({s:x+o,e:x+o+m[2].length,kind:'attr',raw:m[2]})}}
  return segs.sort((p,q)=>p.s-q.s)}
const segs=segments(src),dict=fs.existsSync(DICT)?JSON.parse(fs.readFileSync(DICT,'utf8')):{};
const miss=[...new Set(segs.filter(g=>!(g.raw in dict)).map(g=>g.raw))];
if(flag('--missing')){miss.forEach(m=>console.log(m));process.exit(0)}
if(flag('--segs')){fs.writeFileSync(opt('--segs'),JSON.stringify(segs));}
function esc(t,g){
  if(g.kind==='js'){let o='';for(let i=0;i<t.length;i++){const c=t[i];if(c==='\\'){o+=c+(t[i+1]||'');i++;continue}o+=(c===g.q||(c==='\n'))?(c==='\n'?'\\n':'\\'+c):c}return o}
  if(g.kind==='tpl'){let o='';for(let i=0;i<t.length;i++){const c=t[i];if(c==='\\'){o+=c+(t[i+1]||'');i++;continue}if(c==='`'){o+='\\`';continue}if(c==='$'&&t[i+1]==='{'){o+='\\$';continue}o+=c}return o}
  if(g.kind==='attr')return t.replace(/"/g,'&quot;');
  return t}
let out=src;for(let i=segs.length-1;i>=0;i--){const g=segs[i];if(!(g.raw in dict))continue;out=out.slice(0,g.s)+esc(dict[g.raw],g)+out.slice(g.e)}
out=out.replace('<html lang="ru">','<html lang="en">');
// «ёлочки» остались в кусках без кириллицы (например, `«${T.t}»`): меняем на английские кавычки
out=out.replace(/«/g,'“').replace(/»/g,'”');
// проверка, что JS после замены разбирается
const OPEN='<script type="module">',a=out.indexOf(OPEN)+OPEN.length,b=out.lastIndexOf('</script>');
acorn.parse(out.slice(a,b),{ecmaVersion:'latest',sourceType:'module'});
fs.writeFileSync(OUT,out);
console.log('en.html: фраз',new Set(segs.map(g=>g.raw)).size,'· без перевода',miss.length);
