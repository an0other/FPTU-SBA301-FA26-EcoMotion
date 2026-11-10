import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { attachVietmap } from './vietmapBase'

const HCM_CENTER = [10.7769, 106.7009]

function hasCoords(point) {
  return point && point.latitude != null && point.longitude != null
    && Number.isFinite(Number(point.latitude)) && Number.isFinite(Number(point.longitude))
}

/**
 * Map of the vehicle's last known position and the pickup point. Pure display: no route request.
 * `stale` draws the vehicle marker grey so the user can see the position may be out of date.
 */
export default function LiveVehicleMap({ vehicle, pickup, stale = false, height = '320px' }) {
  const container = useRef(null)
  const map = useRef(null)
  const layer = useRef(null)
  const fitted = useRef(false)
  const [error, setError] = useState('')

  // Create the map once and detach it on unmount.
  useEffect(() => {
    const instance = L.map(container.current, { scrollWheelZoom: false }).setView(HCM_CENTER, 13)
    map.current = instance
    layer.current = L.layerGroup().addTo(instance)
    const detach = attachVietmap(instance, { onError: setError, onLoad: () => setError('') })
    return () => {
      detach()
      instance.remove()
      map.current = null
      layer.current = null
    }
  }, [])

  const vLat = vehicle?.latitude
  const vLon = vehicle?.longitude
  const pLat = pickup?.latitude
  const pLon = pickup?.longitude

  // Redraw markers whenever the positions or the signal state change.
  useEffect(() => {
    const instance = map.current
    const group = layer.current
    if (!instance || !group) return
    group.clearLayers()

    const hasPickup = hasCoords({ latitude: pLat, longitude: pLon })
    const hasVehicle = hasCoords({ latitude: vLat, longitude: vLon })
    const points = []
    if (hasPickup) {
      const latLng = [Number(pLat), Number(pLon)]
      points.push(latLng)
      L.circleMarker(latLng, { radius: 8, color: '#047857', fillColor: '#10b981', fillOpacity: 0.9, weight: 2 })
        .bindTooltip('Điểm đón')
        .addTo(group)
    }
    if (hasVehicle) {
      const latLng = [Number(vLat), Number(vLon)]
      points.push(latLng)
      L.circleMarker(latLng, {
        radius: 9,
        color: stale ? '#64748b' : '#1d4ed8',
        fillColor: stale ? '#94a3b8' : '#3b82f6',
        fillOpacity: 0.9,
        weight: 2,
        dashArray: stale ? '4 4' : null,
      }).bindTooltip(stale ? 'Vị trí xe (có thể đã cũ)' : 'Vị trí xe').addTo(group)
    }

    if (points.length === 0) {
      instance.setView(HCM_CENTER, 13)
    } else if (points.length === 1) {
      instance.setView(points[0], 15)
    } else if (!fitted.current) {
      instance.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 16 })
    }
    if (points.length === 2) fitted.current = true
  }, [vLat, vLon, pLat, pLon, stale])

  return (
    <div className="space-y-1.5">
      <div
        ref={container}
        style={{ height }}
        className="leaflet-map-shell w-full overflow-hidden rounded-xl border border-slate-200"
        aria-label="Bản đồ vị trí xe và điểm đón"
      />
      {error ? <p role="status" className="text-xs text-amber-900">{error}</p> : null}
    </div>
  )
}
