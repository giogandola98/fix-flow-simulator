import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { ExecutionEvent } from '../types';

// Capture the onEvent callback the hook registers with the ws client so tests can drive events.
let capturedOnEvent: ((e: ExecutionEvent) => void) | null = null;
const disposer = vi.fn();

vi.mock('../app/wsClient', () => ({
  wsClient: {
    subscribeExecution: vi.fn((_id: string, onEvent: (e: ExecutionEvent) => void) => {
      capturedOnEvent = onEvent;
      return Promise.resolve(disposer);
    }),
  },
}));

vi.mock('../api/executions', () => ({
  getExecutionEvents: vi.fn(() => Promise.resolve([])),
  getExecutionMessages: vi.fn(() => Promise.resolve([])),
}));

import { useExecutionSubscription } from './useExecutionSubscription';
import { useExecutionStore } from '../store/executionStore';

const resetStore = () =>
  useExecutionStore.setState({
    activeExecutionId: null,
    executionStatus: 'IDLE',
    events: [],
    messages: [],
    seenEventIds: new Set<string>(),
    seenMsgIds: new Set<string>(),
    nodeStatuses: {},
    startedAt: null,
    endedAt: null,
  });

const evt = (id: string, type: string, extra: Partial<ExecutionEvent> = {}): ExecutionEvent => ({
  id,
  executionId: 'exec-1',
  type,
  timestamp: '2026-01-01T00:00:00Z',
  ...extra,
});

describe('useExecutionSubscription', () => {
  beforeEach(() => {
    resetStore();
    capturedOnEvent = null;
    disposer.mockClear();
  });

  const mountAndReady = async () => {
    const view = renderHook(() => useExecutionSubscription('exec-1'));
    await waitFor(() => expect(capturedOnEvent).not.toBeNull());
    return view;
  };

  it('sets RUNNING on EXECUTION_STARTED', async () => {
    await mountAndReady();
    capturedOnEvent!(evt('e1', 'EXECUTION_STARTED'));
    expect(useExecutionStore.getState().executionStatus).toBe('RUNNING');
    expect(useExecutionStore.getState().startedAt).toBe('2026-01-01T00:00:00Z');
  });

  it('EXECUTION_FINISHED parses terminal status from detail.endsWith', async () => {
    await mountAndReady();
    capturedOnEvent!(evt('e1', 'EXECUTION_STARTED'));
    capturedOnEvent!(evt('e2', 'EXECUTION_FINISHED', { detail: 'Execution finished: PASSED' }));
    expect(useExecutionStore.getState().executionStatus).toBe('PASSED');
  });

  it('EXECUTION_FINISHED defaults to FAILED when detail has no known status', async () => {
    await mountAndReady();
    capturedOnEvent!(evt('e1', 'EXECUTION_FINISHED', { detail: 'nonsense' }));
    expect(useExecutionStore.getState().executionStatus).toBe('FAILED');
  });

  // Issue #63: monotonic status guard.
  it('does NOT flip a terminal status back to RUNNING on replayed EXECUTION_STARTED', async () => {
    await mountAndReady();
    // Live run: started then finished PASSED
    capturedOnEvent!(evt('e1', 'EXECUTION_STARTED'));
    capturedOnEvent!(evt('e2', 'EXECUTION_FINISHED', { detail: 'done PASSED' }));
    expect(useExecutionStore.getState().executionStatus).toBe('PASSED');

    // Backfill replays EXECUTION_STARTED after the terminal event — must stay PASSED.
    capturedOnEvent!(evt('e3', 'EXECUTION_STARTED'));
    expect(useExecutionStore.getState().executionStatus).toBe('PASSED');
  });

  it('monotonic guard holds for FAILED and STOPPED too', async () => {
    await mountAndReady();
    for (const terminal of ['FAILED', 'STOPPED'] as const) {
      useExecutionStore.setState({ executionStatus: terminal });
      capturedOnEvent!(evt(`r-${terminal}`, 'EXECUTION_STARTED'));
      expect(useExecutionStore.getState().executionStatus).toBe(terminal);
    }
  });

  it('updates node runtime status from NODE_ENTERED / NODE_EXITED / ERROR', async () => {
    await mountAndReady();
    capturedOnEvent!(evt('n1', 'NODE_ENTERED', { nodeId: 'a' }));
    expect(useExecutionStore.getState().nodeStatuses.a).toBe('running');
    capturedOnEvent!(evt('n2', 'NODE_EXITED', { nodeId: 'a' }));
    expect(useExecutionStore.getState().nodeStatuses.a).toBe('passed');
    capturedOnEvent!(evt('n3', 'ERROR', { nodeId: 'b' }));
    expect(useExecutionStore.getState().nodeStatuses.b).toBe('failed');
  });

  it('disposes the subscription on unmount', async () => {
    const { unmount } = await mountAndReady();
    unmount();
    await waitFor(() => expect(disposer).toHaveBeenCalled());
  });
});
