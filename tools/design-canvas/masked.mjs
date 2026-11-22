/** Same diff as verify.mjs, but blind to the clock — the one element that is
 *  legitimately different between two captures taken minutes apart. */
import { readFile, readdir } from 'node:fs/promises';
import { chromium } from '/home/nclsgnkl/dev/guest-expierience-platform/frontend/node_modules/playwright/index.mjs';
const nameToShot = n => n.replace(/(Light|Dark)$/, m => `-${m.toLowerCase()}`)
  .replace(/^Home/,'home').replace(/^Settings/,'settings').replace(/^InfoModule/,'info')
  .replace(/^Placeholder/,'placeholder').replace(/^MenuOverflow/,'menu-overflow')
  .replace(/^Standby/,'standby').replace(/^Connection/,'connection').replace(/^Pairing/,'pairing');
const browser = await chromium.launch();
const page = await browser.newPage();
const files = (await readdir('./verify')).filter(f=>f.endsWith('.png')&&!f.startsWith('diff-')).sort();
for (const f of files) {
  const name=f.replace('.png','');
  const a=(await readFile(`./verify/${f}`)).toString('base64');
  const b=(await readFile(`./shots/${nameToShot(name)}.png`)).toString('base64');
  const r=await page.evaluate(async ([a,b])=>{
    const load=s=>new Promise(r=>{const i=new Image();i.onload=()=>r(i);i.src='data:image/png;base64,'+s;});
    const [ia,ib]=await Promise.all([load(a),load(b)]);
    const W=ia.width,H=ia.height;
    const px=img=>{const c=new OffscreenCanvas(W,H);const x=c.getContext('2d');x.drawImage(img,0,0);return x.getImageData(0,0,W,H).data;};
    const pa=px(ia),pb=px(ib);
    let bad=0,total=0;
    for(let i=0;i<pa.length;i+=4){
      const p=i/4,x=p%W,y=Math.floor(p/W);
      if (y<130 && x>2380) continue;           // the clock
      total++;
      if(Math.abs(pa[i]-pb[i])+Math.abs(pa[i+1]-pb[i+1])+Math.abs(pa[i+2]-pb[i+2])>24) bad++;
    }
    return +(100*bad/total).toFixed(3);
  },[a,b]);
  console.log(`${name.padEnd(20)} ${String(r).padStart(7)}%`);
}
await browser.close();
