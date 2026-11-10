import test from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

// apiClient đã có baseURL '/api'; mọi service gọi đường dẫn không có '/v1' để thống nhất một tiền tố.
function files(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? files(path) : /\.(js|jsx)$/.test(name) ? [path] : []
  })
}

test('frontend không gọi API qua tiền tố /v1 (chỉ dùng /api)', () => {
  const offenders = files('src').filter((path) => /['`]\/v1\/|\/api\/v1\//.test(readFileSync(path, 'utf8')))
  assert.deepEqual(offenders, [])
})
