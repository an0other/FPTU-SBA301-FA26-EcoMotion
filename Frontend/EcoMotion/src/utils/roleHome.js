export const ROLE_HOME = {
  RENTER: '/',
  PROVIDER: '/provider',
  ADMIN: '/admin',
  DRIVER: '/driver/schedule',
}

export function homeRouteForRole(role) {
  return ROLE_HOME[role] ?? '/login'
}
