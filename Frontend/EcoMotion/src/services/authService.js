import apiClient from './apiClient'

/**
 * Auth endpoints. Mirrors the backend AuthController contract.
 */
export const authService = {
  /**
   * POST /api/auth/login
   * @returns {Promise<{accessToken:string, tokenType:string, expiresIn:number,
   *                    user:{userId:number,email:string,role:string,status:string}}>}
   */
  async login(email, password, rememberMe = false) {
    const { data } = await apiClient.post('/auth/login', { email, password, rememberMe })
    return data
  },

  /** POST /api/auth/refresh — issues a new access token from the refresh cookie. */
  async refresh() {
    const { data } = await apiClient.post('/auth/refresh')
    return data
  },

  /** GET /api/auth/me — current user; requires the Bearer token. */
  async fetchMe() {
    const { data } = await apiClient.get('/auth/me')
    return data
  },

  /** POST /api/auth/logout — revokes the refresh token and clears the cookie. */
  async logout() {
    await apiClient.post('/auth/logout')
  },
}
