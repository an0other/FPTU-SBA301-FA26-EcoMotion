import { useCallback, useEffect, useRef, useState } from 'react'
import { drivingLicenseReviewService as api } from '../../services/drivingLicenseReviewService'
import { uploadPercentage } from './identityProcessing'

export default function useLicenseProcessing(onComplete) {
  const [history, setHistory] = useState([])
  const [historyPage, setHistoryPage] = useState(0)
  const [historyError, setHistoryError] = useState('')
  const [historyBusy, setHistoryBusy] = useState(false)
  const [operation, setOperation] = useState(null)
  const [task, setTask] = useState(null)
  const [remote, setRemote] = useState(null)
  const [pollError, setPollError] = useState(false)
  const generation = useRef(0)
  const callback = useRef(onComplete)
  useEffect(() => { callback.current = onComplete }, [onComplete])
  const loadHistory = useCallback(async (page = 0) => {
    setHistoryBusy(true);setHistoryError('')
    try {const values=await api.pipelineHistory(page);setHistory(values);setHistoryPage(page)}
    catch {setHistoryError('Chưa tải được lịch sử pipeline. Vui lòng thử lại.')}
    finally {setHistoryBusy(false)}
  }, [])
  const active = !!task?.active
  useEffect(() => {
    const controller = new AbortController()
    api.currentProcessing(controller.signal).then(value => {
      if (value && generation.current === 0 && !controller.signal.aborted) { setOperation(value.attemptId); setRemote({ ...value, receivedAt: Date.now(), seenProcessing: true }) }
    }).then(() => api.pipelineHistory()).then(values => {if(!controller.signal.aborted)setHistory(values)}).catch(() => { if (!controller.signal.aborted) setPollError(true) })
    return () => controller.abort()
  }, [])
  useEffect(() => {
    if (!operation) return
    let alive = true, timer
    const controller = new AbortController()
    async function poll() {
      try {
        const value = await api.processing(operation, controller.signal)
        if (!alive) return
        setRemote(previous => ({ ...value, receivedAt: Date.now(), seenProcessing: value.status === 'PROCESSING' || !!previous?.seenProcessing })); setPollError(false)
        if (!active && value.status !== 'PROCESSING') {
          await callback.current(value)
          await loadHistory(0)
          if (alive) { setOperation(null); setTask(null) }
          return
        }
      } catch (error) {
        if (!alive || error.code === 'ERR_CANCELED') return
        if (error.status === 404 && active) setPollError(false)
        else if (error.status === 404) {
          await callback.current({ status: 'ERROR', errorCode: 'LICENSE_PROGRESS_UNAVAILABLE' })
          if (alive) { setOperation(null); setTask(null) }
          return
        } else setPollError(true)
      }
      if (alive) timer = setTimeout(poll, 1500)
    }
    poll()
    return () => { alive = false; controller.abort(); clearTimeout(timer) }
  }, [operation, active, loadHistory])
  function start() {
    generation.current += 1
    const id = crypto.randomUUID()
    setOperation(id);setRemote(null);setPollError(false)
    setTask({ operationId: id, active: true, kind: 'DOCUMENT', phase: 'UPLOAD', startedAt: Date.now(), uploadPercent: null })
    return { id, onUploadProgress: event => setTask(current => current?.active && current.operationId === id ? { ...current, uploadPercent: uploadPercentage(event), phase: uploadPercentage(event) === 100 ? 'WAIT' : current.phase } : current) }
  }
  function finish() { setTask(current => current ? { ...current, active: false } : current) }
  return { start, finish, running: !!operation, task, remote, pollError, history, historyPage, loadHistory, historyError, historyBusy }
}
