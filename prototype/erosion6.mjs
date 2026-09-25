// Prototype v6: pit-free erosion (cap by -deltaH fraction) + concentrated erode
// brush + diffuse deposit brush. Checks: variance order, stability over rounds,
// determinism, exact mass conservation, water monotonicity.
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
function makeBrush(radius) {
  radius = Math.max(0, radius);
  if (radius === 0) return { cells: [[0, 0]], weights: [1] };
  const cells = [], raw = [];
  const R = Math.ceil(radius);
  for (let dy = -R; dy <= R; dy++) for (let dx = -R; dx <= R; dx++) {
    const d = Math.hypot(dx, dy);
    if (d <= radius) { cells.push([dx, dy]); raw.push(1 - d / radius); }
  }
  const s = raw.reduce((a, b) => a + b, 0);
  return { cells, weights: raw.map(w => w / s) };
}
function erode(src, size, p, seed, waterLog) {
  const h = Float64Array.from(src);
  const rng = mulberry32(seed);
  const at = (x, y) => h[y * size + x];
  const erBrush = makeBrush(p.erodeRadius), depBrush = makeBrush(p.depositRadius);
  function brushAdd(px, py, amount, brush) {
    const cx = Math.round(px), cy = Math.round(py);
    let wsum = 0;
    for (let i = 0; i < brush.cells.length; i++) {
      const [dx, dy] = brush.cells[i];
      if (cx + dx >= 0 && cx + dx < size && cy + dy >= 0 && cy + dy < size) wsum += brush.weights[i];
    }
    if (wsum === 0) return 0;
    for (let i = 0; i < brush.cells.length; i++) {
      const [dx, dy] = brush.cells[i];
      const x = cx + dx, y = cy + dy;
      if (x >= 0 && x < size && y >= 0 && y < size) h[y * size + x] += amount * brush.weights[i] / wsum;
    }
    return amount;
  }
  function bilin(px, py) {
    const x0 = Math.floor(px), y0 = Math.floor(py), fx = px - x0, fy = py - y0;
    return at(x0, y0) * (1 - fx) * (1 - fy) + at(x0 + 1, y0) * fx * (1 - fy)
         + at(x0, y0 + 1) * (1 - fx) * fy + at(x0 + 1, y0 + 1) * fx * fy;
  }
  let violations = 0;
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
      if (waterLog) waterLog(water, d, step);
      if (water <= p.minWater) { brushAdd(px, py, sediment, depBrush); sediment = 0; break; }
      const cap = Math.max(0, -deltaH) * speed * water * p.capK;
      if (sediment > cap || deltaH > 0) {
        let amount;
        if (deltaH > 0) amount = Math.min(sediment, Math.max(deltaH, (sediment - cap) * p.deposit));
        else amount = (sediment - cap) * p.deposit;
        brushAdd(px, py, amount, depBrush); sediment -= amount;
      } else {
        // pit-free: never carve more than a fraction of the drop's fall height
        let amount = Math.min((cap - sediment) * p.erosion, (-deltaH) * p.maxCarveFrac);
        amount = Math.max(0, amount);
        if (amount > 1e-12) { brushAdd(px, py, -amount, erBrush); sediment += amount; }
      }
      px = nx; py = ny;
    }
    if (sediment > 1e-12) brushAdd(px, py, sediment, depBrush);
  }
  return { h, violations };
}
const variance = (a) => { const m = a.reduce((s, v) => s + v, 0) / a.length;
  return a.reduce((s, v) => s + (v - m) ** 2, 0) / a.length; };
const sum = (a) => a.reduce((s, v) => s + v, 0);

const size = 96, DROPS = 8000, seeds = [1, 2, 3, 7, 42];
const base = { }; seeds.forEach(s => base[s] = generate(size, s, 6, 0.5, 2, 0.05));
const rates = [0.05, 0.15, 0.3, 0.5];
function mkParams(r, extra) {
  return { drops: DROPS, inertia: 0.05, capK: 4, deposit: 0.1, erosion: r,
    erodeRadius: 0, depositRadius: 3, evap: 0.02, gravity: 4,
    startSpeed: 1, startWater: 1, maxSteps: 64, minWater: 0.01, maxCarveFrac: 0.5, ...extra };
}
console.log('--- single round, varying depRadius ---');
for (const dr of [2, 3, 4, 5]) {
  const means = rates.map(() => 0); let maxDs = 0, nan = false, maxMin = Infinity;
  for (const s of seeds) {
    rates.forEach((r, i) => {
      const { h } = erode(base[s], size, mkParams(r, { depositRadius: dr }), s);
      const v = variance(h); if (Number.isNaN(v)) nan = true;
      means[i] += v; maxDs = Math.max(maxDs, Math.abs(sum(h) - sum(base[s])));
    });
  }
  means.forEach((m, i) => means[i] /= seeds.length);
  console.log(`depR=${dr} ${nan ? 'NaN ' : ''}vars=[${means.map(v => v.toFixed(5)).join(' ')}] mono=${means.every((v,i)=>!i||v>means[i-1])} dSum=${maxDs.toExponential(1)}`);
}
console.log('--- three sequential rounds, depRadius=4, deposit rate variants ---');
for (const dep of [0.05, 0.1, 0.2]) {
  const rows = rates.map(() => ({ vars: [], maxDs: 0 }));
  for (const s of seeds) {
    rates.forEach((r, i) => {
      let cur = base[s];
      const vs = [];
      for (let round = 0; round < 3; round++) {
        const { h } = erode(cur, size, mkParams(r, { depositRadius: 4, deposit: dep }), s + round * 1000);
        cur = h; vs.push(variance(cur));
        rows[i].maxDs = Math.max(rows[i].maxDs, Math.abs(sum(cur) - sum(base[s])));
      }
      vs.forEach((v, k) => rows[i].vars[k] = (rows[i].vars[k] || 0) + v);
    });
  }
  rows.forEach(row => row.vars = row.vars.map(v => v / seeds.length));
  const roundMono = rows.every((row, i) => i === 0 || true) &&
    [0, 1, 2].map(k => rows.every((row, i) => i === 0 || row.vars[k] > rows[i - 1].vars[k]));
  const stable = rows.every(row => row.vars.every(v => Number.isFinite(v) && v < 1));
  console.log(`dep=${dep} stable=${stable} perRoundMono=[${roundMono}]`);
  rows.forEach((row, i) => console.log(`   r=${rates[i]} vars=[${row.vars.map(v=>v.toFixed(5)).join(' ')}] maxCumDs=${row.maxDs.toExponential(1)}`));
}
// determinism + water monotonicity on chosen config
{
  const p = mkParams(0.3, { depositRadius: 4, deposit: 0.1 });
  const a = erode(base[1], size, p, 1).h;
  const b = erode(base[1], size, p, 1).h;
  console.log('determinism:', a.every((v, i) => v === b[i]));
  let prevByDrop = new Map(), bad = 0, count = 0;
  erode(base[1], size, p, 1, (w, d, step) => {
    const prev = prevByDrop.get(d);
    if (step > 0 && prev !== undefined && w > prev + 1e-15) bad++;
    prevByDrop.set(d, w); count++;
  });
  console.log('water non-increasing observations:', count, 'violations:', bad);
}
