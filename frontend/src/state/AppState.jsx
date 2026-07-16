import { createContext, useContext, useMemo, useState } from 'react';

// Minimal shared state across the three screens: the active daemon connection
// and the selected target. Everything else is local to each screen.
const AppStateContext = createContext(null);

export function AppStateProvider({ children }) {
  const [connectionId, setConnectionId] = useState(null);
  const [containerId, setContainerId] = useState(null);
  const [healthUrl, setHealthUrl] = useState('');

  const value = useMemo(
    () => ({
      connectionId,
      setConnectionId,
      containerId,
      setContainerId,
      healthUrl,
      setHealthUrl,
    }),
    [connectionId, containerId, healthUrl],
  );

  return <AppStateContext.Provider value={value}>{children}</AppStateContext.Provider>;
}

export function useAppState() {
  const ctx = useContext(AppStateContext);
  if (!ctx) {
    throw new Error('useAppState must be used within AppStateProvider');
  }
  return ctx;
}
