import { useEffect } from 'react';
import { useSessionStore } from '../store/sessionStore';
import { wsClient, SessionStatusEvent } from '../app/wsClient';

export function useSessionSubscription(sessionIds: string[]): void {
  const updateSession = useSessionStore((s) => s.updateSession);

  useEffect(() => {
    if (sessionIds.length === 0) return;

    const disposers: Array<() => void> = [];
    let cancelled = false;

    const handleStatus = (event: SessionStatusEvent) => {
      updateSession(event.sessionId, { connected: event.status === 'UP' || event.status === 'LOGON' });
    };

    sessionIds.forEach((id) => {
      wsClient
        .subscribeSession(id, handleStatus)
        .then((dispose) => {
          if (cancelled) dispose();
          else disposers.push(dispose);
        })
        .catch((err) => console.error('Session WS subscribe failed', err));
    });

    return () => {
      cancelled = true;
      disposers.forEach((d) => d());
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionIds.join(','), updateSession]);
}
