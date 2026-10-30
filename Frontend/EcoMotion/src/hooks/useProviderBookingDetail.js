import { useEffect, useState, useCallback, useRef } from 'react'
import { providerBookingService } from '../services/providerBookingService'
import { bookingWorkflowService } from '../services/bookingWorkflowService'

export function useProviderBookingDetail(idOrCode) {
  const [state, setState] = useState({ key: null })
  const sequence = useRef(0)
  const refetch = useCallback(async () => {
    const id = ++sequence.current
    const [row, risk, workflow] = await Promise.allSettled([
      providerBookingService.getBookingDetail(idOrCode),
      providerBookingService.getOperationalRisk(idOrCode),
      bookingWorkflowService.get(idOrCode, 'provider'),
    ])
    if (id !== sequence.current) return
    setState(previous => ({
      key: idOrCode,
      booking: row.status === 'fulfilled' ? row.value : previous.key === idOrCode ? previous.booking : null,
      operationalRisk: risk.status === 'fulfilled' ? risk.value : null,
      workflow: workflow.status === 'fulfilled' ? workflow.value : null,
      workflowError: workflow.status === 'rejected' ? workflow.reason.message : null,
      error: row.status === 'rejected' ? row.reason.message : null,
    }))
  }, [idOrCode])
  useEffect(() => {
    const timer = setTimeout(refetch, 0)
    return () => { clearTimeout(timer); ++sequence.current }
  }, [refetch])
  const loaded = state.key === idOrCode
  return { booking: loaded ? state.booking : null, operationalRisk: loaded ? state.operationalRisk : null,
    workflow: loaded ? state.workflow : null, workflowError: loaded ? state.workflowError : null,
    loading: !loaded, error: loaded ? state.error : null, refetch }
}
