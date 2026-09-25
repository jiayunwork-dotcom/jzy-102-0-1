/** 面板参数的默认值（App 首次自动生成地形时也会用到）。 */
export const defaultNoise = {
  resolution: 128,
  octaves: 5,
  lacunarity: 2.0,
  persistence: 0.5,
  baseFrequency: 3.0,
  seed: 42
}

export const defaultErosion = {
  dropletCount: 30000,
  erodeRate: 0.3,
  depositRate: 0.3,
  evaporateRate: 0.02,
  gravity: 4.0,
  capacityFactor: 1.5,
  baseFlow: 0.01,
  inertia: 0.05,
  maxSteps: 64,
  minWater: 0.01,
  seed: 7
}

export const defaultLight = {
  azimuth: 45,
  elevation: 50
}
