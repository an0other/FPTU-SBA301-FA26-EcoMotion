import test from 'node:test'
import assert from 'node:assert/strict'
import { formatAdminRating } from '../src/utils/adminRating.js'

test('formatAdminRating returns null when ratingCount is 0 or missing even if ratingAvg is non-zero', () => {
  assert.equal(formatAdminRating(0, 0), null)
  assert.equal(formatAdminRating(5, 0), null)
  assert.equal(formatAdminRating(null, null), null)
  assert.equal(formatAdminRating(undefined, undefined), null)
})

test('formatAdminRating formats star average with fixed 1 decimal when ratingCount > 0', () => {
  assert.equal(formatAdminRating(4.75, 12, 'lượt'), '★ 4.8 (12 lượt)')
  assert.equal(formatAdminRating(5, 3, 'đánh giá'), '★ 5.0 (3 đánh giá)')
})

