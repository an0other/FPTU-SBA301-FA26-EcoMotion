import apiClient from './apiClient'

function contractPath(bookingCode) {
  return `/renter/bookings/${encodeURIComponent(bookingCode)}/contract`
}

export const yearlyRentalService = {
  async getDrivingLicense() {
    const { data } = await apiClient.get('/renter/driving-license')
    return data.data
  },

  async updateDrivingLicense(request) {
    const { data } = await apiClient.put('/renter/driving-license', request)
    return data.data
  },

  async getContractStatus(bookingCode) {
    const { data } = await apiClient.get(`${contractPath(bookingCode)}/status`)
    return data.data
  },

  async uploadContract(bookingCode, file) {
    const form = new FormData()
    form.append('file', file)

    const { data } = await apiClient.put(contractPath(bookingCode), form)
    return data.data
  },

  async downloadContract(bookingCode) {
    try {
      const response = await apiClient.get(contractPath(bookingCode), {
        responseType: 'blob',
        headers: { Accept: 'application/pdf, application/json' },
      })

      return response.data
    } catch (error) {
      const body = error.original?.response?.data

      if (body instanceof Blob) {
        const text = await body.text()
        let message

        try {
          message = JSON.parse(text).message
        } catch {
          message = null
        }

        if (message) {
          const downloadError = new Error(message)
          downloadError.status = error.status
          throw downloadError
        }
      }

      throw error
    }
  },
}