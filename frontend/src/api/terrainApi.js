// All HTTP interaction with the backend lives here. The components never call
// fetch() directly, which keeps the networking contract in one place.

const BASE = '/api'

async function request(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  if (!res.ok) {
    let message = `请求失败 (${res.status})`
    try {
      const data = await res.json()
      if (data && data.message) message = data.message
    } catch (_) {
      // non-JSON error body
    }
    throw new Error(message)
  }
  return res.json()
}

export function generateTerrain(noiseParams) {
  return request('/terrain/generate', noiseParams)
}

export function runErosion(payload) {
  return request('/erosion', payload)
}

export function saveSnapshot(payload) {
  return request('/snapshots', payload)
}

export async function listSnapshots() {
  const res = await fetch(BASE + '/snapshots')
  if (!res.ok) throw new Error(`快照列表获取失败 (${res.status})`)
  return res.json()
}

export async function loadSnapshot(name) {
  const res = await fetch(`${BASE}/snapshots/${encodeURIComponent(name)}`)
  if (!res.ok) {
    let message = `快照加载失败 (${res.status})`
    try {
      const data = await res.json()
      if (data && data.message) message = data.message
    } catch (_) {
      // ignore
    }
    throw new Error(message)
  }
  return res.json()
}
