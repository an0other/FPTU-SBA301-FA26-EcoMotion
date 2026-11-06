import test from 'node:test'
import assert from 'node:assert/strict'
import {
  INCIDENT_PHOTO_MAX_BYTES,
  createIncidentPhotoUploader,
  normalizeEvidenceUrls,
  sniffImageMime,
  validateIncidentPhoto,
} from '../src/utils/incidentPhotoUpload.js'

const JPEG = [0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46, 0, 1]
const PNG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13]
const WEBP = [0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]
const TEXT = Array.from('hello world, not an image', (c) => c.charCodeAt(0))

const imageFile = (name, type, bytes, extraSize = 0) => {
  const data = new Uint8Array([...bytes, ...new Array(extraSize).fill(0)])
  return new File([data], name, { type })
}
const url = (name) => `https://res.cloudinary.test/ecomotion/${name}.jpg`

// Upload có điều khiển: test quyết định khi nào kết quả trả về.
function controlledUploader() {
  const calls = []
  const upload = (file, onProgress) => new Promise((resolve, reject) => {
    const call = { file, onProgress, resolve, reject }
    calls.push(call)
  })
  return { upload, calls }
}

function makeUploader(overrides = {}) {
  const revoked = []
  let snapshot = []
  const ctl = controlledUploader()
  const uploader = createIncidentPhotoUploader({
    upload: overrides.upload || ctl.upload,
    createPreviewUrl: (file) => `blob:${file.name}`,
    revokePreviewUrl: (u) => revoked.push(u),
    onChange: (items) => { snapshot = items },
    ...overrides,
  })
  return { uploader, calls: ctl.calls, revoked, items: () => snapshot }
}

const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

test('image signatures are recognised and a renamed non-image is rejected', () => {
  assert.equal(sniffImageMime(new Uint8Array(JPEG)), 'image/jpeg')
  assert.equal(sniffImageMime(new Uint8Array(PNG)), 'image/png')
  assert.equal(sniffImageMime(new Uint8Array(WEBP)), 'image/webp')
  assert.equal(sniffImageMime(new Uint8Array(TEXT)), null)
  // Đổi đuôi và Content-Type thành ảnh nhưng nội dung là văn bản: bị từ chối.
  const fake = imageFile('evil.jpg', 'image/jpeg', TEXT)
  assert.equal(validateIncidentPhoto(fake, new Uint8Array(TEXT)).ok, false)
})

test('empty, unsupported, oversized and mismatched files are rejected with a message', () => {
  assert.equal(validateIncidentPhoto(imageFile('a.jpg', 'image/jpeg', []), new Uint8Array([])).ok, false)
  assert.equal(validateIncidentPhoto(imageFile('a.gif', 'image/gif', JPEG), new Uint8Array(JPEG)).ok, false)
  const big = { name: 'big.jpg', type: 'image/jpeg', size: INCIDENT_PHOTO_MAX_BYTES + 1 }
  const bigResult = validateIncidentPhoto(big, new Uint8Array(JPEG))
  assert.equal(bigResult.ok, false)
  assert.match(bigResult.message, /10 MB/)
  assert.equal(validateIncidentPhoto(imageFile('a.png', 'image/png', JPEG), new Uint8Array(JPEG)).ok, false)
  assert.equal(validateIncidentPhoto(imageFile('ok.png', 'image/png', PNG), new Uint8Array(PNG)).ok, true)
})

test('evidence URL normalisation accepts arrays and legacy strings and drops untrusted values', () => {
  assert.deepEqual(normalizeEvidenceUrls(null), [])
  assert.deepEqual(normalizeEvidenceUrls(undefined), [])
  assert.deepEqual(normalizeEvidenceUrls([url('a'), url('a'), '  ', 'http://insecure/x.jpg', 'javascript:alert(1)']), [url('a')])
  assert.deepEqual(normalizeEvidenceUrls(url('old')), [url('old')])
  assert.deepEqual(normalizeEvidenceUrls(`${url('x')}, ${url('y')}`), [url('x'), url('y')])
  assert.deepEqual(normalizeEvidenceUrls(42), [])
})

test('no photo selected: submission is allowed and no URL is sent', () => {
  const { uploader } = makeUploader()
  assert.equal(uploader.canSubmit(), true)
  assert.deepEqual(uploader.evidenceUrls(), [])
})

test('one valid photo uploads, becomes ready, and its URL is the one sent', async () => {
  const { uploader, calls, items } = makeUploader()
  const pending = uploader.add([imageFile('a.jpg', 'image/jpeg', JPEG)])
  await flush()
  assert.equal(items()[0].status, 'uploading')
  assert.equal(uploader.canSubmit(), false)
  calls[0].resolve({ imageUrl: url('a') })
  await pending
  assert.equal(items()[0].status, 'ready')
  assert.equal(uploader.canSubmit(), true)
  assert.deepEqual(uploader.evidenceUrls(), [url('a')])
})

test('several valid photos keep the order they were selected in', async () => {
  const { uploader, calls } = makeUploader()
  const pending = uploader.add([imageFile('1.jpg', 'image/jpeg', JPEG), imageFile('2.png', 'image/png', PNG)])
  await flush()
  calls[1].resolve({ imageUrl: url('second') })
  calls[0].resolve({ imageUrl: url('first') })
  await pending
  assert.deepEqual(uploader.evidenceUrls(), [url('first'), url('second')])
})

test('an invalid file is shown as an error and blocks submission until removed', async () => {
  const { uploader, calls, items } = makeUploader()
  await uploader.add([imageFile('fake.jpg', 'image/jpeg', TEXT)])
  assert.equal(calls.length, 0, 'an invalid file must never be uploaded')
  assert.equal(items()[0].status, 'error')
  assert.equal(uploader.canSubmit(), false)
  uploader.remove(items()[0].id)
  assert.equal(uploader.canSubmit(), true)
})

test('a failed upload can be retried and then becomes ready', async () => {
  const { uploader, calls, items } = makeUploader()
  const pending = uploader.add([imageFile('a.jpg', 'image/jpeg', JPEG)])
  await flush()
  calls[0].reject(Object.assign(new Error('network'), { response: { data: { message: 'Mất kết nối' } } }))
  await pending
  assert.equal(items()[0].status, 'error')
  assert.equal(items()[0].error, 'Mất kết nối')
  assert.equal(uploader.canSubmit(), false)
  const retry = uploader.retry(items()[0].id)
  await flush()
  calls[1].resolve({ imageUrl: url('retried') })
  await retry
  assert.equal(items()[0].status, 'ready')
  assert.deepEqual(uploader.evidenceUrls(), [url('retried')])
})

test('retry is a no-op for an item that was never uploadable', async () => {
  const { uploader, calls, items } = makeUploader()
  await uploader.add([imageFile('fake.jpg', 'image/jpeg', TEXT)])
  await uploader.retry(items()[0].id)
  assert.equal(calls.length, 0)
})

test('removing a photo while it uploads drops its late result', async () => {
  const { uploader, calls, items, revoked } = makeUploader()
  const pending = uploader.add([imageFile('a.jpg', 'image/jpeg', JPEG)])
  await flush()
  const id = items()[0].id
  uploader.remove(id)
  calls[0].resolve({ imageUrl: url('late') })
  await pending
  assert.equal(items().length, 0)
  assert.deepEqual(uploader.evidenceUrls(), [])
  assert.equal(revoked.length, 1, 'the preview URL is released')
})

test('a retry supersedes an earlier attempt for the same photo', async () => {
  const { uploader, calls, items } = makeUploader()
  const first = uploader.add([imageFile('a.jpg', 'image/jpeg', JPEG)])
  await flush()
  calls[0].reject(new Error('timeout'))
  await first
  const retry = uploader.retry(items()[0].id)
  await flush()
  calls[1].resolve({ imageUrl: url('second-attempt') })
  await retry
  assert.deepEqual(uploader.evidenceUrls(), [url('second-attempt')])
})

test('a server response without a trusted https URL is an error, never a sent URL', async () => {
  const { uploader, calls, items } = makeUploader()
  const pending = uploader.add([imageFile('a.jpg', 'image/jpeg', JPEG)])
  await flush()
  calls[0].resolve({ imageUrl: 'http://evil.test/a.jpg' })
  await pending
  assert.equal(items()[0].status, 'error')
  assert.deepEqual(uploader.evidenceUrls(), [])
  assert.equal(uploader.canSubmit(), false)
})

test('the photo count is capped and the extra selection is reported, not silently dropped', async () => {
  const { uploader, items } = makeUploader({ maxCount: 2, upload: async () => ({ imageUrl: url('x') }) })
  await uploader.add([imageFile('1.jpg', 'image/jpeg', JPEG), imageFile('2.jpg', 'image/jpeg', JPEG), imageFile('3.jpg', 'image/jpeg', JPEG)])
  const errors = items().filter((item) => item.status === 'error');
  assert.equal(items().length, 3)
  assert.equal(errors.length, 1)
  assert.match(errors[0].error, /tối đa 2 ảnh/)
})

test('upload progress is reported on the item while it uploads', async () => {
  const { uploader, calls, items } = makeUploader()
  const pending = uploader.add([imageFile('a.jpg', 'image/jpeg', JPEG)])
  await flush()
  calls[0].onProgress(42)
  assert.equal(items()[0].progress, 42)
  calls[0].resolve({ imageUrl: url('a') })
  await pending
})

test('dispose releases every preview URL and clears the list', async () => {
  const { uploader, calls, items, revoked } = makeUploader()
  const pending = uploader.add([imageFile('1.jpg', 'image/jpeg', JPEG), imageFile('2.jpg', 'image/jpeg', JPEG)])
  await flush()
  calls[0].resolve({ imageUrl: url('1') })
  calls[1].resolve({ imageUrl: url('2') })
  await pending
  uploader.dispose()
  assert.equal(items().length, 0)
  assert.equal(revoked.length, 2)
  assert.deepEqual(uploader.evidenceUrls(), [])
})

test('the trip context given when a photo is added is passed to every upload, including retries', async () => {
  const seen = []
  const { uploader, items } = makeUploader({
    upload: (file, onProgress, context) => {
      seen.push(context)
      return seen.length === 1
        ? Promise.reject(new Error('lỗi mạng'))
        : Promise.resolve({ imageUrl: url('ok') })
    },
  })
  await uploader.add([imageFile('a.jpg', 'image/jpeg', JPEG)], 'BK-TRIP-7')
  assert.equal(items()[0].status, 'error')
  await uploader.retry(items()[0].id)
  assert.deepEqual(seen, ['BK-TRIP-7', 'BK-TRIP-7'])
  assert.deepEqual(uploader.evidenceUrls(), [url('ok')])
})
