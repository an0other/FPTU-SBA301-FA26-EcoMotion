import { useState, useEffect, useRef, useMemo } from 'react'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

function lon2tile(lon, zoom) {
  return Math.floor(((lon + 180) / 360) * Math.pow(2, zoom))
}

function lat2tile(lat, zoom) {
  return Math.floor(
    ((1 -
      Math.log(
        Math.tan((lat * Math.PI) / 180) + 1 / Math.cos((lat * Math.PI) / 180)
      ) /
        Math.PI) /
      2) *
      Math.pow(2, zoom)
  )
}

function tile2lon(x, zoom) {
  return (x / Math.pow(2, zoom)) * 360 - 180
}

function tile2lat(y, zoom) {
  const n = Math.PI - (2 * Math.PI * y) / Math.pow(2, zoom)
  return (180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)))
}

export default function LiveMapViewer({
  vehicleLocation, // { latitude, longitude, accuracyMeters, receivedAt, isStale, trackingState }
  pickupLocation, // { latitude, longitude, address }
  vehicleInfo, // { licensePlate, name, color }
  height = '420px',
  interactive = true,
}) {
  const [zoom, setZoom] = useState(15)
  const [center, setCenter] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const dragStartRef = useRef({ x: 0, y: 0 })
  const containerRef = useRef(null)

  // Derive initial or updated center
  useEffect(() => {
    if (vehicleLocation?.latitude && vehicleLocation?.longitude) {
      setCenter({
        lat: Number(vehicleLocation.latitude),
        lon: Number(vehicleLocation.longitude),
      })
    } else if (pickupLocation?.latitude && pickupLocation?.longitude) {
      setCenter({
        lat: Number(pickupLocation.latitude),
        lon: Number(pickupLocation.longitude),
      })
    } else {
      // Default to Ho Chi Minh City Center
      setCenter({ lat: 10.776889, lon: 106.700806 })
    }
  }, [
    vehicleLocation?.latitude,
    vehicleLocation?.longitude,
    pickupLocation?.latitude,
    pickupLocation?.longitude,
  ])

  const trackingState = vehicleLocation?.trackingState || 'UNAVAILABLE'
  const isStale = vehicleLocation?.isStale ?? true

  const stateConfig = useMemo(() => {
    if (!vehicleLocation?.latitude || trackingState === 'UNAVAILABLE') {
      return {
        label: 'Chưa có vị trí',
        badgeClass: 'bg-slate-100 text-slate-700 border-slate-200',
        dotClass: 'bg-slate-400',
      }
    }
    if (trackingState === 'LIVE' && !isStale) {
      return {
        label: 'Đang theo dõi trực tiếp',
        badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200 animate-pulse',
        dotClass: 'bg-emerald-500',
      }
    }
    if (trackingState === 'STALE' || isStale) {
      return {
        label: 'Tín hiệu GPS bị trễ / Mất kết nối',
        badgeClass: 'bg-amber-50 text-amber-700 border-amber-200',
        dotClass: 'bg-amber-500',
      }
    }
    return {
      label: 'Ngoại tuyến',
      badgeClass: 'bg-slate-100 text-slate-600 border-slate-200',
      dotClass: 'bg-slate-400',
    }
  }, [trackingState, isStale, vehicleLocation?.latitude])

  // Center coordinate math
  const effectiveCenter = center || { lat: 10.776889, lon: 106.700806 }

  // Pan / Drag handlers
  const handleMouseDown = (e) => {
    if (!interactive) return
    setIsDragging(true)
    dragStartRef.current = { x: e.clientX, y: e.clientY }
  }

  const handleMouseMove = (e) => {
    if (!isDragging || !interactive) return
    const dx = e.clientX - dragStartRef.current.x
    const dy = e.clientY - dragStartRef.current.y
    dragStartRef.current = { x: e.clientX, y: e.clientY }

    const tileSize = 256
    const tilesCount = Math.pow(2, zoom)
    const degPerPixelLon = 360 / (tileSize * tilesCount)
    const degPerPixelLat = 170 / (tileSize * tilesCount)

    setCenter((prev) => ({
      lat: Math.min(85, Math.max(-85, prev.lat + dy * degPerPixelLat)),
      lon: ((prev.lon - dx * degPerPixelLon + 540) % 360) - 180,
    }))
  }

  const handleMouseUp = () => {
    setIsDragging(false)
  }

  // Calculate tiles to render around center
  const centerTileX = lon2tile(effectiveCenter.lon, zoom)
  const centerTileY = lat2tile(effectiveCenter.lat, zoom)

  const tiles = []
  for (let x = centerTileX - 1; x <= centerTileX + 1; x++) {
    for (let y = centerTileY - 1; y <= centerTileY + 1; y++) {
      tiles.push({ x, y })
    }
  }

  // Calculate pixel position of a coordinate relative to center
  const getMarkerPixelOffset = (lat, lon) => {
    const tileSize = 256
    const tilesCount = Math.pow(2, zoom)

    const centerPxX = ((effectiveCenter.lon + 180) / 360) * (tileSize * tilesCount)
    const centerPxY =
      ((1 -
        Math.log(
          Math.tan((effectiveCenter.lat * Math.PI) / 180) +
            1 / Math.cos((effectiveCenter.lat * Math.PI) / 180)
        ) /
          Math.PI) /
        2) *
      (tileSize * tilesCount)

    const ptPxX = ((lon + 180) / 360) * (tileSize * tilesCount)
    const ptPxY =
      ((1 -
        Math.log(
          Math.tan((lat * Math.PI) / 180) +
            1 / Math.cos((lat * Math.PI) / 180)
        ) /
          Math.PI) /
        2) *
      (tileSize * tilesCount)

    return {
      x: ptPxX - centerPxX,
      y: ptPxY - centerPxY,
    }
  }

  const vehicleOffset =
    vehicleLocation?.latitude && vehicleLocation?.longitude
      ? getMarkerPixelOffset(
          Number(vehicleLocation.latitude),
          Number(vehicleLocation.longitude)
        )
      : null

  const pickupOffset =
    pickupLocation?.latitude && pickupLocation?.longitude
      ? getMarkerPixelOffset(
          Number(pickupLocation.latitude),
          Number(pickupLocation.longitude)
        )
      : null

  const navUrl = vehicleLocation?.latitude
    ? `https://www.google.com/maps/dir/?api=1&destination=${vehicleLocation.latitude},${vehicleLocation.longitude}`
    : pickupLocation?.latitude
    ? `https://www.google.com/maps/dir/?api=1&destination=${pickupLocation.latitude},${pickupLocation.longitude}`
    : null

  return (
    <div className="relative overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 shadow-xs select-none">
      {/* Top Overlay: Status & Timestamps */}
      <div className="absolute top-3 left-3 right-3 z-10 flex flex-wrap items-center justify-between gap-2 pointer-events-none">
        <div className="flex items-center gap-2 pointer-events-auto">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold shadow-xs backdrop-blur-md ${stateConfig.badgeClass}`}
          >
            <span className={`h-2 w-2 rounded-full ${stateConfig.dotClass}`} />
            {stateConfig.label}
          </span>

          {vehicleLocation?.receivedAt && (
            <span className="hidden sm:inline-flex items-center gap-1 rounded-full bg-white/90 px-2.5 py-1 text-[11px] font-medium text-slate-700 shadow-xs border border-slate-200">
              <Icon name="history" className="text-[13px] text-slate-400" />
              <span>
                Cập nhật:{' '}
                {new Date(vehicleLocation.receivedAt).toLocaleTimeString('vi-VN')}
              </span>
            </span>
          )}
        </div>

        {navUrl && (
          <a
            href={navUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="pointer-events-auto inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white/95 px-3 py-1.5 text-xs font-bold text-teal-800 shadow-xs backdrop-blur-md transition hover:bg-white hover:text-teal-900"
          >
            <Icon name="navigation" className="text-[16px] text-teal-600" />
            <span>Mở chỉ đường ngoài</span>
          </a>
        )}
      </div>

      {/* Interactive Map Canvas Container */}
      <div
        ref={containerRef}
        style={{ height }}
        className="relative w-full cursor-grab active:cursor-grabbing overflow-hidden"
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        {/* Slippy Tile Container */}
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="relative w-0 h-0">
            {tiles.map(({ x, y }) => {
              const tileCenterLon = tile2lon(x + 0.5, zoom)
              const tileCenterLat = tile2lat(y + 0.5, zoom)
              const offset = getMarkerPixelOffset(tileCenterLat, tileCenterLon)

              return (
                <img
                  key={`${zoom}-${x}-${y}`}
                  src={`https://tile.openstreetmap.org/${zoom}/${x}/${y}.png`}
                  alt=""
                  loading="lazy"
                  className="absolute max-w-none transition-opacity duration-300 pointer-events-none"
                  style={{
                    width: '256px',
                    height: '256px',
                    left: `${offset.x - 128}px`,
                    top: `${offset.y - 128}px`,
                  }}
                />
              )
            })}

            {/* Pickup Marker */}
            {pickupOffset && (
              <div
                className="absolute z-20 flex flex-col items-center -translate-x-1/2 -translate-y-full transition-transform duration-200"
                style={{
                  left: `${pickupOffset.x}px`,
                  top: `${pickupOffset.y}px`,
                }}
              >
                <div className="rounded-lg bg-emerald-600 px-2 py-0.5 text-[10px] font-bold text-white shadow-md border border-white whitespace-nowrap mb-1">
                  Điểm đón khách
                </div>
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-600 text-white shadow-lg border-2 border-white ring-2 ring-emerald-300">
                  <Icon name="person_pin_circle" className="text-[20px]" />
                </div>
              </div>
            )}

            {/* Vehicle Live Marker */}
            {vehicleOffset && (
              <div
                className="absolute z-30 flex flex-col items-center -translate-x-1/2 -translate-y-1/2 transition-transform duration-300"
                style={{
                  left: `${vehicleOffset.x}px`,
                  top: `${vehicleOffset.y}px`,
                }}
              >
                {/* Accuracy Radius Halo */}
                {vehicleLocation.accuracyMeters && (
                  <div
                    className="absolute rounded-full bg-teal-400/20 border border-teal-500/40 pointer-events-none animate-pulse"
                    style={{
                      width: `${Math.min(120, Math.max(36, vehicleLocation.accuracyMeters * 2))}px`,
                      height: `${Math.min(120, Math.max(36, vehicleLocation.accuracyMeters * 2))}px`,
                    }}
                  />
                )}

                <div className="rounded-lg bg-slate-900/90 backdrop-blur-xs px-2 py-0.5 text-[10px] font-bold text-white shadow-md border border-slate-700 whitespace-nowrap mb-1">
                  {vehicleInfo?.licensePlate || 'Xe phục vụ'}
                </div>
                <div className="relative flex h-10 w-10 items-center justify-center rounded-full bg-teal-600 text-white shadow-xl border-2 border-white ring-4 ring-teal-200">
                  <Icon name="directions_car" className="text-[22px]" />
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Zoom & Center Controls */}
      {interactive && (
        <div className="absolute bottom-6 right-3 z-10 flex flex-col gap-1 shadow-md rounded-xl overflow-hidden border border-slate-200 bg-white">
          <button
            type="button"
            onClick={() => setZoom((z) => Math.min(18, z + 1))}
            className="flex h-8 w-8 items-center justify-center text-slate-700 hover:bg-slate-100 transition active:bg-slate-200 border-b border-slate-100"
            title="Phóng to"
          >
            <Icon name="add" className="text-[18px]" />
          </button>
          <button
            type="button"
            onClick={() => setZoom((z) => Math.max(10, z - 1))}
            className="flex h-8 w-8 items-center justify-center text-slate-700 hover:bg-slate-100 transition active:bg-slate-200 border-b border-slate-100"
            title="Thu nhỏ"
          >
            <Icon name="remove" className="text-[18px]" />
          </button>
          <button
            type="button"
            onClick={() => {
              if (vehicleLocation?.latitude && vehicleLocation?.longitude) {
                setCenter({
                  lat: Number(vehicleLocation.latitude),
                  lon: Number(vehicleLocation.longitude),
                })
              } else if (pickupLocation?.latitude && pickupLocation?.longitude) {
                setCenter({
                  lat: Number(pickupLocation.latitude),
                  lon: Number(pickupLocation.longitude),
                })
              }
            }}
            className="flex h-8 w-8 items-center justify-center text-slate-700 hover:bg-slate-100 transition active:bg-slate-200"
            title="Căn giữa xe"
          >
            <Icon name="my_location" className="text-[16px]" />
          </button>
        </div>
      )}

      {/* Attribution Footer */}
      <div className="absolute bottom-1 left-2 z-10 text-[9px] text-slate-500 bg-white/70 px-1.5 py-0.5 rounded backdrop-blur-xs">
        © OpenStreetMap contributors
      </div>
    </div>
  )
}
