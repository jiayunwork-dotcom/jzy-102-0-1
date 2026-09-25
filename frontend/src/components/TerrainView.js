import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

/**
 * Three.js terrain surface renderer.
 *
 * Responsibilities (rendering only):
 *  - build a planar grid displaced by the height field;
 *  - orbit/pan/zoom camera;
 *  - a directional light whose azimuth/elevation can be changed;
 *  - vertex colors layered by elevation (low vs high) with a separate steep
 *    slope color;
 *  - optional per-step redraw of the current erosion frame.
 */
export class TerrainScene {
  constructor(container) {
    this.container = container
    this.size = 0
    this.heightScale = 14
    this.lightAzimuth = 135 // degrees
    this.lightElevation = 55 // degrees

    this.scene = new THREE.Scene()
    this.scene.background = new THREE.Color(0x0f1420)

    const width = container.clientWidth || 800
    const height = container.clientHeight || 600
    this.camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 2000)
    this.camera.position.set(110, 110, 110)

    this.renderer = new THREE.WebGLRenderer({ antialias: true })
    this.renderer.setPixelRatio(window.devicePixelRatio)
    this.renderer.setSize(width, height)
    container.appendChild(this.renderer.domElement)

    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.dampingFactor = 0.08

    // Lighting: soft ambient fill + the adjustable key directional light.
    this.ambient = new THREE.AmbientLight(0xffffff, 0.45)
    this.scene.add(this.ambient)

    this.sun = new THREE.DirectionalLight(0xffffff, 1.15)
    this.scene.add(this.sun)
    this.scene.add(this.sun.target)

    this.mesh = null

    this._resizeObserver = new ResizeObserver(() => this._resize())
    this._resizeObserver.observe(container)

    this._animate = this._animate.bind(this)
    this._frame = requestAnimationFrame(this._animate)
  }

  setHeightScale(scale) {
    this.heightScale = scale
  }

  setLight(azimuthDeg, elevationDeg) {
    this.lightAzimuth = azimuthDeg
    this.lightElevation = elevationDeg
    this._updateSun()
  }

  _updateSun() {
    const az = THREE.MathUtils.degToRad(this.lightAzimuth)
    const el = THREE.MathUtils.degToRad(this.lightElevation)
    const r = 200
    this.sun.position.set(
      r * Math.cos(el) * Math.cos(az),
      r * Math.sin(el),
      r * Math.cos(el) * Math.sin(az)
    )
    this.sun.target.position.set(0, 0, 0)
  }

  /** Replace the displayed surface with a new size x size height field. */
  setTerrain(heights, size) {
    this.size = size
    if (this.mesh) {
      this.scene.remove(this.mesh)
      this.mesh.geometry.dispose()
      this.mesh.material.dispose()
    }

    const geometry = this._buildGeometry(heights, size)
    const material = new THREE.MeshStandardMaterial({
      vertexColors: true,
      flatShading: false,
      roughness: 0.95,
      metalness: 0.0
    })
    this.mesh = new THREE.Mesh(geometry, material)
    this.scene.add(this.mesh)
    this._updateSun()
  }

  _buildGeometry(heights, size) {
    // Square of side `span`, centered at origin.
    const span = 120
    const geometry = new THREE.PlaneGeometry(span, span, size - 1, size - 1)
    geometry.rotateX(-Math.PI / 2)

    const position = geometry.attributes.position
    const colors = new Float32Array(position.count * 3)

    let min = Infinity
    let max = -Infinity
    for (let i = 0; i < heights.length; i++) {
      if (heights[i] < min) min = heights[i]
      if (heights[i] > max) max = heights[i]
    }
    const range = Math.max(1e-9, max - min)

    for (let i = 0; i < position.count; i++) {
      position.setY(i, (heights[i] - min) * this.heightScale)
    }

    const colorLow = new THREE.Color(0x2e5e8c) // low ground: blue-green water/valley
    const colorHigh = new THREE.Color(0xefe7d2) // high ground: pale ridge/snow
    const colorSteep = new THREE.Color(0x7a5230) // steep slopes: exposed rock/earth
    const tmp = new THREE.Color()
    const steepThreshold = 1.05 // normalized gradient magnitude

    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const i = y * size + x
        const t = (heights[i] - min) / range

        // central-difference gradient in height units, normalized by relief
        const xl = heights[y * size + Math.max(0, x - 1)]
        const xr = heights[y * size + Math.min(size - 1, x + 1)]
        const yd = heights[Math.max(0, y - 1) * size + x]
        const yu = heights[Math.min(size - 1, y + 1) * size + x]
        const gx = (xr - xl) / 2
        const gy = (yu - yd) / 2
        const grad = Math.sqrt(gx * gx + gy * gy) / range * size

        tmp.copy(colorLow).lerp(colorHigh, t)
        if (grad > steepThreshold) {
          const blend = Math.min(1, (grad - steepThreshold) / 1.5)
          tmp.lerp(colorSteep, blend)
        }
        colors[i * 3] = tmp.r
        colors[i * 3 + 1] = tmp.g
        colors[i * 3 + 2] = tmp.b
      }
    }

    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
    geometry.computeVertexNormals()
    return geometry
  }

  resetView() {
    this.camera.position.set(110, 110, 110)
    this.controls.target.set(0, 0, 0)
    this.controls.update()
  }

  _animate() {
    this._frame = requestAnimationFrame(this._animate)
    this.controls.update()
    this.renderer.render(this.scene, this.camera)
  }

  _resize() {
    const width = this.container.clientWidth
    const height = this.container.clientHeight
    if (width === 0 || height === 0) return
    this.camera.aspect = width / height
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(width, height)
  }

  dispose() {
    cancelAnimationFrame(this._frame)
    this._resizeObserver.disconnect()
    this.controls.dispose()
    if (this.mesh) {
      this.mesh.geometry.dispose()
      this.mesh.material.dispose()
    }
    this.renderer.dispose()
    if (this.renderer.domElement.parentElement === this.container) {
      this.container.removeChild(this.renderer.domElement)
    }
  }
}
