const BASE = 'http://localhost:8080/api';

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}
async function get(path) {
  const res = await fetch(BASE + path);
  return { status: res.status, data: await res.json() };
}

const noise = { size: 64, seed: 7, octaves: 6, persistence: 0.5, lacunarity: 2.0, baseFrequency: 0.05 };
let pass = true;
const check = (cond, msg) => { console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`); if (!cond) pass = false; };

// 1. generate
const gen = await post('/terrain/generate', noise);
check(gen.status === 200, `generate status 200 (got ${gen.status})`);
const base = gen.data;
check(base.heights.length === 64 * 64, 'heights length = size*size');

// 2. erosion at low vs high rate, same drops/seed -> variance order
const erodeBody = (rate) => ({
  size: 64, heights: base.heights, noise,
  raindrops: 6000, erosionRate: rate
});
const [lo, hi] = await Promise.all([
  post('/erosion', erodeBody(0.02)),
  post('/erosion', erodeBody(0.30))
]);
check(lo.status === 200 && hi.status === 200, 'erosion runs return 200');
console.log(`   variance base=${base.variance.toFixed(5)} low=${lo.data.variance.toFixed(5)} high=${hi.data.variance.toFixed(5)}`);
check(hi.data.variance > lo.data.variance, 'variance(high rate) > variance(low rate)');

// 3. mass conservation from returned sums
const dSum = Math.abs(hi.data.sum - base.sum);
console.log(`   |sum before - sum after| = ${dSum.toExponential(2)}`);
check(dSum < 1e-6 * Math.max(1, Math.abs(base.sum)), 'total height sum conserved');

// 4. reproducibility: same request twice -> identical heights
const hi2 = await post('/erosion', erodeBody(0.30));
let identical = true;
for (let i = 0; i < hi.data.heights.length; i++) if (hi.data.heights[i] !== hi2.data.heights[i]) { identical = false; break; }
check(identical, 'identical request reproduces bit-identical field');

// 5. invalid params rejected before computation
const badSize = await post('/terrain/generate', { ...noise, size: 8 });
check(badSize.status === 400 && badSize.data.error === 'invalid_parameters', `bad resolution -> 400 (got ${badSize.status})`);
const badRate = await post('/erosion', { size: 64, heights: base.heights, noise, erosionRate: -0.5 });
check(badRate.status === 400, `negative erosionRate -> 400 (got ${badRate.status})`);
const badPersistence = await post('/terrain/generate', { ...noise, persistence: 1.5 });
check(badPersistence.status === 400, `bad persistence -> 400 (got ${badPersistence.status})`);
const badHeights = await post('/erosion', { size: 64, heights: [1, 2, 3], noise });
check(badHeights.status === 400, `heights length mismatch -> 400 (got ${badHeights.status})`);

// 6. snapshots save/list/load
const name = 'e2e-ridge';
const saved = await post('/snapshots', {
  name, size: 64, heights: hi.data.heights, noise, erosion: { raindrops: 6000, erosionRate: 0.3 }
});
check(saved.status === 200 && saved.data.saved === true, 'snapshot saved');
const dup = await post('/snapshots', { name, size: 64, heights: hi.data.heights, noise });
check(dup.status === 409, `duplicate name -> 409 (got ${dup.status})`);
const list = await get('/snapshots');
check(list.status === 200 && list.data.some(s => s.name === name), 'snapshot appears in list');
const loaded = await get('/snapshots/' + name);
check(loaded.status === 200 && loaded.data.heights.length === 4096, 'snapshot loads with heights');
let same = true;
for (let i = 0; i < 4096; i++) if (loaded.data.heights[i] !== hi.data.heights[i]) { same = false; break; }
check(same, 'loaded heights equal saved heights');
const missing = await get('/snapshots/nope');
check(missing.status === 404, 'missing snapshot -> 404');

console.log(pass ? '\nALL E2E PASS' : '\nE2E FAIL');
process.exitCode = pass ? 0 : 1;
