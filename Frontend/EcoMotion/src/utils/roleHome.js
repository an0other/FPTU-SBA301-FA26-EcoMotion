// Renters and drivers land on the car list, which is the public home page at "/".
export const ROLE_HOME = {
  RENTER: '/',
  PROVIDER: '/provider',
  ADMIN: '/admin',
  DRIVER: '/',
}

export function homeRouteForRole(role) {
  return ROLE_HOME[role] ?? '/login'
}
