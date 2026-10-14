export const ROLE_HOME = {
  RENTER: '/',
  PROVIDER: '/provider',
  ADMIN: '/admin',
  DRIVER: '/',
}

export function homeRouteForRole(role) {
  return ROLE_HOME[role] ?? '/login'
}
