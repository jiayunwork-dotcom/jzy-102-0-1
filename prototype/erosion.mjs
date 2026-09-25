// Prototype of noise + hydraulic erosion to validate invariants before Java port.

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// --- Value noise with seeded permutation, gradient via finite differences ---
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
  function h(ix, iy) {
    const v = p[(p[ix & 255] + iy) & 255] / 255;
    return v * 2 - 1; // [-1,1]
  }
  function noise(x, y) {
    const x0 = Math.floor(x), y0 = Math.floor(y);
    const xf = x - x0, yf = y - y0;
    const u = fade(xf), v = fade(yf);
    const n00 = h(x0, y0), n10 = h(x0 + 1, y0), n01 = h(x0, y0 + 1), n11 = h(x0 + 1, y0 + 1);
    return lerp(lerp(n00, n10, u), lerp(n01, n11, u), v);
  }
  return noise;
}

function generateTerrain(size, seed, octaves, persistence, lacunarity, baseFrequency) {
  const noise = makeNoise(seed);
  const hmap = new Float64Array(size * size);
  let freq = baseFrequency;
  let norm = 0;
  let f = freq, amp = 1;
  for (let o = 0; o < octaves; o++) { norm += amp; amp *= persistence; f *= lacunarity; }
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let total = 0, amp2 = 1, f2 = freq;
      for (let o = 0; o < octaves; o++) {
        total += noise(x / size * f2 * size / 8, y / size * f2 * size / 8) * amp2;
        // simpler: sample coordinates directly
        o;
        amp2 *= persistence; f2 *= lacunarity;
      }
      hmap[y * size + x] = total;
    }
  }
  return hmap;
}

// cleaner generator: sample at fx*x/.. ; use domain coords directly
function generate(size, seed, octaves, persistence, lacunarity, baseFreq) {
  const noise = makeNoise(seed);
  const h = new Float64Array(size * size);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let total = 0, amp = 1, norm = 0, f = baseFreq;
      for (let o = 0; o < octaves; o++) {
        total += noise(x * f, y * f) * amp;
        norm += amp;
        amp *= persistence; f *= lacunarity;
      }
      h[y * size + x] = total / norm;
    }
  }
  return h;
}

// --- Erosion ---
const PARAMS = {
  drops: 20000,
  inertia: 0.05,
  sedimentCapacity: 4,
  depositRate: 0.3,
  erosionRate: 0.3,
  erosionRadius: 3,
  evaporateRate: 0.02,
  gravity: 4,
  startSpeed: 1,
  startWater: 1,
  maxSteps: 64,
  minWater: 0.01,
  maxDrop: 4,
};

function erode(hmap, size, p, seed) {
  const h = Float64Array.from(hmap);
  const rng = mulberry32(seed);
  const pos = new Float64Array(size * size); // brush weight scratch

  function idx(x, y) { return y * size + x; }
  function heightAt(x, y) {
    const x0 = Math.floor(x), y0 = Math.floor(y);
    const fx = x - x0, fy = y - y0;
    return h[idx(x0, y0)] * (1 - fx) * (1 - fy)
         + h[idx(x0 + 1, y0)] * fx * (1 - fy)
         + h[idx(x0, y0 + 1)] * (1 - fx) * fy
         + h[idx(x0 + 1, y0 + 1)] * fx * fy;
  }
  // gradient at (x,y) in direction (dirX,dirY)
  function gradient(x, y, dx, dy) {
    const x0 = Math.floor(x), y0 = Math.floor(y);
    const fx = x - x0, fy = y - y0;
    const hL = h[idx(x0, y0)], hR = h[idx(x0 + 1, y0)];
    const hD = h[idx(x0, y0 + 1)], hU = h[idx(x0 + 1, y0 + 1)];
    const gx = hR - hL;
    const gy = hU - hD;
    return gx * dx + gy * dy;
  }

  function depositAt(px, py, amount, radius) {
    // bilinear 4 nodes (radius ignored, use unit brush)
    const x0 = Math.floor(px), y0 = Math.floor(py);
    const fx = px - x0, fy = py - y0;
    let rem = amount;
    const w00 = (1 - fx) * (1 - fy);
    const w10 = fx * (1 - fy);
    const w01 = (1 - fx) * fy;
    let a00 = amount * w00, a10 = amount * w10, a01 = amount * w01;
    let a11 = amount - a00 - a10 - a01;
    h[idx(x0, y0)] += a00;
    h[idx(x0 + 1, y0)] += a10;
    h[idx(x0, y0 + 1)] += a01;
    h[idx(x0 + 1, y0 + 1)] += a11;
  }

  for (let d = 0; d < p.drops; d++) {
    let px = rng() * (size - 1);
    let py = rng() * (size - 1);
    let dx = 0, dy = 0;
    let speed = p.startSpeed;
    let water = p.startWater;
    let sediment = 0;

    for (let step = 0; step < p.maxSteps; step++) {
      const x0 = Math.floor(px), y0 = Math.floor(py);
      if (x0 < 0 || x0 >= size - 1 || y0 < 0 || y0 >= size - 1) {
        depositAt(px, py, sediment, 0); sediment = 0;
        break;
      }
      const oldH = heightAt(px, py);
      // update direction using local gradient
      const fx = px - x0, fy = py - y0;
      const hL = h[idx(x0, y0)], hR = h[idx(x0 + 1, y0)];
      const hD = h[idx(x0, y0 + 1)], hU = h[idx(x0 + 1, y0 + 1)];
      const gx = hR - hL, gy = hU - hD;
      dx = dx * p.inertia - gx * (1 - p.inertia);
      dy = dy * p.inertia - gy * (1 - p.inertia);
      const dl = Math.hypot(dx, dy);
      if (dl !== 0) { dx /= dl; dy /= dl; }
      const nx = px + dx, ny = py + dy;
      if (nx < 0 || nx >= size - 1 || ny < 0 || ny >= size - 1) {
        depositAt(px, py, sediment, 0); sediment = 0;
        break;
      }
      const newH = heightAt(nx, ny);
      const deltaH = newH - oldH; // negative downhill
      speed = Math.sqrt(Math.max(0, speed * speed + p.gravity * -deltaH));
      // evaporate strictly before any erosion/deposit this step
      water *= (1 - p.evaporateRate);
      if (water <= p.minWater) {
        depositAt(px, py, sediment, 0); sediment = 0;
        break;
      }
      const capacity = Math.max(0, -deltaH) * speed * water * p.sedimentCapacity;
      if (sediment > capacity || deltaH > 0) {
        // deposit
        let amount;
        if (deltaH > 0) {
          // climbing: fill up to newH at most
          amount = Math.min(sediment, Math.max(deltaH, (sediment - capacity) * p.depositRate));
        } else {
          amount = (sediment - capacity) * p.depositRate;
        }
        depositAt(px, py, amount, 0);
        sediment -= amount;
      } else {
        // erode, capped so nodes aren't carved below newH (no weird uphill pits)
        let amount = Math.min((capacity - sediment) * p.erosionRate, -deltaH);
        if (amount > 1e-9) {
          // distribute bilinear over the 4 nodes, but each node can only drop to floor=newH
          const w00 = (1 - fx) * (1 - fy);
          const w10 = fx * (1 - fy);
          const w01 = (1 - fx) * fy;
          const w11 = fx * fy;
          const nodes = [
            [x0, y0, w00], [x0 + 1, y0, w10], [x0, y0 + 1, w01], [x0 + 1, y0 + 1, w11],
          ];
          let need = amount;
          for (let pass = 0; pass < 4 && need > 1e-12; pass++) {
            let wsum = 0, avail = 0;
            for (const [, , w] of nodes) wsum += w;
            for (const [nx2, ny2, w] of nodes) {
              const floor = newH;
              const room = Math.max(0, h[idx(nx2, ny2)] - floor);
              avail += Math.min(room, (need / wsum) * w);
            }
            const give = Math.min(need, avail);
            for (const [nx2, ny2, w] of nodes) {
              const floor = newH;
              const room = Math.max(0, h[idx(nx2, ny2)] - floor);
              const take = Math.min(room, (give / wsum) * w);
              h[idx(nx2, ny2)] -= take;
            }
            need -= give;
            if (give < 1e-12) break;
          }
          const eroded = amount - need;
          sediment += eroded;
        }
      }
      px = nx; py = ny;
    }
  }
  return h;
}

function variance(a) {
  const m = a.reduce((s, v) => s + v, 0) / a.length;
  return a.reduce((s, v) => s + (v - m) * (v - m), 0) / a.length;
}
function sum(a) { return a.reduce((s, v) => s + v, 0); }
function gradientEnergy(h, size) {
  let e = 0, n = 0;
  for (let y = 1; y < size - 1; y++) {
    for (let x = 1; x < size - 1; x++) {
      const gx = (h[y * size + x + 1] - h[y * size + x - 1]) / 2;
      const gy = (h[(y + 1) * size + x] - h[(y - 1) * size + x]) / 2;
      e += gx * gx + gy * gy; n++;
    }
  }
  return e / n;
}

const size = 128;
let allPass = true;
for (const seed of [1, 2, 3, 7, 42]) {
  const base = generate(size, seed, 6, 0.5, 2, 0.06);
  const pLo = { ...PARAMS, erosionRate: 0.001 };
  const pHi = { ...PARAMS, erosionRate: 0.6 };
  const lo = erode(base, size, pLo, seed);
  const hi = erode(base, size, pHi, seed);
  const vB = variance(base), vL = variance(lo), vH = variance(hi);
  // determinism
  const hi2 = erode(base, size, pHi, seed);
  const det = hi.every((v, i) => v === hi2[i]);
  // mass
  const dSum = sum(hi) - sum(base);
  console.log(`seed=${seed} var base=${vB.toFixed(4)} lo=${vL.toFixed(4)} hi=${vH.toFixed(4)} hi>lo? ${vH > vL} det? ${det} dSum=${dSum.toExponential(2)}`);
  if (!(vH > vL && det && Math.abs(dSum) < 1e-6)) allPass = false;
}
// octaves vs gradient energy
for (const seed of [1, 2]) {
  const g1 = gradientEnergy(generate(size, seed, 1, 0.5, 2, 0.06), size);
  const g6 = gradientEnergy(generate(size, seed, 8, 0.5, 2, 0.06), size);
  console.log(`seed=${seed} gradEnergy oct1=${g1.toFixed(5)} oct8=${g6.toFixed(5)} rise? ${g6 > g1}`);
  if (!(g6 > g1)) allPass = false;
}
console.log(allPass ? 'ALL PASS' : 'FAIL');
