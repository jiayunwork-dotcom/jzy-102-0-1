import { readFileSync } from 'fs';
let src = readFileSync('/workspace/prototype/tune.mjs','utf8');
src = src.slice(0, src.indexOf('const size=96, seeds'));
src += `
const size=96;
for(const s of [1,7,42]){const base=generate(size,s,6,0.5,2,0.05);const bed=smooth(base,size,2);
let lo=base,hi=base;const vL=[variance(base)],vH=[variance(base)];const dL=[0];
let prev=null;
for(let k=0;k<10;k++){const a=erode(lo,bed,size,P(0.02,0.05,1.2,0.1),s);const b=erode(hi,bed,size,P(0.3,0.05,1.2,0.1),s);
if(k===0)prev=sum(a)-sum(lo);else{/*identical delta check*/}
lo=a;hi=b;vL.push(variance(lo));vH.push(variance(hi));}
// verify identical per-round delta: round3 vs round2
const r2=erode(erode(base,bed,size,P(0.3,0.05,1.2,0.1),s),bed,size,P(0.3,0.05,1.2,0.1),s);
const r1=erode(base,bed,size,P(0.3,0.05,1.2,0.1),s);
let lin=true;const r3=erode(r2,bed,size,P(0.3,0.05,1.2,0.1),s);
for(let i=0;i<r3.length;i++){const d1=r1[i]-base[i],step1=r2[i]-r1[i],step2=r3[i]-r2[i];if(Math.abs(step1-d1)>1e-12||Math.abs(step2-d1)>1e-12){lin=false;break;}}
console.log('seed',s,'linearAdd=',lin,'lo',['+vL.map(v=>v.toFixed(3)).join(',')+']`,'hi',vH.map(v=>v.toFixed(3)).join(','),'order10=',vH.every((v,i)=>i===0||v>vL[i]));}
`;
const tmp='/workspace/prototype/_lib.mjs';
new (await import('fs')).default; 
import { writeFileSync as w } from 'fs'; w(tmp, src);
const mod = await import(tmp);
