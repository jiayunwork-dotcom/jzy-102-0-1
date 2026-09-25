// Prototype v5: concentrated erosion brush + diffuse deposition brush.
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

// Build Theusner-style brush: flat disk minus pyramid falloff, normalized.
// radius 0 => single cell [1].
function makeBrush(radius) {
  if (radius <= 0) return { cells: [[0, 0]], weights: [1] };
  const cells = [], raw = [];
  const R = Math.ceil(radius);
  for (let dy = -R; dy <= R; dy++) for (let dx = -R; dx <= R; dx++) {
    const d = Math.hypot(dx, dy);
    if (d <= radius) {
      const w = 1 - d / radius; // cone, center-heavy
      cells.push([dx, dy]); raw.push(w);
    }
  }
  const s = raw.reduce((a, b) => a + b, 0);
  return { cells, weights: raw.map(w => w / s) };
}

function erode(src, size, p, seed) {
  const h = Float64Array.from(src);
  const rng = mulberry32(seed);
  const at = (x, y) => h[y * size + x];
  const erBrush = makeBrush(p.erodeRadius);
  const depBrush = makeBrush(p.depositRadius);
  const hScale = Math.max(1e-6, (() => { let mx = -Infinity, mn = Infinity;
    for (const v of h) { mx = Math.max(mx, v); mn = Math.min(mn, v); } return mx - mn; })());

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
      if (x0 < 1 || x0 >= size - 1 || y0 < 1 || y0 >= size - 1) { brushAdd(px, py, sediment, depBrush); sediment = 0; break; }
      const gx = at(x0 + 1, y0) - at(x0, y0), gy = at(x0, y0 + 1) - at(x0, y0);
      const oldH = bilin(px, py);
      dx = dx * p.inertia - gx * (1 - p.inertia);
      dy = dy * p.inertia - gy * (1 - p.inertia);
      const dl = Math.hypot(dx, dy);
      if (dl !== 0) { dx /= dl; dy /= dl; }
      const nx = px + dx, ny = py + dy;
      if (nx < 1 || nx >= size - 1 || ny < 1 || ny >= size - 1) { brushAdd(px, py, sediment, depBrush); sediment = 0; break; }
      const newH = bilin(nx, ny), deltaH = newH - oldH;
      speed = Math.sqrt(Math.max(0, speed * speed + p.gravity * -deltaH));
      water *= (1 - p.evap);
      if (water <= p.minWater) { brushAdd(px, py, sediment, depBrush); sediment = 0; break; }
      const cap = Math.max(0, -deltaH) * speed * water * p.capK;
      if (sediment > cap || deltaH > 0) {
        let amount;
        if (deltaH > 0) amount = Math.min(sediment, Math.max(deltaH, (sediment - cap) * p.deposit));
        else amount = (sediment - cap) * p.deposit;
        brushAdd(px, py, amount, depBrush); sediment -= amount;
      } else {
        let amount = (cap - sediment) * p.erosion;
        amount = Math.min(amount, hScale * 0.05); // stability clamp; still fully conserved
        brushAdd(px, py, -amount, erBrush); sediment += amount;
      }
      px = nx; py = ny;
    }
  }
  return h;
}
const variance = (a) => { const m = a.reduce((s, v) => s + v, 0) / a.length;
  return a.reduce((s, v) => s + (v - m) ** 2, 0) / a.length; };
const sum = (a) => a.reduce((s, v) => s + v, 0);

const size = 80, DROPS = 4000, seeds = [1, 2, 3];
const rates = [0.02, 0.1, 0.3, 0.6];
for (const er of [0, 1])
  for (const dr of [2, 3, 4])
    for (const deposit of [0.1, 0.3])
      for (const capK of [4, 10]) {
        const means = rates.map(() => 0); let maxDs = 0, nan = false;
        for (const seed of seeds) {
          const base = generate(size, seed, 6, 0.5, 2, 0.05);
          rates.forEach((r, i) => {
            const res = erode(base, size, { drops: DROPS, inertia: 0.05, capK, deposit, erosion: r, erodeRadius: er, depositRadius: dr, evap: 0.02, gravity: 4, startSpeed: 1, startWater: 1, maxSteps: 64, minWater: 0.01 }, seed);
            const v = variance(res); if (Number.isNaN(v)) nan = true;
            means[i] += v;
            maxDs = Math.max(maxDs, Math.abs(sum(res) - sum(base)));
          });
        }
        means.forEach((m, i) => means[i] = m / seeds.length);
        const mono = means.every((v, i) => i === 0 || v > means[i - 1]);
        console.log(`erR=${er} depR=${dr} dep=${deposit} K=${capK} ${nan ? 'NaN' : ''} mono=${mono} vars=[${means.map(v => v.toFixed(5)).join(' ')}] dSum=${maxDs.toExponential(1)}`);
      }
