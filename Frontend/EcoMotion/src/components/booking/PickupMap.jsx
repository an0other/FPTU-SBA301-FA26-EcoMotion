import { useCallback, useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { vehicleService } from '../../services/vehicleService'

const DEFAULT_CENTER = [10.7769, 106.7009]
const PIN = L.divIcon({
  className: '',
  html: '<div style="width:28px;height:28px;border-radius:50% 50% 50% 0;background:#15803d;border:3px solid white;box-shadow:0 2px 8px #0005;transform:rotate(-45deg)"></div>',
  iconSize: [28, 28], iconAnchor: [14, 28],
})

export function PickupMap({ value, onChange, onError, readOnly = false }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)
  const onChangeRef = useRef(onChange)
  const valueRef = useRef(value)
  const initialValueRef = useRef(value)
  const [query, setQuery] = useState(value?.address ?? '')
  const [suggestions, setSuggestions] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => { onChangeRef.current = onChange }, [onChange])
  useEffect(() => { valueRef.current = value }, [value])

  const selectPoint = useCallback(async (latitude, longitude, knownAddress) => {
    if (readOnly) return
    setLoading(true)
    try {
      const address = knownAddress || await vehicleService.reverseGeocode({ latitude, longitude })
      if (!address) throw new Error('Không tìm được địa chỉ tại điểm này')
      const next = { address, latitude: Number(latitude), longitude: Number(longitude) }
      setQuery(address)
      setSuggestions([])
      onChangeRef.current(next)
    } catch (error) {
      const previous = valueRef.current
      if (previous?.latitude != null && previous?.longitude != null) {
        const previousPoint = [Number(previous.latitude), Number(previous.longitude)]
        markerRef.current?.setLatLng(previousPoint)
        mapRef.current?.setView(previousPoint, Math.max(mapRef.current.getZoom(), 15))
        setQuery(previous.address ?? '')
      }
      onError?.(error.message || 'Không thể xác định điểm đón')
    } finally {
      setLoading(false)
    }
  }, [onError, readOnly])

  const tileErrorReportedRef = useRef(false)

  useEffect(() => {
    const initialValue = initialValueRef.current
    const map = L.map(containerRef.current, { scrollWheelZoom: true })
      .setView(initialValue?.latitude != null ? [initialValue.latitude, initialValue.longitude] : DEFAULT_CENTER, initialValue?.latitude != null ? 16 : 13)
    const primaryTiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    })
    primaryTiles.on('tileerror', () => {
      if (!tileErrorReportedRef.current) {
        tileErrorReportedRef.current = true
        onError?.('Bản đồ tạm thời gián đoạn kết nối máy chủ; điểm đón và địa chỉ đã chọn vẫn được giữ nguyên.')
      }
    })
    primaryTiles.addTo(map)
    if (!readOnly) map.on('click', (event) => selectPoint(event.latlng.lat, event.latlng.lng))
    mapRef.current = map
    return () => { map.remove(); mapRef.current = null; markerRef.current = null }
  }, [onError, readOnly, selectPoint])

  useEffect(() => {
    const map = mapRef.current
    if (!map || value?.latitude == null || value?.longitude == null) return
    const latLng = [Number(value.latitude), Number(value.longitude)]
    if (!markerRef.current) {
      markerRef.current = L.marker(latLng, { icon: PIN, draggable: !readOnly }).addTo(map)
      if (!readOnly) markerRef.current.on('dragend', (event) => {
        const point = event.target.getLatLng()
        selectPoint(point.lat, point.lng)
      })
    } else markerRef.current.setLatLng(latLng)
    map.setView(latLng, Math.max(map.getZoom(), 15))
  }, [readOnly, selectPoint, value?.latitude, value?.longitude])

  useEffect(() => {
    if (readOnly || query.trim().length < 3 || query === value?.address) return undefined
    const timer = window.setTimeout(async () => {
      try {
        setSuggestions(await vehicleService.searchAddress(query.trim()))
      } catch (error) {
        onError?.(error.message || 'Không thể tìm địa chỉ')
      }
    }, 350)
    return () => window.clearTimeout(timer)
  }, [onError, query, readOnly, value?.address])

  const useCurrentLocation = () => {
    if (!navigator.geolocation) return onError?.('Trình duyệt không hỗ trợ định vị')
    setLoading(true)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => selectPoint(coords.latitude, coords.longitude),
      () => { setLoading(false); onError?.('Không thể truy cập vị trí hiện tại') },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  return (
    <div className="space-y-2">
      {!readOnly ? (
        <div className="relative">
          <div className="flex gap-2">
            <input value={query} onChange={(event) => {
              setQuery(event.target.value)
              if (event.target.value.trim().length < 3) setSuggestions([])
            }}
              placeholder="Tìm số nhà, tên đường hoặc địa điểm"
              className="min-w-0 flex-1 rounded-xl border border-[#BEC9C1] px-3 py-2.5 text-sm focus:border-[#15803D] focus:outline-none" />
            <button type="button" onClick={useCurrentLocation}
              className="rounded-xl border border-[#15803D] px-3 text-sm font-semibold text-[#15803D] hover:bg-[#E8F6EE]">
              Vị trí của tôi
            </button>
          </div>
          {suggestions.length ? (
            <div className="absolute z-[1000] mt-1 max-h-48 w-full overflow-auto rounded-xl border bg-white shadow-lg">
              {suggestions.map((item, index) => (
                <button key={`${item.latitude}-${item.longitude}-${index}`} type="button"
                  onClick={() => selectPoint(item.latitude, item.longitude, item.displayName || item.address)}
                  className="block w-full border-b px-3 py-2 text-left text-xs hover:bg-[#E8F6EE]">
                  {item.displayName || item.address}
                </button>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
      <div ref={containerRef} className="h-56 w-full overflow-hidden rounded-xl border border-[#E6EBE8] bg-slate-100" />
      {loading ? <p className="text-xs text-[#15803D]">Đang xác định địa chỉ...</p> : null}
      {value?.address ? <p className="text-xs font-medium text-slate-700">Điểm đã chọn: {value.address}</p> : null}
    </div>
  )
}
