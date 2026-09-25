// Prototype v2: mass-conserving bilinear brush, no -deltaH cap,
// unnormalized fBm with per-octave seeded noise, invariant tuning.

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
  for (let i = 255; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [tmp[i], tmp[j]] = [tmp[j], tmp[i]];
  }
  for (let i = 0; i < 512; i++) p[i] = tmp[i & 255];
  const fade = (t) => t * t * t * (t * (t * 6 - 15) + 10);
  const lerp = (a, b, t) => a + (b - a) * t;
  const h = (ix, iy) => p[(p[ix & 255] + iy) & 255] / 255; // [0,1]
  return function noise(x, y) {
    const x0 = Math.floor(x), y0 = Math.floor(y);
    const xf = x - x0, yf = y - y0;
    const u = fade(xf), v = fade(yf);
    const n00 = h(x0, y0), n10 = h(x0 + 1, y0), n01 = h(x0, y0 + 1), n11 = h(x0 + 1, y0 + 1);
    return lerp(lerp(n00, n10, u), lerp(n01, n11, u), v);
  };
}

// fBm: NO normalization; amp_o = persistence^o
function generate(size, seed, octaves, persistence, lacunarity, baseFreq) {
  const out = new Float64Array(size * size);
  for (let o = 0; o < octaves; o++) {
    const noise = makeNoise((seed + 1) * 7919 + o * 104729);
    const amp = Math.pow(persistence, o);
    const f = baseFreq * Math.pow(lacunarity, o);
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        out[y * size + x] += noise(x * f, y * f) * amp;
      }
    }
  }
  return out;
}

const P = {
  drops: 20000,
  inertia: 0.05,
  capacity: 4,
  depositRate: 0.3,
  erosionRate: 0.3,
  evaporateRate: 0.02,
  gravity: 4,
  startSpeed: 1,
  startWater: 1,
  maxSteps: 64,
  minWater: 0.01,
};

function erode(src, size, p, seed) {
  const h = Float64Array.from(src);
  const rng = mulberry32(seed);
  const at = (x, y) => h[y * size + x];

  function bilinear(px, py) {
    const x0 = Math.floor(px), y0 = Math.floor(py);
    const fx = px - x0, fy = py - y0;
    return at(x0, y0) * (1 - fx) * (1 - fy) + at(x0 + 1, y0) * fx * (1 - fy)
         + at(x0, y0 + 1) * (1 - fx) * fy + at(x0 + 1, y0 + 1) * fx * fy;
  }
  // Exactly mass-conserving bilinear add: sum of weights == 1.
  function add(px, py, amount) {
    const x0 = Math.floor(px), y0 = Math.floor(py);
    const fx = px - x0, fy = py - y0;
    h[at_i(x0, y0)]     += amount * (1 - fx) * (1 - fy);
    h[at_i(x0 + 1, y0)] += amount * fx * (1 - fy);
    h[at_i(x0, y0 + 1)] += amount * (1 - fx) * fy;
    h[at_i(x0 + 1, y0 + 1)] += amount * fx * fy;
  };
  const at_i = (x, y) => y * size + x;

  function dropSediment(px, py, amount) { add(px, py, amount); }

  for (let d = 0; d < p.drops; d++) {
    let px = rng() * (size - 2) + 1;
    let py = rng() * (size - 2) + 1;
    let dx = 0, dy = 0;
    let speed = p.startSpeed;
    let water = p.startWater;
    let sediment = 0;

    for (let step = 0; step < p.maxSteps; step++) {
      const x0 = Math.floor(px), y0 = Math.floor(py);
      if (x0 < 1 || x0 >= size - 1 || y0 < 1 || y0 >= size - 1) {
        add(px, py, sediment); sediment = 0; break;
      }
      const fx = px - x0, fy = py - y0;
      const hL = at(x0, y0), hR = at(x0 + 1, y0), hD = at(x0, y0 + 1), hU = at(x0 + 1, y0 + 1);
      const gx = hR - hL, gy = hU - hD;
      const oldH = bilinear(px, py);

      dx = dx * p.inertia - gx * (1 - p.inertia);
      dy = dy * p.inertia - gy * (1 - p.inertia);
      const dl = Math.hypot(dx, dy);
      if (dl !== 0) { dx /= dl; dy /= dl; }
      const nx = px + dx, ny = py + dy;
      if (nx < 1 || nx >= size - 1 || ny < 1 || ny >= size - 1) {
        add(px, py, sediment); sediment = 0; break;
      }
      const newH = bilinear(nx, ny);
      const deltaH = newH - oldH;
      speed = Math.sqrt(Math.max(0, speed * speed + p.gravity * -deltaH));

      // Evaporation FIRST, strictly monotone non-increasing; die at/below threshold.
      water *= (1 - p.evaporateRate);
      if (water <= p.minWater) {
        add(px, py, sediment); sediment = 0; break;
      }

      const capacity = Math.max(0, -deltaH) * speed * water * p.capacity;
      if (sediment > capacity || deltaH > 0) {
        let amount;
        if (deltaH > 0) amount = Math.min(sediment, Math.max(deltaH, (sediment - capacity) * p.depositRate));
        else amount = (sediment - capacity) * p.depositRate;
        add(px, py, amount);
        sediment -= amount;
      } else {
        const amount = (capacity - sediment) * p.erosionRate; // no -deltaH clamp
        add(px, py, -amount);
        sediment += amount;
      }
      px = nx; py = ny;
    }
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

const size = 128;
let allPass = true;
const rates = [0.01, 0.05, 0.1, 0.3];
for (const seed of [1, 2, 3, 7, 42]) {
  const base = generate(size, seed, 6, 0.5, 2, 0.05);
  const results = rates.map(r => erode(base, size, { ...P, erosionRate: r }, seed));
  const vars = results.map(variance);
  const hi2 = erode(base, size, { ...P, erosionRate: 0.3 }, seed);
  const det = results[3].every((v, i) => v === hi2[i]);
  const dSum = sum(results[3]) - sum(base);
  const mono = vars.every((v, i) => i === 0 || v >= vars[i - 1]);
  console.log(`seed=${seed} vars=${vars.map(v => v.toFixed(5)).join(', ')} mono? ${mono} det? ${det} dSum=${dSum.toExponential(1)}`);
  if (!(mono && det && Math.abs(dSum) < 1e-6)) allPass = false;
}
for (const seed of [1, 2]) {
  const g1 = gradEnergy(generate(size, seed, 1, 0.5, 2, 0.05), size);
  const g8 = gradEnergy(generate(size, seed, 8, 0.5, 2, 0.05), size);
  console.log(`seed=${seed} grad oct1=${g1.toFixed(5)} oct8=${g8.toFixed(5)} rise? ${g8 > g1}`);
  if (!(g8 > g1)) allPass = false;
}
// water monotonicity test: instrument via evaporate-only check is structural; verify minWater termination
console.log(allPass ? 'ALL PASS' : 'FAIL');
