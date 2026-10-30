import { useEffect, useState } from 'react'

export function useDeadlineExpired(deadline) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return Boolean(deadline && Date.parse(deadline) <= now)
}
