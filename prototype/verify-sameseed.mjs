import { readFileSync, writeFileSync } from 'fs';
// Extract pure functions from tune.mjs (everything before the sweep driver).
let src = readFileSync('/workspace/prototype/tune.mjs', 'utf8');
src = src.slice(0, src.indexOf('for (const cf of'));
src += '\nexport { generate, smooth, erode, variance, sum, P };\n';
writeFileSync('/workspace/prototype/_lib.mjs', src);
const { generate, smooth, erode, variance, sum, P } = await import('./_lib.mjs');

const size = 96;
for (const s of [1, 7, 42]) {
  const base = generate(size, s, 6, 0.5, 2, 0.05);
  const bed = smooth(base, size, 2);
  const run = (start, r) => erode(start, bed, size, P(r, 0.05, 1.2, 0.1), s);
  // linear superposition check: every same-seed round must add the SAME delta
  const r1 = run(base, 0.3);
  const r2 = run(r1, 0.3);
  const r3 = run(r2, 0.3);
  let lin = true;
  for (let i = 0; i < r3.length; i++) {
    const d1 = r1[i] - base[i];
    if (Math.abs((r2[i] - r1[i]) - d1) > 1e-12 || Math.abs((r3[i] - r2[i]) - d1) > 1e-12) { lin = false; break; }
  }
  // trajectories low vs high erosion over 10 rounds
  let lo = base, hi = base;
  const vL = [variance(base)], vH = [variance(base)];
  let massErr = 0;
  for (let k = 0; k < 10; k++) {
    lo = run(lo, 0.02); hi = run(hi, 0.3);
    vL.push(variance(lo)); vH.push(variance(hi));
    massErr = Math.max(massErr, Math.abs(sum(lo) - sum(base)), Math.abs(sum(hi) - sum(base)));
  }
  const order = vH.every((v, i) => i === 0 || v > vL[i]);
  console.log(`seed=${s} linearAdd=${lin} order10=${order} massErr=${massErr.toExponential(1)}`);
  console.log(`  lo [${vL.map(v => v.toFixed(3)).join(', ')}]`);
  console.log(`  hi [${vH.map(v => v.toFixed(3)).join(', ')}]`);
}
