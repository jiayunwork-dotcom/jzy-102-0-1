<template>
  <div class="layout">
    <ParameterPanel
      ref="panel"
      :has-terrain="heightmap !== null"
      :busy="busy"
      :stats="stats"
      :error="error"
      :notice="notice"
      :snapshots="snapshots"
      v-model:light-azimuth="light.azimuth"
      v-model:light-elevation="light.elevation"
      @generate="onGenerate"
      @erode="onErode"
      @save-snapshot="onSaveSnapshot"
      @load-snapshot="onLoadSnapshot"
    />
    <main class="viewport">
      <TerrainViewer
        :heightmap="heightmap"
        :resolution="resolution"
        :light-azimuth="light.azimuth"
        :light-elevation="light.elevation"
      />
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import ParameterPanel from './components/ParameterPanel.vue'
import TerrainViewer from './components/TerrainViewer.vue'
import * as api from './api/terrainApi.js'
import { defaultErosion, defaultLight, defaultNoise } from './defaults.js'

const panel = ref(null)

// 当前画面中的高度场及其分辨率（前端持有，侵蚀时提交给后端追加一轮）
const heightmap = ref(null)
const resolution = ref(0)

// 最近一次使用的参数（随快照一起保存）
const lastNoise = ref({ ...defaultNoise })
const lastErosion = ref({ ...defaultErosion })

const light = reactive({ ...defaultLight })
const snapshots = ref([])
const stats = ref(null)
const busy = ref(false) // false | 'generate' | 'erode'
const error = ref('')
const notice = ref('')

function clearMessages() {
  error.value = ''
  notice.value = ''
}

async function run(taskName, fn) {
  busy.value = taskName
  clearMessages()
  try {
    await fn()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function onGenerate(noiseParams) {
  run('generate', async () => {
    const data = await api.generateTerrain(noiseParams)
    heightmap.value = data.heightmap
    resolution.value = data.resolution
    lastNoise.value = noiseParams
    stats.value = null
    notice.value = `已生成 ${data.resolution}×${data.resolution} 高度场`
  })
}

function onErode(erosionParams) {
  if (!heightmap.value) {
    error.value = '请先生成地形'
    return
  }
  run('erode', async () => {
    const data = await api.erodeTerrain(resolution.value, heightmap.value, erosionParams)
    heightmap.value = data.heightmap
    lastErosion.value = erosionParams
    stats.value = data.stats
  })
}

function onSaveSnapshot({ name, noise, erosion }) {
  run('snapshot', async () => {
    await api.saveSnapshot({
      name,
      resolution: resolution.value,
      heightmap: heightmap.value,
      noise,
      erosion
    })
    notice.value = `快照「${name}」已保存`
    await refreshSnapshots()
  })
}

function onLoadSnapshot(name) {
  run('snapshot', async () => {
    const snap = await api.loadSnapshot(name)
    heightmap.value = snap.heightmap
    resolution.value = snap.resolution
    if (snap.noise) lastNoise.value = snap.noise
    if (snap.erosion) lastErosion.value = snap.erosion
    panel.value?.setParams(snap.noise, snap.erosion)
    stats.value = null
    notice.value = `已加载快照「${name}」`
  })
}

async function refreshSnapshots() {
  try {
    snapshots.value = await api.listSnapshots()
  } catch {
    // 列表失败不阻塞主流程
  }
}

onMounted(async () => {
  refreshSnapshots()
  onGenerate({ ...defaultNoise })
})
</script>

<style scoped>
.layout {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
.viewport {
  flex: 1;
  min-width: 0;
  height: 100%;
}
</style>
