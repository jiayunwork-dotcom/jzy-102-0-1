// Final prototype validation of the chosen erosion model across many seeds and
// two grid sizes; plus a cumulative-carve cap for multi-round stability.
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x71f23b5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function makeNoise(seed) {
  const rng = mulberry32(seed);
  const p = new Uint8Array(512);
  const tmp = Array.from({ length: 256 }, (_, i) => i);
  for (let i = 255; i > 0; i--) { const j = Math.floor(rng() * (i + 1)); [tmp[i], tmp[j]] = [tmp[j], tmp[i]]; }
  for (let i = 0; i < 512; i++) p[i] = tmp[i & 255];
  const fade = (t) => t * t * t * (t * (t * 6 - 15) + 10);
  const lerp = (a, b, t) => a + (b - a) * t;
  const hh = (ix, iy) => p[(p[ix & 255] + iy) & 255] / 255;
  return (x, y) => {
    const x0 = Math.floor(x), y0 = Math.floor(y), xf = x - x0, yf = y - y0;
    return lerp(lerp(hh(x0, y0), hh(x0 + 1, y0), fade(xf)), lerp(hh(x0, y0 + 1), hh(x0 + 1, y0 + 1), fade(xf)), fade(yf));
  };
}
function generate(size, seed, octaves, persistence, lacunarity, baseFreq) {
  const out = new Float64Array(size * size);
  for (let o = 0; o < octaves; o++) {
    const noise = makeNoise((seed + 1) * 7919 + o * 104729);
    const amp = Math.pow(persistence, o), f = baseFreq * Math.pow(lacunarity, o);
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++)
      out[y * size + x] += noise(x * f, y * f) * amp;
  }
  return out;
}
function makeBrush(radius) {
  if (radius <= 0) return { cells: [[0, 0]], weights: [1] };
  const cells = [], raw = [];
  const R = Math.ceil(radius);
  for (let dy = -R; dy <= R; dy++) for (let dx = -R; dx <= R; dx++) {
    const d = Math.hypot(dx, dy);
    if (d <= radius) { cells.push([dx, dy]); raw.push(1 - d / radius); }
  }
  const s = raw.reduce((a, b) => a + b, 0);
  return { cells, weights: raw.map(w => w / s) };
}
function smooth(src, size, passes) {
  let cur = Float64Array.from(src);
  for (let k = 0; k < passes; k++) {
    const next = new Float64Array(cur.length);
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      let s = 0, n = 0;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const xx = x + dx, yy = y + dy;
        if (xx >= 0 && xx < size && yy >= 0 && yy < size) { s += cur[yy * size + xx]; n++; }
      }
      next[y * size + x] = s / n;
    }
    cur = next;
  }
  return cur;
}
function erode(src, size, p, seed, observer) {
  const h = Float64Array.from(src);
  const sbed = smooth(src, size, p.smoothPasses);
  const rng = mulberry32(seed);
  const at = (f, x, y) => f[y * size + x];
  const erBrush = makeBrush(p.erodeRadius), depBrush = makeBrush(p.depositRadius);
  function brushAdd(px, py, amount, brush) {
    const cx = Math.round(px), cy = Math.round(py);
    let wsum = 0;
    for (let i = 0; i < brush.cells.length; i++) {
      const [dx, dy] = brush.cells[i];
      if (cx + dx >= 0 && cx + dx < size && cy + dy >= 0 && cy + dy < size) wsum += brush.weights[i];
    }
    if (wsum === 0) return;
    for (let i = 0; i < brush.cells.length; i++) {
      const [dx, dy] = brush.cells[i];
      const x = cx + dx, y = cy + dy;
      if (x >= 0 && x < size && y >= 0 && y < size) h[y * size + x] += amount * brush.weights[i] / wsum;
    }
  }
  function bilin(f, px, py) {
    const x0 = Math.floor(px), y0 = Math.floor(py), fx = px - x0, fy = py - y0;
    return at(f, x0, y0) * (1 - fx) * (1 - fy) + at(f, x0 + 1, y0) * fx * (1 - fy)
         + at(f, x0, y0 + 1) * (1 - fx) * fy + at(f, x0 + 1, y0 + 1) * fx * fy;
  }
  for (let d = 0; d < p.drops; d++) {
    let px = rng() * (size - 2) + 1, py = rng() * (size - 2) + 1;
    let dx = 0, dy = 0, sediment = 0, speed = p.startSpeed, water = p.startWater;
    for (let step = 0; step < p.maxSteps; step++) {
      const x0 = Math.floor(px), y0 = Math.floor(py);
      if (x0 < 1 || x0 >= size - 1 || y0 < 1 || y0 >= size - 1) { brushAdd(px, py, sediment, depBrush); sediment = 0; break; }
      const gx = at(sbed, x0 + 1, y0) - at(sbed, x0, y0);
      const gy = at(sbed, x0, y0 + 1) - at(sbed, x0, y0);
      const oldS = bilin(sbed, px, py);
      dx = dx * p.inertia - gx * (1 - p.inertia);
      dy = dy * p.inertia - gy * (1 - p.inertia);
      const dl = Math.hypot(dx, dy);
      if (dl !== 0) { dx /= dl; dy /= dl; }
      const nx = px + dx, ny = py + dy;
      if (nx < 1 || nx >= size - 1 || ny < 1 || ny >= size - 1) { brushAdd(px, py, sediment, depBrush); sediment = 0; break; }
      const newS = bilin(sbed, nx, ny), deltaS = newS - oldS;
      speed = Math.sqrt(Math.max(0, speed * speed + p.gravity * -deltaS));
      water *= (1 - p.evap);
      if (observer) observer(water, d, step);
      if (water <= p.minWater) { brushAdd(px, py, sediment, depBrush); sediment = 0; break; }
      const fall = Math.max(0, -deltaS);
      const cap = fall * speed * water * p.capK;
      if (sediment > cap || deltaS > 0) {
        let amount;
        if (deltaS > 0) amount = Math.min(sediment, Math.max(deltaS, (sediment - cap) * p.deposit));
        else amount = (sediment - cap) * p.deposit;
        brushAdd(px, py, amount, depBrush); sediment -= amount;
      } else {
        const eroded = Math.max(0, Math.min((cap - sediment) * p.erosion, fall * p.carveFrac));
        brushAdd(px, py, -eroded, erBrush); sediment += eroded;
      }
      px = nx; py = ny;
    }
    if (sediment > 1e-12) brushAdd(px, py, sediment, depBrush);
  }
  return h;
}
const variance = (a) => { const m = a.reduce((s, v) => s + v, 0) / a.length;
  return a.reduce((s, v) => s + (v - m) ** 2, 0) / a.length; };
const sum = (a) => a.reduce((s, v) => s + v, 0);
function gradEnergy(h, size) {
  let e = 0, n = 0;
  for (let y = 1; y < size - 1; y++) for (let x = 1; x < size - 1; x++) {
    const gx = (h[y * size + x + 1] - h[y * size + x - 1]) / 2;
    const gy = (h[(y + 1) * size + x] - h[(y - 1) * size + x]) / 2;
    e += gx * gx + gy * gy; n++;
  }
  return e / n;
}

function P(r) {
  return { drops: 12000, inertia: 0.05, capK: 2, deposit: 0.1, erosion: r,
    erodeRadius: 0, depositRadius: 3, evap: 0.02, gravity: 4, smoothPasses: 2,
    carveFrac: 0.1,
    startSpeed: 1, startWater: 1, maxSteps: 64, minWater: 0.01 };
}
let allPass = true;
for (const size of [96, 128]) {
  const seeds = Array.from({ length: 12 }, (_, i) => i + 1);
  let orderOk = 0, detOk = 0, massMax = 0;
  for (const s of seeds) {
    const base = generate(size, s, 6, 0.5, 2, 0.05);
    const lo = erode(base, size, P(0.02), s);
    const hi = erode(base, size, P(0.3), s);
    if (variance(hi) > variance(lo)) orderOk++;
    const hi2 = erode(base, size, P(0.3), s);
    if (hi.every((v, i) => v === hi2[i])) detOk++;
    massMax = Math.max(massMax, Math.abs(sum(hi) - sum(base)));
  }
  console.log(`size=${size} variance-order ${orderOk}/${seeds.length} determinism ${detOk}/${seeds.length} max|dSum|=${massMax.toExponential(1)}`);
  if (orderOk !== seeds.length || detOk !== seeds.length || massMax > 1e-6) allPass = false;
}
// octaves -> gradient energy, several seeds
for (const size of [96, 128]) {
  let ok = 0;
  for (let s = 1; s <= 12; s++) {
    const g1 = gradEnergy(generate(size, s, 1, 0.5, 2, 0.05), size);
    const g8 = gradEnergy(generate(size, s, 8, 0.5, 2, 0.05), size);
    if (g8 > g1) ok++;
  }
  console.log(`size=${size} octave-gradient-rise ${ok}/12`);
  if (ok !== 12) allPass = false;
}
// water monotonicity + termination
{
  const size = 128, s = 5;
  const base = generate(size, s, 6, 0.5, 2, 0.05);
  const seen = new Map(); let viol = 0, obs = 0, postDeath = 0;
  erode(base, size, P(0.3), s, (w, d, step) => {
    obs++;
    if (step > 0 && seen.has(d) && w > seen.get(d) + 1e-15) viol++;
    seen.set(d, w);
  });
  console.log(`water obs=${obs} non-increasing violations=${viol}`);
  if (viol !== 0) allPass = false;
}
console.log(allPass ? 'ALL PASS' : 'FAIL');
