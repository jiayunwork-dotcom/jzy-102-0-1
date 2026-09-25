// Prototype v3: safe clamped deposition (exact conservation) + parameter sweep
// to find a regime where height variance strictly grows with erosion rate.

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
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
    const u = fade(xf), v = fade(yf);
    return lerp(lerp(hh(x0, y0), hh(x0 + 1, y0), u), lerp(hh(x0, y0 + 1), hh(x0 + 1, y0 + 1), u), v);
  };
}
function generate(size, seed, octaves, persistence, lacunarity, baseFreq) {
  const out = new Float64Array(size * size);
  for (let o = 0; o < octaves; o++) {
    const noise = makeNoise((seed + 1) * 7919 + o * 104729);
    const amp = Math.pow(persistence, o);
    const f = baseFreq * Math.pow(lacunarity, o);
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++)
      out[y * size + x] += noise(x * f, y * f) * amp;
  }
  return out;
}

function erode(src, size, p, seed) {
  const h = Float64Array.from(src);
  const rng = mulberry32(seed);
  const at = (x, y) => h[y * size + x];
  // clamp to interior fractional range so bilinear nodes always exist
  const clampPos = (q) => Math.min(size - 1 - 1e-9, Math.max(0, q));
  function add(px, py, amount) {
    px = clampPos(px); py = clampPos(py);
    const x0 = Math.floor(px), y0 = Math.floor(py), fx = px - x0, fy = py - y0;
    h[y0 * size + x0]         += amount * (1 - fx) * (1 - fy);
    h[y0 * size + x0 + 1]     += amount * fx * (1 - fy);
    h[(y0 + 1) * size + x0]   += amount * (1 - fx) * fy;
    h[(y0 + 1) * size + x0 + 1] += amount * fx * fy;
  }
  function bilin(px, py) {
    const x0 = Math.floor(px), y0 = Math.floor(py), fx = px - x0, fy = py - y0;
    return at(x0, y0) * (1 - fx) * (1 - fy) + at(x0 + 1, y0) * fx * (1 - fy)
         + at(x0, y0 + 1) * (1 - fx) * fy + at(x0 + 1, y0 + 1) * fx * fy;
  }
  for (let d = 0; d < p.drops; d++) {
    let px = rng() * (size - 2) + 1, py = rng() * (size - 2) + 1;
    let dx = 0, dy = 0, speed = p.startSpeed, water = p.startWater, sediment = 0;
    for (let step = 0; step < p.maxSteps; step++) {
      const x0 = Math.floor(px), y0 = Math.floor(py);
      if (x0 < 1 || x0 >= size - 1 || y0 < 1 || y0 >= size - 1) { add(px, py, sediment); sediment = 0; break; }
      const gx = at(x0 + 1, y0) - at(x0, y0), gy = at(x0, y0 + 1) - at(x0, y0);
      const oldH = bilin(px, py);
      dx = dx * p.inertia - gx * (1 - p.inertia);
      dy = dy * p.inertia - gy * (1 - p.inertia);
      const dl = Math.hypot(dx, dy);
      if (dl !== 0) { dx /= dl; dy /= dl; }
      const nx = px + dx, ny = py + dy;
      if (nx < 1 || nx >= size - 1 || ny < 1 || ny >= size - 1) { add(px, py, sediment); sediment = 0; break; }
      const newH = bilin(nx, ny);
      const deltaH = newH - oldH;
      speed = Math.sqrt(Math.max(0, speed * speed + p.gravity * -deltaH));
      water *= (1 - p.evap);
      if (water <= p.minWater) { add(px, py, sediment); sediment = 0; break; }
      const cap = Math.max(0, -deltaH) * speed * water * p.capK;
      if (sediment > cap || deltaH > 0) {
        let amount;
        if (deltaH > 0) amount = Math.min(sediment, Math.max(deltaH, (sediment - cap) * p.deposit));
        else amount = (sediment - cap) * p.deposit;
        add(px, py, amount); sediment -= amount;
      } else {
        const amount = (cap - sediment) * p.erosion;
        add(px, py, -amount); sediment += amount;
      }
      px = nx; py = ny;
    }
  }
  return h;
}
const variance = (a) => { const m = a.reduce((s, v) => s + v, 0) / a.length;
  return a.reduce((s, v) => s + (v - m) ** 2, 0) / a.length; };
const sum = (a) => a.reduce((s, v) => s + v, 0);

const size = Number(process.argv[2] || 96);
const DROPS = Number(process.argv[3] || 6000);
const SEEDS = process.argv[4] ? process.argv[4].split(',').map(Number) : [1, 2, 3, 7, 42];
const rates = [0.02, 0.1, 0.3, 0.6];
const baseOf = {};
for (const seed of SEEDS) baseOf[seed] = generate(size, seed, 6, 0.5, 2, 0.05);

const sweep = [];
let combo = 0;
for (const capK of [1, 4, 10, 25])
  for (const deposit of [0.1, 0.3, 0.5])
    for (const evap of [0.01, 0.02, 0.05])
      for (const gravity of [4, 10]) {
        combo++;
        let mono = true, maxDs = 0;
        for (const seed of SEEDS) {
          const base = baseOf[seed];
          const vars = [];
          for (const r of rates) {
            const res = erode(base, size, { drops: DROPS, inertia: 0.05, capK, deposit, erosion: r, evap, gravity, startSpeed: 1, startWater: 1, maxSteps: 64, minWater: 0.01 }, seed);
            vars.push(variance(res));
            maxDs = Math.max(maxDs, Math.abs(sum(res) - sum(base)));
          }
          for (let i = 1; i < vars.length; i++) if (!(vars[i] > vars[i - 1])) mono = false;
        }
        if (mono) sweep.push({ capK, deposit, evap, gravity, maxDs });
        if (combo % 12 === 0) process.stderr.write(combo + '/72\n');
      }
console.log('monotone regimes:');
for (const s of sweep) console.log(JSON.stringify(s));
console.log(sweep.length ? 'FOUND' : 'NONE');
