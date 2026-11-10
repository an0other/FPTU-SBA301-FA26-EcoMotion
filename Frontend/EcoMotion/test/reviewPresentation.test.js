import test from 'node:test'
import assert from 'node:assert/strict'
import { selectReviews, reviewPhotos, reviewGallery, initials } from '../src/utils/reviewPresentation.js'

const items = [
  { id: 1, rating: 5, comment: 'Đón đúng giờ', reviewerName: 'An', createdAt: '2026-01-01', mediaUrls: ['/images/a.png', '/images/a.png'] },
  { id: 2, rating: 3, comment: 'Chờ hơi lâu', reviewerName: 'Bình', createdAt: '2026-03-01', mediaUrls: [] },
  { id: 3, rating: 5, comment: 'Lái xe an toàn', reviewerName: 'Chi', createdAt: '2026-02-01', mediaUrls: ['/images/a.png', 'https://example.com/b.png'] },
]
test('review filters combine rating, photos and Vietnamese text without mutating the response', () => {
  const before = JSON.stringify(items)
  assert.deepEqual(selectReviews(items, { stars: 5, photos: true, query: 'AN TOÀN' }).map((r) => r.id), [3])
  assert.equal(JSON.stringify(items), before)
  assert.deepEqual(selectReviews(items, { stars: 1 }), [])
})
test('review sorting is deterministic for dates and rating ties', () => {
  assert.deepEqual(selectReviews(items).map((r) => r.id), [2, 3, 1])
  assert.deepEqual(selectReviews(items, { order: 'oldest' }).map((r) => r.id), [1, 3, 2])
  assert.deepEqual(selectReviews(items, { order: 'highest' }).map((r) => r.id), [3, 1, 2])
  assert.deepEqual(selectReviews(items, { order: 'lowest' }).map((r) => r.id), [2, 3, 1])
})
test('photos reject unsafe schemes and deduplicate within reviews and the gallery', () => {
  assert.deepEqual(reviewPhotos({ mediaUrls: ['javascript:alert(1)', '//external.test/a', 'data:image/png,x', ' /images/a.png ', null] }), ['/images/a.png'])
  assert.equal(reviewGallery(items).length, 2)
  assert.equal(reviewGallery(items)[1].reviewId, 3)
  assert.deepEqual(reviewPhotos(null), [])
})
test('empty data and masked names have usable fallbacks', () => {
  assert.deepEqual(selectReviews(), [])
  assert.deepEqual(reviewGallery(), [])
  assert.equal(initials('Trương Hùng Cường'), 'HC')
  assert.equal(initials(''), 'KH')
})
