import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { formatSlotPrice } from '../../utils/planLabels'

const DEFAULT_CENTER = [10.7769, 106.7009]
const BASEMAPS = [
  {
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  },
  {
    url: 'https://tile.openstreetmap.de/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  },
]

function carPopup(items, selectedPlanType, cancelClose, scheduleClose) {
  const container = document.createElement('div')
  container.className = 'vehicle-map-popup'
  container.addEventListener('mouseenter', cancelClose)
  container.addEventListener('mouseleave', scheduleClose)
  container.addEventListener('focusin', cancelClose)
  container.addEventListener('focusout', (event) => {
    if (!container.contains(event.relatedTarget)) scheduleClose()
  })
  const address = document.createElement('p')
  address.className = 'vehicle-map-popup-address'
  address.textContent = items[0].vehicle.providerDepotAddress || 'Depot của nhà cung cấp'
  container.append(address)
  const hint = document.createElement('p')
  hint.className = 'vehicle-map-popup-hint'
  hint.textContent = 'Rê chuột hoặc chạm vào xe để xem ảnh.'
  container.append(hint)

  const layout = document.createElement('div')
  layout.className = 'vehicle-map-popup-layout'
  const preview = document.createElement('figure')
  preview.className = 'vehicle-map-popup-preview'
  const previewImage = document.createElement('img')
  previewImage.hidden = true
  const previewCaption = document.createElement('figcaption')
  const noImage = document.createElement('p')
  noImage.textContent = 'Chọn xe để xem ảnh.'
  previewImage.addEventListener('error', () => {
    previewImage.hidden = true
    noImage.hidden = false
  })
  preview.append(previewImage, noImage, previewCaption)
  const carList = document.createElement('div')
  carList.className = 'vehicle-map-popup-list'

  const showPreview = (car) => {
    const imageUrl = car.imageUrls?.[0] || car.coverImageUrl
    previewCaption.textContent = `${car.brand} ${car.model}`
    previewImage.alt = `Ảnh đầu tiên của ${car.brand} ${car.model}`
    previewImage.hidden = !imageUrl
    noImage.textContent = 'Xe này chưa có ảnh.'
    noImage.hidden = Boolean(imageUrl)
    if (imageUrl) previewImage.src = imageUrl
  }

  for (const item of items) {
    const car = item.vehicle
    const row = document.createElement('div')
    row.className = 'vehicle-map-popup-car'
    const info = document.createElement('button')
    info.type = 'button'
    info.className = 'vehicle-map-popup-car-info'
    const title = document.createElement('strong')
    title.textContent = `${car.brand} ${car.model}`
    const price = document.createElement('span')
    const plans = car.plans || []
    const plan = plans.find((candidate) => candidate.planType === selectedPlanType)
      || plans.reduce((min, candidate) => !min || Number(candidate.slotPrice) < Number(min.slotPrice)
        ? candidate : min, null)
    price.textContent = `${item.distanceKm.toFixed(1)} km · ${plan ? formatSlotPrice(plan.slotPrice, plan.slotLengthMinutes) : 'Liên hệ'}`
    const link = document.createElement('a')
    link.href = `/vehicles/${encodeURIComponent(car.id)}`
    link.textContent = 'Xem chi tiết'
    info.append(title, price)
    row.append(info, link)
    row.addEventListener('mouseenter', () => showPreview(car))
    row.addEventListener('focusin', () => showPreview(car))
    info.addEventListener('click', () => showPreview(car))
    carList.append(row)
  }
  layout.append(preview, carList)
  container.append(layout)
  return container
}

export default function VehicleMap({ draftOrigin, selectedOrigin, vehicles, selectedPlanType,
  radiusKm, onPick, onGps, gpsLoading, draftRadius,
  onRadiusChange, onMapSearch, children }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markersRef = useRef(null)
  const tileLayerRef = useRef(null)
  const retryBasemapRef = useRef(null)
  const [tileError, setTileError] = useState(false)
  const [tileLoading, setTileLoading] = useState(true)

  useEffect(() => {
    const map = L.map(containerRef.current, { scrollWheelZoom: true, touchZoom: true, zoomControl: false })
      .setView(DEFAULT_CENTER, 13)
    L.control.zoom({ position: 'bottomright' }).addTo(map)
    let sourceIndex = 0
    let tileTimeout
    const addBasemap = () => {
      const source = BASEMAPS[sourceIndex]
      let hasLoadedTile = false
      const layer = L.tileLayer(source.url, {
        maxZoom: 19,
        attribution: source.attribution,
      })
      const handleFailure = () => {
        window.clearTimeout(tileTimeout)
        if (tileLayerRef.current !== layer || hasLoadedTile) return
        if (sourceIndex < BASEMAPS.length - 1) {
          map.removeLayer(layer)
          sourceIndex += 1
          addBasemap()
        } else {
          setTileLoading(false)
          setTileError(true)
        }
      }
      layer.on('tileload', () => {
        hasLoadedTile = true
        window.clearTimeout(tileTimeout)
        setTileLoading(false)
        setTileError(false)
      })
      layer.once('tileerror', handleFailure)
      tileLayerRef.current = layer
      tileTimeout = window.setTimeout(handleFailure, 8000)
      layer.addTo(map)
    }
    retryBasemapRef.current = () => {
      window.clearTimeout(tileTimeout)
      if (tileLayerRef.current) map.removeLayer(tileLayerRef.current)
      sourceIndex = 0
      setTileError(false)
      setTileLoading(true)
      addBasemap()
    }
    addBasemap()
    map.on('click', (event) => onPick({ latitude: event.latlng.lat, longitude: event.latlng.lng }))
    mapRef.current = map
    markersRef.current = L.layerGroup().addTo(map)
    return () => {
      window.clearTimeout(tileTimeout)
      tileLayerRef.current = null
      retryBasemapRef.current = null
      map.remove()
      mapRef.current = null
      markersRef.current = null
    }
  }, [onPick])

  useEffect(() => {
    const map = mapRef.current
    const markers = markersRef.current
    if (!map || !markers) return
    markers.clearLayers()
    const pendingOrigin = draftOrigin && selectedOrigin
      && (draftOrigin.latitude !== selectedOrigin.latitude
        || draftOrigin.longitude !== selectedOrigin.longitude)

    if (selectedOrigin && !pendingOrigin) {
      L.circle([selectedOrigin.latitude, selectedOrigin.longitude], {
        radius: radiusKm * 1000,
        color: '#059669', fillColor: '#10b981', fillOpacity: 0.08, weight: 2,
      }).addTo(markers)
      L.circleMarker([selectedOrigin.latitude, selectedOrigin.longitude], {
        radius: 10, color: '#ffffff', weight: 3, fillColor: '#047857', fillOpacity: 1,
      }).bindTooltip('Vị trí tìm kiếm').addTo(markers)
    }

    if (draftOrigin && (!selectedOrigin || draftOrigin.latitude !== selectedOrigin.latitude
      || draftOrigin.longitude !== selectedOrigin.longitude)) {
      L.circleMarker([draftOrigin.latitude, draftOrigin.longitude], {
        radius: 10, color: '#ffffff', weight: 3, fillColor: '#2563eb', fillOpacity: 1,
      }).bindTooltip('Ghim chưa xác nhận').addTo(markers)
    }

    const groups = new Map()
    for (const item of vehicles) {
      const key = `${item.depotLatitude},${item.depotLongitude}`
      groups.set(key, [...(groups.get(key) || []), item])
    }
    for (const group of groups.values()) {
      const first = group[0]
      const address = first.vehicle.providerDepotAddress || 'depot của nhà cung cấp'
      const marker = L.circleMarker([Number(first.depotLatitude), Number(first.depotLongitude)], {
        radius: 10, color: '#ffffff', weight: 3, fillColor: '#dc2626', fillOpacity: 1,
      })
      let closeTimer
      const cancelClose = () => window.clearTimeout(closeTimer)
      const scheduleClose = () => {
        cancelClose()
        closeTimer = window.setTimeout(() => marker.closePopup(), 250)
      }
      marker.bindPopup(carPopup(group, selectedPlanType, cancelClose, scheduleClose), {
        maxHeight: 360,
        minWidth: Math.min(480, Math.max(220, window.innerWidth - 80)),
        maxWidth: 480,
      })
      marker.on('mouseover', () => {
        cancelClose()
        marker.openPopup()
      })
      marker.on('mouseout', scheduleClose)
      marker.on('click', () => {
        cancelClose()
        marker.openPopup()
      })
      marker.on('popupopen', () => {
        const popupElement = marker.getPopup().getElement()
        popupElement.addEventListener('mouseenter', cancelClose)
        popupElement.addEventListener('mouseleave', scheduleClose)
      })
      marker.on('popupclose', cancelClose)
      marker.on('remove', cancelClose)
      marker.addTo(markers)
      const markerElement = marker.getElement()
      markerElement.setAttribute('tabindex', '0')
      markerElement.setAttribute('role', 'button')
      markerElement.setAttribute('aria-label', `${group.length} xe tại ${address}`)
      markerElement.style.cursor = 'pointer'
      markerElement.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          cancelClose()
          marker.openPopup()
        } else if (event.key === 'Escape') {
          marker.closePopup()
        }
      })
    }
  }, [draftOrigin, selectedOrigin, vehicles, selectedPlanType, radiusKm])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    if (draftOrigin && (!selectedOrigin || draftOrigin.latitude !== selectedOrigin.latitude
      || draftOrigin.longitude !== selectedOrigin.longitude)) {
      map.panTo([draftOrigin.latitude, draftOrigin.longitude])
    } else if (selectedOrigin) {
      map.panTo([selectedOrigin.latitude, selectedOrigin.longitude])
    }
  }, [draftOrigin, selectedOrigin])

  return (
    <div className="relative h-[480px] w-full rounded-xl bg-slate-100 sm:h-[580px]">
      <div ref={containerRef} className="relative z-0 h-full w-full rounded-xl" aria-label="Bản đồ tìm xe" />
      <div className="absolute inset-x-3 top-3 z-[1001] flex flex-wrap items-start justify-end gap-2">
        <div className="min-w-0 w-full xl:mr-auto xl:w-[min(45%,24rem)]">{children}</div>
        <div role="group" aria-label="Điều khiển tìm xe trên bản đồ"
          className="flex flex-wrap items-center justify-end gap-2 rounded-xl bg-white/95 p-2 shadow-lg">
          <button type="button" onClick={onGps} disabled={gpsLoading}
            className="rounded-lg border border-brand-600 px-3 py-2 text-xs font-semibold text-brand-700 hover:bg-brand-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 disabled:opacity-50">
            {gpsLoading ? 'Đang định vị…' : 'Dùng vị trí hiện tại'}
          </button>
          <label className="text-xs font-semibold text-slate-700">
            Bán kính
            <select value={draftRadius} onChange={(event) => onRadiusChange(Number(event.target.value))}
              className="ml-2 rounded-lg border border-slate-200 bg-white px-2 py-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-600">
              <option value={10}>10 km</option>
              <option value={15}>15 km</option>
            </select>
          </label>
          <button type="button" onClick={onMapSearch}
            className="rounded-lg bg-brand-600 px-4 py-2 text-xs font-semibold text-white hover:bg-brand-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600">
            Tìm xe gần đây
          </button>
        </div>
        {tileLoading && (
          <div role="status" className="w-full rounded-lg bg-white px-3 py-2 text-sm text-slate-700 shadow-lg">
            Đang tải bản đồ…
          </div>
        )}
        {tileError && (
          <div role="alert" className="w-full rounded-lg border border-amber-200 bg-white p-3 text-sm text-slate-700 shadow-lg">
            <span>Không tải được nền bản đồ. Kiểm tra kết nối mạng rồi </span>
            <button type="button" onClick={() => retryBasemapRef.current?.()}
              className="font-semibold text-brand-700 underline hover:text-brand-800">
              thử lại
            </button>.
          </div>
        )}
      </div>
    </div>
  )
}
