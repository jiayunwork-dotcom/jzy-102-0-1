/**
 * 与后端交互的全部请求封装。后端把参数非法的情况以 400 + {error: 原因}
 * 返回，这里统一把原因抛成 Error 供界面展示。
 */
const BASE = '/api'

async function request(path, options = {}) {
  const res = await fetch(BASE + path, options)
  if (!res.ok) {
    let message = `请求失败（HTTP ${res.status}）`
    try {
      const body = await res.json()
      if (body && body.error) message = body.error
      else if (body && body.message) message = body.message
    } catch {
      // 响应体不是 JSON，保留默认信息
    }
    throw new Error(message)
  }
  return res.json()
}

function post(path, payload) {
  return request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
}

/** 按噪声参数生成一张新高度场。 */
export function generateTerrain(noiseParams) {
  return post('/terrain/generate', noiseParams)
}

/** 在当前高度场基础上追加一轮侵蚀。 */
export function erodeTerrain(resolution, heightmap, erosionParams) {
  return post('/terrain/erode', {
    resolution,
    heightmap,
    erosion: erosionParams
  })
}

export function listSnapshots() {
  return request('/snapshots')
}

export function saveSnapshot(payload) {
  return post('/snapshots', payload)
}

export function loadSnapshot(name) {
  return request('/snapshots/' + encodeURIComponent(name))
}
