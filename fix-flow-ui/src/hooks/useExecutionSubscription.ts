import { useEffect } from 'react';
import { useExecutionStore } from '../store/executionStore';
import { wsClient } from '../app/wsClient';
import { getExecutionEvents, getExecutionMessages } from '../api/executions';
import { ExecutionEvent, FIXMessage } from '../types';

export function useExecutionSubscription(executionId: string | null): void {
  const addEvent = useExecutionStore((s) => s.addEvent);
  const addMessage = useExecutionStore((s) => s.addMessage);
  const setNodeStatus = useExecutionStore((s) => s.setNodeStatus);
  const updateStatus = useExecutionStore((s) => s.updateStatus);
  const setStartedAt = useExecutionStore((s) => s.setStartedAt);
  const setEndedAt = useExecutionStore((s) => s.setEndedAt);

  useEffect(() => {
    if (!executionId) return;
    let disposer: (() => void) | null = null;
    let cancelled = false;

    const handleEvent = (event: ExecutionEvent) => {
      addEvent(event);
      if (event.type === 'EXECUTION_STARTED') {
        setStartedAt(event.timestamp);
        updateStatus('RUNNING');
      }
      if (event.type === 'NODE_ENTERED' && event.nodeId) setNodeStatus(event.nodeId, 'running');
      if (event.type === 'NODE_EXITED' && event.nodeId) setNodeStatus(event.nodeId, 'passed');
      if (event.type === 'ERROR' && event.nodeId) setNodeStatus(event.nodeId, 'failed');
      if (event.type === 'EXECUTION_FINISHED') {
        const status = (['PASSED', 'FAILED', 'STOPPED'] as const).find((s) => event.detail?.endsWith(s)) ?? 'FAILED';
        updateStatus(status);
        setEndedAt(event.timestamp);
      }
    };

    const handleMessage = (msg: FIXMessage) => addMessage(msg);

    wsClient
      .subscribeExecution(executionId, handleEvent, handleMessage)
      .then((d) => {
        if (cancelled) { d(); return; }
        disposer = d;
        // Backfill events/messages persisted before WS subscription was ready (race condition)
        Promise.all([
          getExecutionEvents(executionId),
          getExecutionMessages(executionId),
        ]).then(([events, messages]) => {
          if (cancelled) return;
          events.forEach(handleEvent);
          messages.forEach(handleMessage);
        }).catch(() => { /* best-effort */ });
      })
      .catch((err) => console.error('WS subscribe failed', err));

    return () => {
      cancelled = true;
      if (disposer) disposer();
    };
  }, [executionId, addEvent, addMessage, setNodeStatus, updateStatus, setStartedAt, setEndedAt]);
}
