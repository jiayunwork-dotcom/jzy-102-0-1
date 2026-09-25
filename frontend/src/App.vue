<template>
  <div class="app">
    <ParameterPanel
      ref="panelRef"
      :busy="busy"
      :has-terrain="hasTerrain"
      :snapshots="snapshots"
      @generate="onGenerate"
      @erode="onErode"
      @save-snapshot="onSaveSnapshot"
      @load-snapshot="onLoadSnapshot"
      @reset-view="onResetView"
      @light-change="onLightChange"
      @exaggeration-change="onExaggerationChange"
    />

    <div class="viewport">
      <div ref="threeContainer" class="three-container"></div>

      <div class="hud">
        <h1>水力侵蚀地形实验室</h1>
        <p v-if="stats" class="stats">
          均值 {{ stats.mean.toFixed(3) }} · 方差 <b>{{ stats.variance.toFixed(4) }}</b>
          · 梯度能量 {{ stats.gradientEnergy.toFixed(4) }} ·
          高程 [{{ stats.min.toFixed(2) }}, {{ stats.max.toFixed(2) }}]
        </p>
        <p v-if="roundCount > 0" class="rounds">已追加侵蚀轮数：{{ roundCount }}</p>
      </div>

      <transition name="fade">
        <div v-if="message" class="toast" :class="messageKind">{{ message }}</div>
      </transition>
      <div v-if="!hasTerrain" class="overlay">
        <div class="overlay-card">
          <p>调整左侧噪声参数，点击「生成地形」开始。</p>
          <p class="sub">生成后反复点击「跑一轮侵蚀」，观察山脊被冲刷成河谷沟壑。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import ParameterPanel from './components/ParameterPanel.vue'
import { TerrainScene } from './components/TerrainView.js'
import {
  generateTerrain,
  runErosion,
  saveSnapshot,
  listSnapshots,
  loadSnapshot
} from './api/terrainApi.js'

const panelRef = ref(null)
const threeContainer = ref(null)

let scene = null
const busy = ref(false)
const hasTerrain = ref(false)
const roundCount = ref(0)
const message = ref('')
const messageKind = ref('info')
const snapshots = ref([])

const current = reactive({
  size: 0,
  heights: null,
  stats: null
})
const stats = ref(null)

let toastTimer = null
function notify(text, kind = 'info') {
  message.value = text
  messageKind.value = kind
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (message.value = ''), 3500)
}

function applyTerrain(data) {
  current.size = data.size
  current.heights = data.heights
  current.stats = {
    mean: data.mean,
    variance: data.variance,
    gradientEnergy: data.gradientEnergy,
    min: data.min,
    max: data.max
  }
  stats.value = current.stats
  scene.setTerrain(data.heights, data.size)
  hasTerrain.value = true
}

async function onGenerate() {
  const p = panelRef.value.noise
  busy.value = true
  try {
    const data = await generateTerrain({ ...p })
    applyTerrain(data)
    roundCount.value = 0
    scene.resetView()
    notify('新地形已生成')
  } catch (e) {
    notify(e.message, 'error')
  } finally {
    busy.value = false
  }
}

async function onErode() {
  if (!hasTerrain.value) return
  const noise = panelRef.value.noise
  const erosion = panelRef.value.erosion
  busy.value = true
  try {
    const data = await runErosion({
      size: current.size,
      heights: current.heights,
      noise: { ...noise, size: current.size },
      ...erosion
    })
    applyTerrain(data)
    roundCount.value += 1
    notify(`第 ${roundCount.value} 轮侵蚀完成`)
  } catch (e) {
    notify(e.message, 'error')
  } finally {
    busy.value = false
  }
}

async function onSaveSnapshot(name) {
  if (!name || !name.trim()) {
    notify('请先填写快照名称', 'error')
    return
  }
  const noise = panelRef.value.noise
  const erosion = panelRef.value.erosion
  busy.value = true
  try {
    await saveSnapshot({
      name: name.trim(),
      size: current.size,
      heights: current.heights,
      noise: { ...noise, size: current.size },
      erosion: { ...erosion }
    })
    await refreshSnapshots()
    notify(`快照「${name.trim()}」已保存`)
  } catch (e) {
    notify(e.message, 'error')
  } finally {
    busy.value = false
  }
}

async function onLoadSnapshot(name) {
  busy.value = true
  try {
    const data = await loadSnapshot(name)
    applyTerrain(data)
    // Restore the panel's parameter values from the snapshot so a subsequent
    // erosion round uses the same configuration.
    const noise = panelRef.value.noise
    if (data.noiseParams) {
      Object.assign(noise, {
        size: data.noiseParams.size,
        seed: data.noiseParams.seed,
        octaves: data.noiseParams.octaves,
        persistence: data.noiseParams.persistence,
        lacunarity: data.noiseParams.lacunarity,
        baseFrequency: data.noiseParams.baseFrequency
      })
    }
    notify(`已载入快照「${name}」`)
  } catch (e) {
    notify(e.message, 'error')
  } finally {
    busy.value = false
  }
}

async function refreshSnapshots() {
  try {
    snapshots.value = await listSnapshots()
  } catch (_) {
    // listing is best-effort
  }
}

function onResetView() {
  scene.resetView()
}

function onLightChange(l) {
  scene?.setLight(l.azimuth, l.elevation)
}

function onExaggerationChange(scale) {
  scene?.setHeightScale(scale)
  if (hasTerrain.value) scene.setTerrain(current.heights, current.size)
}

onMounted(() => {
  scene = new TerrainScene(threeContainer.value)
  refreshSnapshots()
})

onBeforeUnmount(() => {
  scene?.dispose()
})
</script>

<style>
* {
  box-sizing: border-box;
}
html,
body,
#app {
  margin: 0;
  height: 100%;
  font-family: -apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: #0b0f1a;
  color: #d7deec;
}
.app {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}
.viewport {
  position: relative;
  flex: 1;
  min-width: 0;
}
.three-container {
  position: absolute;
  inset: 0;
}
.hud {
  position: absolute;
  top: 12px;
  left: 12px;
  background: rgba(13, 19, 32, 0.78);
  border: 1px solid #243047;
  border-radius: 10px;
  padding: 10px 14px;
  pointer-events: none;
  max-width: 60%;
}
.hud h1 {
  font-size: 15px;
  margin: 0 0 4px;
}
.stats {
  font-size: 12px;
  color: #aebad2;
  margin: 0;
  font-variant-numeric: tabular-nums;
}
.stats b {
  color: #ffd479;
}
.rounds {
  font-size: 12px;
  color: #8aa0c6;
  margin: 4px 0 0;
}
.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.overlay-card {
  background: rgba(13, 19, 32, 0.9);
  border: 1px solid #2a3550;
  border-radius: 12px;
  padding: 24px 32px;
  text-align: center;
}
.overlay-card p {
  margin: 6px 0;
}
.overlay-card .sub {
  color: #8aa0c6;
  font-size: 13px;
}
.toast {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: #1b2438;
  border: 1px solid #2a3550;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 10;
}
.toast.error {
  border-color: #8a3b3b;
  background: #3a2024;
  color: #ffc9c9;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
