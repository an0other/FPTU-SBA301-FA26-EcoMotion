import { useEffect, useState } from 'react'
import { yearlyRentalService } from '../services/yearlyRentalService'

export function useYearlyRental(bookingCode) {
  const [licenseState, setLicenseState] = useState({
    data: null,
    loading: true,
    error: '',
  })
  const [contractState, setContractState] = useState({
    data: null,
    loading: false,
    error: '',
  })
  const [licenseRevision, setLicenseRevision] = useState(0)
  const [contractRevision, setContractRevision] = useState(0)

  useEffect(() => {
    let active = true

    async function load() {
      setLicenseState({ data: null, loading: true, error: '' })

      try {
        const data = await yearlyRentalService.getDrivingLicense()
        if (active) {
          setLicenseState({ data, loading: false, error: '' })
        }
      } catch (error) {
        if (active) {
          setLicenseState({
            data: null,
            loading: false,
            error: error.message || 'Không tải được GPLX.',
          })
        }
      }
    }

    load()

    return () => {
      active = false
    }
  }, [licenseRevision])

  useEffect(() => {
    let active = true

    async function load() {
      if (!bookingCode) {
        setContractState({ data: null, loading: false, error: '' })
        return
      }

      setContractState({ data: null, loading: true, error: '' })

      try {
        const data = await yearlyRentalService.getContractStatus(bookingCode)
        if (active) {
          setContractState({ data, loading: false, error: '' })
        }
      } catch (error) {
        if (active) {
          setContractState({
            data: null,
            loading: false,
            error: error.message || 'Không tải được trạng thái hợp đồng.',
          })
        }
      }
    }

    load()

    return () => {
      active = false
    }
  }, [bookingCode, contractRevision])

  return {
    licenseState,
    contractState,
    reloadLicense: () => setLicenseRevision((value) => value + 1),
    reloadContract: () => setContractRevision((value) => value + 1),
  }
}