export const ROLE_HOME = {
  RENTER: '/renter',
  PROVIDER: '/provider',
  ADMIN: '/admin',
  DRIVER: '/renter',
}

export function homeRouteForRole(role) {
  return ROLE_HOME[role] ?? '/login'
}
