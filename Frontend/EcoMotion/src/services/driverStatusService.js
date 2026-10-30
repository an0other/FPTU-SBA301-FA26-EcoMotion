import apiClient from './apiClient.js'
export function createDriverStatusService(client = apiClient) {
  return {
    async getStatus() {
      const { data } = await client.get('/v1/driver/status')
      return data.data
    },
    async updateStatus(activityStatus) {
      const { data } = await client.put('/v1/driver/status', { activityStatus })
      return data.data
    },
  }
}
export const driverStatusService = createDriverStatusService()
