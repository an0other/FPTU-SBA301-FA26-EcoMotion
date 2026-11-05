import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { attachVietmap } from './vietmapBase'
import { locationService } from '../../services/locationService'

export default function RoadRouteMap({ from, to, height = '360px', interactive = true }) {
  const routeLayers = useRef(null); const routeDestination = useRef(null)
  const container = useRef(null); const map = useRef(null); const layers = useRef(null); const lastRequest = useRef(0)
  const [error, setError] = useState(''); const [routeError, setRouteError] = useState(''); const [routeInfo, setRouteInfo] = useState(null)
  const fromLat = from?.latitude; const fromLon = from?.longitude; const toLat = to?.latitude; const toLon = to?.longitude
  useEffect(() => {
    const instance = L.map(container.current, { dragging: interactive, scrollWheelZoom: interactive }).setView([10.7769,106.7009], 13)
    map.current = instance; layers.current = L.layerGroup().addTo(instance); routeLayers.current = L.layerGroup().addTo(instance)
    const detach = attachVietmap(instance, { onError: setError, onLoad: () => setError('') })
    return () => { detach(); instance.remove(); map.current = null }
  }, [interactive])
  useEffect(() => {
    const group = layers.current; const instance = map.current
    if (!group || !instance) return
    group.clearLayers()
    const destination = `${toLat},${toLon}`
    if (routeDestination.current !== destination || fromLat == null || fromLon == null) {
      routeLayers.current.clearLayers(); setRouteInfo(null); routeDestination.current = destination
    }
    setRouteError('')
    const points = []
    for (const [point, label] of [[{ latitude: fromLat, longitude: fromLon }, 'Vị trí xe'], [{ latitude: toLat, longitude: toLon }, 'Điểm đón']]) {
      if (point?.latitude == null || point?.longitude == null) continue
      const latLng = [Number(point.latitude), Number(point.longitude)]; points.push(latLng)
      L.circleMarker(latLng, { radius: 8, color: label === 'Vị trí xe' ? '#1d4ed8' : '#047857' }).bindTooltip(label).addTo(group)
    }
    if (points.length && routeLayers.current.getLayers().length === 0) instance.fitBounds(L.latLngBounds(points), { padding: [28,28], maxZoom: 16 })
    if (points.length !== 2) return
    let active = true
    const timer = setTimeout(async () => {
      lastRequest.current = Date.now()
      try {
        const result = await locationService.route({ fromLatitude: points[0][0], fromLongitude: points[0][1], toLatitude: points[1][0], toLongitude: points[1][1] })
        if (active) {
          routeLayers.current.clearLayers()
          const line = L.polyline(result.coordinates.map(([lon,lat]) => [lat,lon]), { color: '#047857', weight: 4 }).addTo(routeLayers.current)
          instance.fitBounds(line.getBounds(), { padding: [28, 28], maxZoom: 16 }); setRouteInfo({ ...result, displayedAt: new Date().toLocaleTimeString('vi-VN') })
        }
      } catch { if (active) { routeLayers.current.clearLayers(); setRouteInfo(null); setRouteError('Chưa có tuyến ô tô khả dụng. Không ước lượng bằng đường chim bay.') } }
    }, Math.max(0, lastRequest.current + 30000 - Date.now()))
    return () => { active = false; clearTimeout(timer) }
  }, [fromLat, fromLon, toLat, toLon, interactive])
  return <div className="space-y-2"><div ref={container} style={{ height }} className="w-full rounded-xl border border-slate-200" aria-label="Bản đồ tuyến ô tô VIETMAP" />
    {error && <p role="status" className="text-xs text-amber-900">{error}</p>}
    {routeError && <p role="status" className="text-xs text-amber-900">{routeError}</p>}
    {routeInfo && <p className="text-xs text-slate-700">Tuyến tham khảo VIETMAP: {(routeInfo.distanceMeters / 1000).toFixed(2)} km · khoảng {Math.ceil(routeInfo.durationSeconds / 60)} phút (cập nhật {routeInfo.displayedAt}). Không thay đổi phí đã lưu của đơn.</p>}
  </div>
}
