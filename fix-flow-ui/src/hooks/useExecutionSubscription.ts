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
  const setOperatorPrompt = useExecutionStore((s) => s.setOperatorPrompt);
  const refreshOperatorPrompt = useExecutionStore((s) => s.refreshOperatorPrompt);

  useEffect(() => {
    if (!executionId) return;
    let disposer: (() => void) | null = null;
    let cancelled = false;
    let backfilling = false;
    setOperatorPrompt(null);

    // The open prompt is read from the server: it knows the labels and the id of the child
    // execution that is waiting, which the event alone does not carry.
    const refreshPrompt = () => { if (!cancelled) void refreshOperatorPrompt(executionId); };

    const TERMINAL: ReadonlyArray<string> = ['PASSED', 'FAILED', 'STOPPED'];

    const handleEvent = (event: ExecutionEvent) => {
      addEvent(event);
      if (event.type === 'EXECUTION_STARTED') {
        setStartedAt(event.timestamp);
        // Monotonic status: never drop a finished run back to RUNNING. The persisted-event
        // backfill can replay EXECUTION_STARTED after the live EXECUTION_FINISHED has already
        // arrived; without this guard the status would be stranded at RUNNING and the Stop
        // button would never reset to Run (issue #63).
        if (!TERMINAL.includes(useExecutionStore.getState().executionStatus)) updateStatus('RUNNING');
      }
      if (event.type === 'NODE_ENTERED' && event.nodeId) setNodeStatus(event.nodeId, 'running');
      if (event.type === 'NODE_EXITED' && event.nodeId) setNodeStatus(event.nodeId, 'passed');
      if (event.type === 'ERROR' && event.nodeId) setNodeStatus(event.nodeId, 'failed');
      if (event.type === 'OPERATOR_PROMPT' && event.nodeId) setNodeStatus(event.nodeId, 'running');
      if ((event.type === 'OPERATOR_PROMPT' || event.type === 'OPERATOR_ANSWERED') && !backfilling) refreshPrompt();
      if (event.type === 'EXECUTION_FINISHED') {
        setOperatorPrompt(null);
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
          backfilling = true;
          events.forEach(handleEvent);
          backfilling = false;
          messages.forEach(handleMessage);
          refreshPrompt();
        }).catch(() => { /* best-effort */ });
      })
      .catch((err) => console.error('WS subscribe failed', err));

    return () => {
      cancelled = true;
      if (disposer) disposer();
    };
  }, [executionId, addEvent, addMessage, setNodeStatus, updateStatus, setStartedAt, setEndedAt, setOperatorPrompt, refreshOperatorPrompt]);
}
