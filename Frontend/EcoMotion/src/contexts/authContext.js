import { createContext } from 'react'

/**
 * Bare context object, kept in its own module so `AuthContext.jsx` only exports
 * components (avoids the react-refresh "only-export-components" lint warning).
 */
export const AuthContext = createContext(null)
