const BASE='http://localhost:8080/api';
const post=(p,b)=>fetch(BASE+p,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)}).then(r=>r.json());
const noise={size:64,seed:7,octaves:6,persistence:0.5,lacunarity:2,baseFrequency:0.05};
const base=await post('/terrain/generate',noise);
async function rounds(rate){let cur=base;const vs=[base.variance];const sums=[base.sum];
  for(let k=0;k<6;k++){cur=await post('/erosion',{size:64,heights:cur.heights,noise,raindrops:6000,erosionRate:rate});
    vs.push(cur.variance);sums.push(cur.sum);}
  return {vs,sums};}
const lo=await rounds(0.02), hi=await rounds(0.3);
console.log('low  var',lo.vs.map(v=>v.toFixed(4)).join(', '));
console.log('high var',hi.vs.map(v=>v.toFixed(4)).join(', '));
const ordered=hi.vs.every((v,i)=>i===0||v>lo.vs[i]);
const bounded=hi.vs.every(v=>Number.isFinite(v)&&v<1);
const massErr=Math.max(...hi.sums.map(s=>Math.abs(s-base.sum)),...lo.sums.map(s=>Math.abs(s-base.sum)));
console.log('ordered every round:',ordered,'| bounded:',bounded,'| max|dSum|:',massErr.toExponential(2));
process.exitCode=(ordered&&bounded&&massErr<1e-6)?0:1;
