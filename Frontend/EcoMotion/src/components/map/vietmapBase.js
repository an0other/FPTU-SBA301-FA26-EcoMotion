import L from 'leaflet'
import '@vietmap/vietmap-gl-js/dist/vietmap-gl.css'

export const hasVietmapKey = Boolean(import.meta.env.VITE_VIETMAP_TILEMAP_KEY?.trim())
export function attachVietmap(map, { onLoad = () => {}, onError = () => {} } = {}) {
  let cancelled = false; let layer; let timeout
  map.__vietmapReady = false
  const fail = () => { if (!cancelled) { map.__vietmapReady = false; onError('Bản đồ VIETMAP hiện chưa khả dụng. Bạn vẫn có thể xem thông tin địa điểm hoặc thử lại sau.'); } }
  if (!hasVietmapKey || (import.meta.env.PROD && import.meta.env.VITE_VIETMAP_STYLE_VERIFIED !== 'true')) { queueMicrotask(fail); return () => { cancelled = true } }
  const attribution = '© <a href="https://maps.vietmap.vn/" target="_blank" rel="noopener noreferrer">VIETMAP</a>'
  map.attributionControl?.addAttribution(attribution)
  import('@vietmap/vietmap-gl-leaflet').then(() => {
    if (cancelled) return
    try {
      map.setMinZoom(1)
      map.setMaxBounds([[-85, -180], [85, 180]])
      layer = L.vietmapGL({ style: `https://maps.vietmap.vn/maps/styles/tm/style.json?apikey=${encodeURIComponent(import.meta.env.VITE_VIETMAP_TILEMAP_KEY)}` }).addTo(map)
      const gl = layer.getVietmapMap()
      timeout = setTimeout(fail, 10000)
      gl.on('load', () => { if (!cancelled) { clearTimeout(timeout); map.__vietmapReady = true; onLoad() } })
      gl.on('error', fail)
    } catch { fail() }
  }).catch(fail)
  return () => { cancelled = true; map.attributionControl?.removeAttribution(attribution); clearTimeout(timeout); if (layer && map.hasLayer(layer)) map.removeLayer(layer) }
}
