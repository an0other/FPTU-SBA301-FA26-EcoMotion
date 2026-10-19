import { useState, useEffect, useRef, useCallback } from 'react'
import { driverTripService } from '../services/driverTripService'

export function useDriverLocationTracker({
  tripId,
  enabled = false,
  minIntervalMs = 10000,
  onError,
  onLocationSent,
}) {
  const [trackingState, setTrackingState] = useState('IDLE') // IDLE, ACTIVE, DENIED, UNAVAILABLE, ERROR
  const [currentCoords, setCurrentCoords] = useState(null)
  const [lastSentAt, setLastSentAt] = useState(null)
  const [errorMessage, setErrorMessage] = useState(null)

  const watchIdRef = useRef(null)
  const lastSentTimestampRef = useRef(0)
  const isSubmittingRef = useRef(false)

  const stopTracking = useCallback(() => {
    if (watchIdRef.current !== null && navigator.geolocation) {
      navigator.geolocation.clearWatch(watchIdRef.current)
      watchIdRef.current = null
    }
    setTrackingState('IDLE')
  }, [])

  const sendLocationUpdate = useCallback(
    async (coords) => {
      if (!tripId || isSubmittingRef.current) return

      const now = Date.now()
      if (now - lastSentTimestampRef.current < minIntervalMs) {
        return
      }

      isSubmittingRef.current = true
      try {
        const payload = {
          latitude: coords.latitude,
          longitude: coords.longitude,
          accuracyMeters: coords.accuracy || 10,
          recordedAt: new Date().toISOString(),
        }

        const res = await driverTripService.sendLocation(tripId, payload)
        lastSentTimestampRef.current = now
        setLastSentAt(new Date())
        setErrorMessage(null)
        if (onLocationSent) {
          onLocationSent(res)
        }
      } catch (err) {
        const msg = err.response?.data?.message || err.message || 'Lỗi gửi tọa độ vị trí'
        setErrorMessage(msg)
        if (onError) onError(err)
      } finally {
        isSubmittingRef.current = false
      }
    },
    [tripId, minIntervalMs, onError, onLocationSent]
  )

  const startTracking = useCallback(() => {
    if (!navigator.geolocation) {
      setTrackingState('UNAVAILABLE')
      setErrorMessage('Trình duyệt không hỗ trợ Geolocation GPS')
      return
    }

    if (watchIdRef.current !== null) {
      return
    }

    setTrackingState('ACTIVE')
    setErrorMessage(null)

    const handleSuccess = (position) => {
      const coords = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: position.coords.accuracy,
      }
      setCurrentCoords(coords)
      sendLocationUpdate(coords)
    }

    const handleError = (error) => {
      switch (error.code) {
        case error.PERMISSION_DENIED:
          setTrackingState('DENIED')
          setErrorMessage('Bạn đã từ chối quyền truy cập vị trí GPS.')
          break
        case error.POSITION_UNAVAILABLE:
          setTrackingState('UNAVAILABLE')
          setErrorMessage('Không thể xác định vị trí hiện tại của thiết bị.')
          break
        case error.TIMEOUT:
          setTrackingState('ERROR')
          setErrorMessage('Hết thời gian chờ phản hồi từ GPS.')
          break
        default:
          setTrackingState('ERROR')
          setErrorMessage('Lỗi không xác định khi lấy vị trí.')
          break
      }
      if (onError) onError(error)
    }

    watchIdRef.current = navigator.geolocation.watchPosition(
      handleSuccess,
      handleError,
      {
        enableHighAccuracy: true,
        maximumAge: 5000,
        timeout: 15000,
      }
    )
  }, [sendLocationUpdate, onError])

  useEffect(() => {
    if (enabled && tripId) {
      startTracking()
    } else {
      stopTracking()
    }

    return () => {
      stopTracking()
    }
  }, [enabled, tripId, startTracking, stopTracking])

  return {
    trackingState,
    currentCoords,
    lastSentAt,
    errorMessage,
    startTracking,
    stopTracking,
  }
}
