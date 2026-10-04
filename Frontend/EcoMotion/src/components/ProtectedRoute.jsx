import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { homeRouteForRole } from '../utils/roleHome'

/**
 * Route guard.
 *
 * - While the session is being restored, render a loader (avoids a flash
 *   redirect to /login on a hard refresh of a protected page).
 * - Unauthenticated → redirect to /login, remembering where the user came from.
 * - `allowedRoles` (optional) restricts access by role.
 */
export default function ProtectedRoute({ allowedRoles }) {
  const { isAuthenticated, loading, user } = useAuth()
  const location = useLocation()

  if (loading) {
    return <div className="route-loading">Đang tải…</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={homeRouteForRole(user.role)} replace />
  }

  return <Outlet />
}
