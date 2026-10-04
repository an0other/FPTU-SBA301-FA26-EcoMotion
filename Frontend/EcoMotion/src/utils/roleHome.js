/**
 * Maps a user role to its landing route after login.
 * One account = one role (project decision D1).
 */
export const ROLE_HOME = {
  RENTER: '/renter',
  PROVIDER: '/provider',
  ADMIN: '/admin',
  DRIVER: '/renter',
}

export function homeRouteForRole(role) {
  return ROLE_HOME[role] ?? '/login'
}
