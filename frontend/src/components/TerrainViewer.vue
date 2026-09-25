<template>
  <div ref="container" class="terrain-viewer">
    <div v-if="!heightmap" class="viewer-placeholder">暂无地形数据 — 请在左侧生成</div>
    <div class="viewer-hint">左键旋转 · 滚轮缩放 · 右键平移</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'

const props = defineProps({
  // 长度为 resolution^2 的高度数组（行优先），null 表示还没有数据
  heightmap: { type: Array, default: null },
  resolution: { type: Number, default: 0 },
  // 方向光角度（度）
  lightAzimuth: { type: Number, default: 45 },
  lightElevation: { type: Number, default: 50 }
})

const WORLD_SIZE = 100
const HEIGHT_SCALE = 26

const container = ref(null)
let renderer
let scene
let camera
let controls
let dirLight
let terrain = null
let animationId = 0
let resizeObserver = null

// 分层着色：低处 / 高处 / 陡坡
const COLOR_LOW = new THREE.Color('#2f7a3d')   // 低地：绿
const COLOR_HIGH = new THREE.Color('#f2ede0')  // 高处：近白
const COLOR_ROCK = new THREE.Color('#7a6248')  // 陡坡：岩褐
const tmpColor = new THREE.Color()

function init() {
  const el = container.value
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.setSize(el.clientWidth, el.clientHeight)
  el.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  scene.background = new THREE.Color('#10151c')
  scene.fog = new THREE.Fog('#10151c', 220, 520)

  camera = new THREE.PerspectiveCamera(50, el.clientWidth / el.clientHeight, 0.1, 2000)
  camera.position.set(0, 70, 110)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.target.set(0, 8, 0)
  controls.maxPolarAngle = Math.PI * 0.49

  scene.add(new THREE.AmbientLight('#ffffff', 0.45))
  dirLight = new THREE.DirectionalLight('#fff4e0', 1.6)
  scene.add(dirLight)
  updateLight()

  const grid = new THREE.GridHelper(WORLD_SIZE, 20, '#2a3442', '#1d2530')
  grid.position.y = -0.05
  scene.add(grid)

  resizeObserver = new ResizeObserver(handleResize)
  resizeObserver.observe(el)

  animate()
}

function handleResize() {
  const el = container.value
  if (!el || !renderer) return
  camera.aspect = el.clientWidth / el.clientHeight
  camera.updateProjectionMatrix()
  renderer.setSize(el.clientWidth, el.clientHeight)
}

function updateLight() {
  if (!dirLight) return
  const az = THREE.MathUtils.degToRad(props.lightAzimuth)
  const el = THREE.MathUtils.degToRad(props.lightElevation)
  const r = 160
  dirLight.position.set(
    r * Math.cos(el) * Math.sin(az),
    r * Math.sin(el),
    r * Math.cos(el) * Math.cos(az)
  )
}

/** 高度场变化时重建地形网格与顶点着色。 */
function rebuildTerrain() {
  if (terrain) {
    scene.remove(terrain)
    terrain.geometry.dispose()
    terrain.material.dispose()
    terrain = null
  }
  const h = props.heightmap
  const res = props.resolution
  if (!h || !res) return

  let min = Infinity
  let max = -Infinity
  for (const v of h) {
    if (v < min) min = v
    if (v > max) max = v
  }
  const range = Math.max(max - min, 1e-9)

  const geometry = new THREE.PlaneGeometry(WORLD_SIZE, WORLD_SIZE, res - 1, res - 1)
  geometry.rotateX(-Math.PI / 2)
  const pos = geometry.attributes.position
  // PlaneGeometry 顶点按行优先排列，与高度场 index = y * res + x 一一对应
  for (let i = 0; i < pos.count; i++) {
    pos.setY(i, ((h[i] - min) / range) * HEIGHT_SCALE)
  }
  geometry.computeVertexNormals()

  const normals = geometry.attributes.normal
  const colors = new Float32Array(pos.count * 3)
  for (let i = 0; i < pos.count; i++) {
    const t = (h[i] - min) / range
    // 先按高度在低色与高色之间插值
    tmpColor.copy(COLOR_LOW).lerp(COLOR_HIGH, t * t * (3 - 2 * t))
    // 再按坡度混入岩色：法线越偏离竖直方向坡越陡
    const slope = 1 - normals.getY(i)
    const rockMix = THREE.MathUtils.smoothstep(slope, 0.18, 0.5)
    tmpColor.lerp(COLOR_ROCK, rockMix * 0.85)
    colors[i * 3] = tmpColor.r
    colors[i * 3 + 1] = tmpColor.g
    colors[i * 3 + 2] = tmpColor.b
  }
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

  const material = new THREE.MeshStandardMaterial({
    vertexColors: true,
    roughness: 0.95,
    metalness: 0.0
  })
  terrain = new THREE.Mesh(geometry, material)
  scene.add(terrain)
}

function animate() {
  animationId = requestAnimationFrame(animate)
  controls.update()
  renderer.render(scene, camera)
}

onMounted(init)

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
  resizeObserver?.disconnect()
  controls?.dispose()
  if (terrain) {
    terrain.geometry.dispose()
    terrain.material.dispose()
  }
  renderer?.dispose()
})

watch(() => [props.heightmap, props.resolution], rebuildTerrain)
watch(() => [props.lightAzimuth, props.lightElevation], updateLight)
</script>

<style scoped>
.terrain-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.viewer-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7c8aa0;
  font-size: 15px;
}
.viewer-hint {
  position: absolute;
  right: 12px;
  bottom: 10px;
  color: #5c6b82;
  font-size: 12px;
  pointer-events: none;
}
</style>
