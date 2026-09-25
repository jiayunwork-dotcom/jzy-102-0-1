// Tune fixed-bed model so repeated clicks stay visually reasonable.
function mulberry32(seed){let a=seed>>>0;return function(){a|=0;a=(a+0x71f23b5)|0;let t=Math.imul(a^(a>>>15),1|a);t=(t+Math.imul(t^(t>>>7),61|t))^t;return((t^(t>>>14))>>>0)/4294967296;};}
function makeNoise(seed){const rng=mulberry32(seed);const p=new Uint8Array(512);const tmp=Array.from({length:256},(_,i)=>i);for(let i=255;i>0;i--){const j=Math.floor(rng()*(i+1));[tmp[i],tmp[j]]=[tmp[j],tmp[i]];}for(let i=0;i<512;i++)p[i]=tmp[i&255];const fade=t=>t*t*t*(t*(t*6-15)+10);const lerp=(a,b,t)=>a+(b-a)*t;const hh=(ix,iy)=>p[(p[ix&255]+iy)&255]/255;return(x,y)=>{const x0=Math.floor(x),y0=Math.floor(y),xf=x-x0,yf=y-y0;return lerp(lerp(hh(x0,y0),hh(x0+1,y0),fade(xf)),lerp(hh(x0,y0+1),hh(x0+1,y0+1),fade(xf)),fade(yf));};}
function generate(size,seed,octaves,persistence,lacunarity,baseFreq){const out=new Float64Array(size*size);for(let o=0;o<octaves;o++){const noise=makeNoise((seed+1)*7919+o*104729);const amp=Math.pow(persistence,o),f=baseFreq*Math.pow(lacunarity,o);for(let y=0;y<size;y++)for(let x=0;x<size;x++)out[y*size+x]+=noise(x*f,y*f)*amp;}return out;}
function makeBrush(radius){if(radius<=0)return{cells:[[0,0]],weights:[1]};const cells=[],raw=[];const R=Math.ceil(radius);for(let dy=-R;dy<=R;dy++)for(let dx=-R;dx<=R;dx++){const d=Math.hypot(dx,dy);if(d<=radius){cells.push([dx,dy]);raw.push(1-d/radius);}}const s=raw.reduce((a,b)=>a+b,0);return{cells,weights:raw.map(w=>w/s)};}
function smooth(src,size,passes){let cur=Float64Array.from(src);for(let k=0;k<passes;k++){const next=new Float64Array(cur.length);for(let y=0;y<size;y++)for(let x=0;x<size;x++){let s=0,n=0;for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy;if(xx>=0&&xx<size&&yy>=0&&yy<size){s+=cur[yy*size+xx];n++;}}next[y*size+x]=s/n;}cur=next;}return cur;}
function erode(src,bed,size,p,seed){const h=Float64Array.from(src);const sbed=bed;const rng=mulberry32(seed);const at=(f,x,y)=>f[y*size+x];const erBrush=makeBrush(p.erodeRadius),depBrush=makeBrush(p.depositRadius);
function brushAdd(px,py,amount,brush){const cx=Math.round(px),cy=Math.round(py);let wsum=0;for(let i=0;i<brush.cells.length;i++){const[dx,dy]=brush.cells[i];if(cx+dx>=0&&cx+dx<size&&cy+dy>=0&&cy+dy<size)wsum+=brush.weights[i];}if(wsum===0)return;for(let i=0;i<brush.cells.length;i++){const[dx,dy]=brush.cells[i];const x=cx+dx,y=cy+dy;if(x>=0&&x<size&&y>=0&&y<size)h[y*size+x]+=amount*brush.weights[i]/wsum;}}
function bilin(f,px,py){const x0=Math.floor(px),y0=Math.floor(py),fx=px-x0,fy=py-y0;return at(f,x0,y0)*(1-fx)*(1-fy)+at(f,x0+1,y0)*fx*(1-fy)+at(f,x0,y0+1)*(1-fx)*fy+at(f,x0+1,y0+1)*fx*fy;}
for(let d=0;d<p.drops;d++){let px=rng()*(size-2)+1,py=rng()*(size-2)+1;let dx=0,dy=0,sediment=0,speed=p.startSpeed,water=p.startWater;
for(let step=0;step<p.maxSteps;step++){const x0=Math.floor(px),y0=Math.floor(py);if(x0<1||x0>=size-1||y0<1||y0>=size-1){brushAdd(px,py,sediment,depBrush);sediment=0;break;}const gx=at(sbed,x0+1,y0)-at(sbed,x0,y0),gy=at(sbed,x0,y0+1)-at(sbed,x0,y0);const oldS=bilin(sbed,px,py);dx=dx*p.inertia-gx*(1-p.inertia);dy=dy*p.inertia-gy*(1-p.inertia);const dl=Math.hypot(dx,dy);if(dl!==0){dx/=dl;dy/=dl;}const nx=px+dx,ny=py+dy;if(nx<1||nx>=size-1||ny<1||ny>=size-1){brushAdd(px,py,sediment,depBrush);sediment=0;break;}const newS=bilin(sbed,nx,ny),deltaS=newS-oldS;speed=Math.sqrt(Math.max(0,speed*speed+p.gravity*-deltaS));water*=(1-p.evap);if(water<=p.minWater){brushAdd(px,py,sediment,depBrush);sediment=0;break;}const fall=Math.max(0,-deltaS);const cap=fall*speed*water*p.capK;if(sediment>cap||deltaS>0){let amount;if(deltaS>0)amount=Math.min(sediment,Math.max(deltaS,(sediment-cap)*p.deposit));else amount=(sediment-cap)*p.deposit;brushAdd(px,py,amount,depBrush);sediment-=amount;}else{const eroded=Math.max(0,Math.min((cap-sediment)*p.erosion,fall*p.carveFrac));brushAdd(px,py,-eroded,erBrush);sediment+=eroded;}px=nx;py=ny;}
if(sediment>1e-12)brushAdd(px,py,sediment,depBrush);}
return h;}
const variance=a=>{const m=a.reduce((s,v)=>s+v,0)/a.length;return a.reduce((s,v)=>s+(v-m)**2,0)/a.length;};
const sum=a=>a.reduce((s,v)=>s+v,0);

const size=96, seeds=[1,2,3,7,42];
function P(r,cf,K,dep){return{drops:9000,inertia:0.05,capK:K,deposit:dep,erosion:r,erodeRadius:0,depositRadius:3,evap:0.02,gravity:4,carveFrac:cf,startSpeed:1,startWater:1,maxSteps:64,minWater:0.01};}
for (const cf of [0.03,0.05,0.08]) for (const K of [1.2,2]) {
  let order=0, nS=0, mass=0; const trajLo=[],trajHi=[];
  for (const s of seeds){const base=generate(size,s,6,0.5,2,0.05);const bed=smooth(base,size,2);
    let lo=base,hi=base;const vL=[variance(base)],vH=[variance(base)];
    for(let k=0;k<6;k++){lo=erode(lo,bed,size,P(0.02,cf,K,0.1),s+k*99991);hi=erode(hi,bed,size,P(0.3,cf,K,0.1),s+k*99991);vL.push(variance(lo));vH.push(variance(hi));if(variance(hi)>variance(lo))order++;nS++;mass=Math.max(mass,Math.abs(sum(hi)-sum(base)));}
    vL.forEach((v,k)=>trajLo[k]=(trajLo[k]||0)+v);vH.forEach((v,k)=>trajHi[k]=(trajHi[k]||0)+v);
  }
  const m=a=>a.map(v=>(v/seeds.length));
  console.log(`cf=${cf} K=${K} order=${order}/${nS} mass=${mass.toExponential(1)}`);
  console.log(`   lo [${m(trajLo).map(v=>v.toFixed(3)).join(', ')}]`);
  console.log(`   hi [${m(trajHi).map(v=>v.toFixed(3)).join(', ')}]`);
}
// single-round order at 128 with final params
let ok128=0;
for(const s of Array.from({length:12},(_,i)=>i+1)){const base=generate(128,s,6,0.5,2,0.05);const bed=smooth(base,128,2);
const lo=erode(base,bed,128,P(0.02,0.05,1.2,0.1),s);const hi=erode(base,bed,128,P(0.3,0.05,1.2,0.1),s);
if(variance(hi)>variance(lo))ok128++;}
console.log('128 single-round order',ok128+'/12');
