<template>
  <div class="panel">
    <section class="group">
      <h2>地形生成（噪声）</h2>

      <label class="row">
        <span>网格分辨率 <b>{{ noise.size }}</b></span>
        <input type="range" min="16" max="200" step="4" v-model.number="noise.size" />
      </label>
      <label class="row">
        <span>噪声层数 <b>{{ noise.octaves }}</b></span>
        <input type="range" min="1" max="12" step="1" v-model.number="noise.octaves" />
      </label>
      <label class="row">
        <span>频率倍增 <b>{{ noise.lacunarity.toFixed(2) }}</b></span>
        <input type="range" min="1.05" max="4" step="0.05" v-model.number="noise.lacunarity" />
      </label>
      <label class="row">
        <span>振幅衰减 <b>{{ noise.persistence.toFixed(2) }}</b></span>
        <input type="range" min="0.05" max="0.95" step="0.01" v-model.number="noise.persistence" />
      </label>
      <label class="row">
        <span>基础频率 <b>{{ noise.baseFrequency.toFixed(3) }}</b></span>
        <input type="range" min="0.005" max="0.3" step="0.005" v-model.number="noise.baseFrequency" />
      </label>
      <label class="row seed">
        <span>随机种子</span>
        <input type="number" v-model.number="noise.seed" />
        <button class="mini" @click="randomizeSeed">随机</button>
      </label>

      <button class="primary" :disabled="busy" @click="$emit('generate')">
        {{ busy ? '计算中…' : '生成地形' }}
      </button>
    </section>

    <section class="group">
      <h2>水力侵蚀</h2>

      <label class="row">
        <span>雨滴数量 <b>{{ erosion.raindrops }}</b></span>
        <input type="range" min="500" max="30000" step="500" v-model.number="erosion.raindrops" />
      </label>
      <label class="row">
        <span>侵蚀率 <b>{{ erosion.erosionRate.toFixed(2) }}</b></span>
        <input type="range" min="0" max="0.6" step="0.01" v-model.number="erosion.erosionRate" />
      </label>
      <label class="row">
        <span>沉积率 <b>{{ erosion.depositRate.toFixed(2) }}</b></span>
        <input type="range" min="0" max="0.6" step="0.01" v-model.number="erosion.depositRate" />
      </label>
      <label class="row">
        <span>蒸发率 <b>{{ erosion.evaporateRate.toFixed(3) }}</b></span>
        <input type="range" min="0" max="0.2" step="0.005" v-model.number="erosion.evaporateRate" />
      </label>
      <label class="row">
        <span>输沙能力系数 <b>{{ erosion.sedimentCapacity.toFixed(2) }}</b></span>
        <input type="range" min="0.1" max="4" step="0.1" v-model.number="erosion.sedimentCapacity" />
      </label>
      <label class="row">
        <span>方向惯性 <b>{{ erosion.inertia.toFixed(2) }}</b></span>
        <input type="range" min="0" max="0.9" step="0.01" v-model.number="erosion.inertia" />
      </label>
      <label class="row">
        <span>重力 <b>{{ erosion.gravity.toFixed(1) }}</b></span>
        <input type="range" min="1" max="12" step="0.5" v-model.number="erosion.gravity" />
      </label>
      <label class="row">
        <span>最大步数 <b>{{ erosion.maxSteps }}</b></span>
        <input type="range" min="8" max="200" step="4" v-model.number="erosion.maxSteps" />
      </label>

      <button class="primary" :disabled="busy || !hasTerrain" @click="$emit('erode')">
        {{ busy ? '模拟中…' : '跑一轮侵蚀' }}
      </button>
    </section>

    <section class="group">
      <h2>光照 / 视角</h2>
      <label class="row">
        <span>光源方位角 <b>{{ light.azimuth }}°</b></span>
        <input type="range" min="0" max="360" step="5" v-model.number="light.azimuth" />
      </label>
      <label class="row">
        <span>光源高度角 <b>{{ light.elevation }}°</b></span>
        <input type="range" min="5" max="89" step="1" v-model.number="light.elevation" />
      </label>
      <label class="row">
        <span>垂直夸张 <b>{{ verticalExaggeration.toFixed(1) }}</b></span>
        <input type="range" min="2" max="40" step="1" v-model.number="verticalExaggeration" />
      </label>
      <button class="mini" @click="$emit('reset-view')">重置视角</button>
    </section>

    <section class="group">
      <h2>快照</h2>
      <div class="snapshot-row">
        <input type="text" v-model="snapshotName" placeholder="快照名称" />
        <button class="mini" :disabled="busy || !hasTerrain" @click="$emit('save-snapshot', snapshotName)">
          另存
        </button>
      </div>
      <div class="snapshot-list">
        <div v-for="s in snapshots" :key="s.name" class="snapshot-item">
          <span class="snapshot-name" :title="s.name">{{ s.name }}</span>
          <span class="snapshot-meta">{{ s.size }}×{{ s.size }}</span>
          <button class="mini" :disabled="busy" @click="$emit('load-snapshot', s.name)">载入</button>
        </div>
        <p v-if="snapshots.length === 0" class="hint">暂无快照</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'

const props = defineProps({
  busy: { type: Boolean, default: false },
  hasTerrain: { type: Boolean, default: false },
  snapshots: { type: Array, default: () => [] }
})
const emit = defineEmits([
  'generate', 'erode', 'save-snapshot', 'load-snapshot', 'reset-view',
  'light-change', 'exaggeration-change'
])

const noise = reactive({
  size: 128,
  seed: 42,
  octaves: 6,
  persistence: 0.5,
  lacunarity: 2.0,
  baseFrequency: 0.05
})

const erosion = reactive({
  raindrops: 9000,
  maxSteps: 64,
  inertia: 0.05,
  sedimentCapacity: 1.2,
  depositRate: 0.1,
  erosionRate: 0.15,
  evaporateRate: 0.02,
  gravity: 4.0,
  startSpeed: 1.0,
  startWater: 1.0,
  minWater: 0.01,
  depositRadius: 3.0,
  smoothPasses: 2,
  carveFraction: 0.05
})

const light = reactive({ azimuth: 135, elevation: 55 })
const verticalExaggeration = ref(14)
const snapshotName = ref('')

function randomizeSeed() {
  noise.seed = Math.floor(Math.random() * 1_000_000)
}

// Forward live view-only controls to the parent.
watch(light, (l) => emit('light-change', { ...l }), { deep: true })
watch(verticalExaggeration, (scale) => emit('exaggeration-change', scale))

// Parent reads the noise/erosion parameter models when issuing requests.
defineExpose({ noise, erosion })
</script>

<style scoped>
.panel {
  width: 320px;
  min-width: 320px;
  height: 100%;
  overflow-y: auto;
  background: #141a28;
  color: #d7deec;
  padding: 14px 16px;
  box-sizing: border-box;
  border-right: 1px solid #243047;
}
.group {
  border-bottom: 1px solid #243047;
  padding-bottom: 14px;
  margin-bottom: 14px;
}
h2 {
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #8aa0c6;
  margin: 0 0 10px;
}
.row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 13px;
}
.row span b {
  float: right;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
}
.row.seed,
.snapshot-row {
  flex-direction: row;
  align-items: center;
  gap: 6px;
}
.row.seed input[type='number'] {
  flex: 1;
}
input[type='range'] {
  width: 100%;
  accent-color: #4f8cff;
}
input[type='number'],
input[type='text'] {
  background: #0d1320;
  border: 1px solid #2a3550;
  color: #e6ecf8;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 13px;
  min-width: 0;
}
.primary {
  width: 100%;
  margin-top: 6px;
  padding: 10px;
  border: none;
  border-radius: 8px;
  background: #2f6df6;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.primary:disabled {
  background: #2a3550;
  cursor: not-allowed;
}
.mini {
  padding: 6px 10px;
  border: 1px solid #2a3550;
  border-radius: 6px;
  background: #1b2438;
  color: #d7deec;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.mini:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.snapshot-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.snapshot-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #0d1320;
  border: 1px solid #243047;
  border-radius: 6px;
  padding: 6px 8px;
}
.snapshot-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.snapshot-meta {
  font-size: 11px;
  color: #7d8bab;
}
.hint {
  font-size: 12px;
  color: #7d8bab;
  margin: 4px 0;
}
</style>
