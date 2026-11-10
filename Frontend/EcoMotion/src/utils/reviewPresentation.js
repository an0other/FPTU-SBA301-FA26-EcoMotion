export function reviewPhotos(item) {
  const urls = Array.isArray(item?.mediaUrls) ? item.mediaUrls : []
  return [...new Set(urls.filter((url) => typeof url === 'string').map((url) => url.trim())
    .filter((url) => /^https?:\/\/[^\s]+$/i.test(url) || /^\/(?!\/)[^\s]+$/.test(url)))]
}

const timestamp = (item) => Date.parse(item.createdAt) || 0

export function selectReviews(items = [], { stars = 0, photos = false, query = '', order = 'newest' } = {}) {
  const needle = query.trim().toLocaleLowerCase('vi-VN')
  return items.filter((item) => (!stars || Number(item.rating) === Number(stars))
    && (!photos || reviewPhotos(item).length > 0)
    && (!needle || `${item.comment || ''} ${item.reviewerName || ''}`.toLocaleLowerCase('vi-VN').includes(needle)))
    .slice().sort((a, b) => {
      const recent = timestamp(b) - timestamp(a) || Number(b.id) - Number(a.id)
      if (order === 'oldest') return -recent
      if (order === 'highest') return Number(b.rating) - Number(a.rating) || recent
      if (order === 'lowest') return Number(a.rating) - Number(b.rating) || recent
      return recent
    })
}

export function reviewGallery(items = []) {
  const seen = new Set()
  return items.flatMap((item) => reviewPhotos(item).filter((url) => {
    if (seen.has(url)) return false
    seen.add(url)
    return true
  }).map((url) => ({ url, reviewerName: item.reviewerName || 'Khách thuê', reviewId: item.id })))
}

export function initials(name = '') {
  return name.trim().split(/\s+/).filter(Boolean).slice(-2).map((word) => word[0]).join('').toUpperCase() || 'KH'
}
