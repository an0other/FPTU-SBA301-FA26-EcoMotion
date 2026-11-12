import { useEffect, useRef, useState } from 'react'
import { identityVerificationService as api } from '../../services/identityVerificationService'
import { uploadPercentage } from './identityProcessing'

export default function useIdentityProcessing({ attempt, activationToken, onRefresh }) {
  const [task, setTask] = useState(null)
  const [reports, setReports] = useState({})
  const [remote, setRemote] = useState(null)
  const [pollError, setPollError] = useState(false)
  const sequence = useRef(0)
  const currentTask = useRef(null)
  const active = !!task?.active
  const recovering = attempt?.status === 'PROCESSING'
  const running = active || recovering
  useEffect(() => {
    if (!running || !attempt?.attemptId) return
    let alive = true
    let timer
    const controller = new AbortController()
    async function poll() {
      try {
        const response = await api.processing(attempt.attemptId, activationToken, controller.signal)
        if (!alive) return
        setRemote(previous => ({ ...response, aiProgress: response.aiProgress || previous?.aiProgress, receivedAt: Date.now(), seenProcessing: response.status === 'PROCESSING' || !!previous?.seenProcessing })); setPollError(false)
        if (response.aiProgress && response.processingStage) setReports(previous => ({ ...previous,
          [response.processingStage]: { ...previous[response.processingStage], attemptId: attempt.attemptId,
            task: previous[response.processingStage]?.task || { kind: response.processingStage, startedAt: Date.parse(response.startedAt) },
            remote: { ...response, seenProcessing: true, receivedAt: Date.now() } } }))
        if (!active && response.status !== 'PROCESSING') {
          if (task?.kind) setReports(previous => ({ ...previous, [task.kind]: { ...previous[task.kind], complete: true,
            task: { ...previous[task.kind]?.task, finishedAt: Date.now() },
            remote: { ...previous[task.kind]?.remote, status: response.status === 'ERROR' ? 'ERROR' : 'COMPLETED', seenProcessing: true } } }))
          await onRefresh(); return
        }
      } catch (error) { if (alive && error.code !== 'ERR_CANCELED') setPollError(true) }
      if (alive) timer = setTimeout(poll, 1500)
    }
    poll()
    return () => { alive = false; controller.abort(); clearTimeout(timer) }
  }, [running, active, attempt?.attemptId, activationToken, onRefresh, task?.key, task?.kind])
  function start(kind) {
    const key = ++sequence.current
    currentTask.current = { key, kind, startedAt: Date.now() }
    setRemote(null); setPollError(false)
    setReports(previous => ({ ...(kind === 'DOCUMENT' ? {} : previous), [kind]: { attemptId: attempt.attemptId, task: { kind, startedAt: Date.now() }, remote: null, complete: false } }))
    setTask({ key, kind, active: true, startedAt: Date.now(), uploadPercent: null, phase: 'UPLOAD' })
    return event => setTask(current => current?.key === key && current.active
      ? { ...current, uploadPercent: uploadPercentage(event), phase: uploadPercentage(event) === 100 ? 'WAIT' : current.phase } : current)
  }
  function finish(success = true) {
    const ended = currentTask.current
    if (!ended) return
    currentTask.current = null
    setReports(previous => ({ ...previous, [ended.kind]: { ...previous[ended.kind], complete: success || previous[ended.kind]?.remote?.status === 'ERROR',
      task: { ...ended, active: false, finishedAt: Date.now() },
      remote: { ...previous[ended.kind]?.remote, status: success ? 'COMPLETED' : previous[ended.kind]?.remote?.status || 'UNKNOWN', seenProcessing: true } } }))
    setTask(current => current ? { ...current, active: false, phase: 'DONE' } : current)
  }
  const visibleRemote = recovering && (!remote || remote.attemptId !== attempt.attemptId)
    ? { status: 'PROCESSING', processingStage: attempt.processingStage } : remote
  const persisted = {}
  for (const run of attempt?.pipelineReports || []) {
    if (run.revision !== attempt.documentRevision || run.status === 'PROCESSING') continue
    if (running && (task?.kind === 'DOCUMENT' || run.kind === task?.kind)) continue
    persisted[run.kind] = { attemptId: attempt.attemptId, complete: true,
      task: { kind: run.kind, startedAt: Date.parse(run.startedAt), finishedAt: Date.parse(run.finishedAt) },
      remote: { status: run.status, aiProgress: run.progress, errorCode: run.errorCode, seenProcessing: true } }
  }
  return { start, finish, running, task, remote: visibleRemote, pollError, reports: { ...reports, ...persisted } }
}
