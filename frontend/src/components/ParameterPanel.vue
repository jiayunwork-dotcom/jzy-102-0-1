<template>
  <aside class="panel">
    <h1>地形水力侵蚀演示</h1>

    <section>
      <h2>噪声地形生成</h2>
      <label class="slider">
        <span>网格分辨率 <b>{{ noise.resolution }}</b></span>
        <input type="range" min="32" max="256" step="32" v-model.number="noise.resolution" />
      </label>
      <label class="slider">
        <span>层数 octaves <b>{{ noise.octaves }}</b></span>
        <input type="range" min="1" max="10" step="1" v-model.number="noise.octaves" />
      </label>
      <label class="slider">
        <span>频率倍增 lacunarity <b>{{ noise.lacunarity.toFixed(1) }}</b></span>
        <input type="range" min="1" max="4" step="0.1" v-model.number="noise.lacunarity" />
      </label>
      <label class="slider">
        <span>振幅衰减 persistence <b>{{ noise.persistence.toFixed(2) }}</b></span>
        <input type="range" min="0.1" max="0.9" step="0.05" v-model.number="noise.persistence" />
      </label>
      <label class="slider">
        <span>基础频率 <b>{{ noise.baseFrequency.toFixed(1) }}</b></span>
        <input type="range" min="0.5" max="8" step="0.5" v-model.number="noise.baseFrequency" />
      </label>
      <label class="slider">
        <span>随机种子 <b>{{ noise.seed }}</b></span>
        <input type="range" min="0" max="9999" step="1" v-model.number="noise.seed" />
      </label>
      <button class="primary" :disabled="busy" @click="emitGenerate">
        {{ busy === 'generate' ? '生成中…' : '生成地形' }}
      </button>
    </section>

    <section>
      <h2>水力侵蚀模拟</h2>
      <label class="slider">
        <span>雨滴数量 <b>{{ erosion.dropletCount }}</b></span>
        <input type="range" min="1000" max="100000" step="1000" v-model.number="erosion.dropletCount" />
      </label>
      <label class="slider">
        <span>侵蚀率 <b>{{ erosion.erodeRate.toFixed(2) }}</b></span>
        <input type="range" min="0" max="1" step="0.05" v-model.number="erosion.erodeRate" />
      </label>
      <label class="slider">
        <span>沉积率 <b>{{ erosion.depositRate.toFixed(2) }}</b></span>
        <input type="range" min="0" max="1" step="0.05" v-model.number="erosion.depositRate" />
      </label>
      <label class="slider">
        <span>蒸发率 <b>{{ erosion.evaporateRate.toFixed(3) }}</b></span>
        <input type="range" min="0" max="0.2" step="0.005" v-model.number="erosion.evaporateRate" />
      </label>
      <label class="slider">
        <span>重力系数 <b>{{ erosion.gravity.toFixed(1) }}</b></span>
        <input type="range" min="0" max="10" step="0.5" v-model.number="erosion.gravity" />
      </label>
      <label class="slider">
        <span>携带能力系数 <b>{{ erosion.capacityFactor.toFixed(1) }}</b></span>
        <input type="range" min="0.5" max="4" step="0.1" v-model.number="erosion.capacityFactor" />
      </label>
      <label class="slider">
        <span>方向惯性 <b>{{ erosion.inertia.toFixed(2) }}</b></span>
        <input type="range" min="0" max="0.9" step="0.05" v-model.number="erosion.inertia" />
      </label>
      <label class="slider">
        <span>最大步数 <b>{{ erosion.maxSteps }}</b></span>
        <input type="range" min="10" max="200" step="5" v-model.number="erosion.maxSteps" />
      </label>
      <label class="slider">
        <span>水量阈值 <b>{{ erosion.minWater.toFixed(2) }}</b></span>
        <input type="range" min="0.01" max="0.3" step="0.01" v-model.number="erosion.minWater" />
      </label>
      <label class="slider">
        <span>随机种子 <b>{{ erosion.seed }}</b></span>
        <input type="range" min="0" max="9999" step="1" v-model.number="erosion.seed" />
      </label>
      <button class="primary erode" :disabled="busy || !hasTerrain" @click="emitErode">
        {{ busy === 'erode' ? '侵蚀中…' : '侵蚀一轮' }}
      </button>
    </section>

    <section>
      <h2>光照</h2>
      <label class="slider">
        <span>方位角 <b>{{ lightAzimuth }}°</b></span>
        <input type="range" min="0" max="360" step="5"
               :value="lightAzimuth" @input="$emit('update:lightAzimuth', +$event.target.value)" />
      </label>
      <label class="slider">
        <span>高度角 <b>{{ lightElevation }}°</b></span>
        <input type="range" min="5" max="90" step="5"
               :value="lightElevation" @input="$emit('update:lightElevation', +$event.target.value)" />
      </label>
    </section>

    <section>
      <h2>快照</h2>
      <div class="snapshot-save">
        <input v-model.trim="snapshotName" type="text" placeholder="快照名称" maxlength="100" />
        <button :disabled="busy || !hasTerrain || !snapshotName" @click="emitSave">另存</button>
      </div>
      <ul v-if="snapshots.length" class="snapshot-list">
        <li v-for="s in snapshots" :key="s.name">
          <span class="snapshot-name">{{ s.name }}</span>
          <span class="snapshot-meta">{{ s.resolution }}²</span>
          <button :disabled="busy" @click="$emit('load-snapshot', s.name)">加载</button>
        </li>
      </ul>
      <p v-else class="snapshot-empty">暂无快照</p>
    </section>

    <section v-if="stats" class="stats">
      <h2>上一轮侵蚀统计</h2>
      <p>雨滴：{{ stats.dropletsSimulated }} 颗 · 共 {{ stats.totalSteps }} 步</p>
      <p>总高度：{{ stats.totalHeightBefore.toFixed(4) }} → {{ stats.totalHeightAfter.toFixed(4) }}</p>
      <p class="conservation">
        质量守恒偏差：{{ (stats.totalHeightAfter - stats.totalHeightBefore).toExponential(2) }}
      </p>
    </section>

    <div v-if="error" class="error-box">{{ error }}</div>
    <div v-if="notice" class="notice-box">{{ notice }}</div>
  </aside>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { defaultErosion, defaultNoise } from '../defaults.js'

const props = defineProps({
  hasTerrain: { type: Boolean, default: false },
  busy: { type: [String, Boolean], default: false },
  stats: { type: Object, default: null },
  error: { type: String, default: '' },
  notice: { type: String, default: '' },
  snapshots: { type: Array, default: () => [] },
  lightAzimuth: { type: Number, default: 45 },
  lightElevation: { type: Number, default: 50 }
})

const emit = defineEmits([
  'generate',
  'erode',
  'save-snapshot',
  'load-snapshot',
  'update:lightAzimuth',
  'update:lightElevation'
])

// 面板持有参数状态，点击按钮时把整份参数抛给 App
const noise = reactive({ ...defaultNoise })
const erosion = reactive({ ...defaultErosion })
const snapshotName = ref('')

function emitGenerate() {
  emit('generate', { ...noise })
}

function emitErode() {
  emit('erode', { ...erosion })
}

function emitSave() {
  emit('save-snapshot', {
    name: snapshotName.value,
    noise: { ...noise },
    erosion: { ...erosion }
  })
}

/** 加载快照后把快照里的参数回填到面板上。 */
function setParams(savedNoise, savedErosion) {
  if (savedNoise) Object.assign(noise, savedNoise)
  if (savedErosion) Object.assign(erosion, savedErosion)
}

defineExpose({ setParams })
</script>

<style scoped>
.panel {
  width: 320px;
  min-width: 320px;
  height: 100%;
  overflow-y: auto;
  background: #171e29;
  color: #cfd8e6;
  padding: 16px 18px 32px;
  box-sizing: border-box;
  font-size: 13px;
}
h1 {
  font-size: 17px;
  margin: 2px 0 14px;
  color: #eef3fa;
}
h2 {
  font-size: 13px;
  margin: 0 0 10px;
  color: #8fa3c0;
  letter-spacing: 0.05em;
}
section {
  border-top: 1px solid #232d3d;
  padding: 14px 0;
}
.slider {
  display: block;
  margin-bottom: 9px;
}
.slider span {
  display: flex;
  justify-content: space-between;
  margin-bottom: 3px;
  color: #a9b8cd;
}
.slider b {
  color: #e8eef7;
  font-weight: 600;
}
input[type='range'] {
  width: 100%;
  accent-color: #4f8fdd;
}
button {
  border: 1px solid #33405a;
  background: #232e40;
  color: #d5e0f0;
  border-radius: 6px;
  padding: 7px 12px;
  cursor: pointer;
  font-size: 13px;
}
button:hover:not(:disabled) {
  background: #2d3b53;
}
button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
button.primary {
  width: 100%;
  margin-top: 6px;
  background: #2f5f9e;
  border-color: #3a72bd;
  color: #fff;
  font-weight: 600;
}
button.primary:hover:not(:disabled) {
  background: #386fB5;
}
button.erode {
  background: #8a5a2b;
  border-color: #a06c35;
}
button.erode:hover:not(:disabled) {
  background: #a06c35;
}
.snapshot-save {
  display: flex;
  gap: 6px;
}
.snapshot-save input {
  flex: 1;
  min-width: 0;
  background: #10161f;
  border: 1px solid #33405a;
  border-radius: 6px;
  color: #dfe7f2;
  padding: 6px 8px;
}
.snapshot-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
}
.snapshot-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px solid #20293a;
}
.snapshot-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.snapshot-meta {
  color: #6d7f9a;
  font-size: 12px;
}
.snapshot-empty {
  color: #5c6b82;
  margin: 8px 0 0;
}
.stats p {
  margin: 4px 0;
  color: #a9b8cd;
}
.conservation {
  color: #7fc98a !important;
}
.error-box {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #4a2027;
  border: 1px solid #7a3540;
  color: #f0b9c0;
  word-break: break-all;
}
.notice-box {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #1f3a2a;
  border: 1px solid #35603f;
  color: #a9dcb4;
}
</style>
