import { describe, it, expect, beforeEach } from 'vitest';
import { useExecutionStore } from './executionStore';
import { ExecutionEvent, FIXMessage } from '../types';

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

const mkEvent = (id: string): ExecutionEvent => ({
  id,
  executionId: 'exec-1',
  type: 'NODE_ENTERED',
  timestamp: '2026-01-01T00:00:00Z',
});

const mkMsg = (id: string): FIXMessage => ({
  id,
  executionId: 'exec-1',
  direction: 'OUTBOUND',
  rawFix: '35=D',
  fields: {},
  receivedAt: '2026-01-01T00:00:00Z',
});

describe('executionStore', () => {
  beforeEach(resetStore);

  it('addEvent dedups by id (seen-set keeps one)', () => {
    const { addEvent } = useExecutionStore.getState();
    addEvent(mkEvent('e1'));
    addEvent(mkEvent('e1'));
    addEvent(mkEvent('e2'));
    expect(useExecutionStore.getState().events.map((e) => e.id)).toEqual(['e1', 'e2']);
    expect(useExecutionStore.getState().seenEventIds.has('e1')).toBe(true);
  });

  it('addMessage dedups by id (seen-set keeps one)', () => {
    const { addMessage } = useExecutionStore.getState();
    addMessage(mkMsg('m1'));
    addMessage(mkMsg('m1'));
    addMessage(mkMsg('m2'));
    expect(useExecutionStore.getState().messages.map((m) => m.id)).toEqual(['m1', 'm2']);
  });

  it('updateStatus sets the execution status', () => {
    useExecutionStore.getState().updateStatus('RUNNING');
    expect(useExecutionStore.getState().executionStatus).toBe('RUNNING');
    useExecutionStore.getState().updateStatus('PASSED');
    expect(useExecutionStore.getState().executionStatus).toBe('PASSED');
  });

  it('setNodeStatus updates individual node runtime status', () => {
    useExecutionStore.getState().setNodeStatus('n1', 'running');
    useExecutionStore.getState().setNodeStatus('n2', 'passed');
    useExecutionStore.getState().setNodeStatus('n1', 'failed');
    expect(useExecutionStore.getState().nodeStatuses).toEqual({ n1: 'failed', n2: 'passed' });
  });

  it('seen-set reset clears dedup memory so the same id can be re-added', () => {
    const { addEvent } = useExecutionStore.getState();
    addEvent(mkEvent('e1'));
    expect(useExecutionStore.getState().events).toHaveLength(1);
    resetStore();
    useExecutionStore.getState().addEvent(mkEvent('e1'));
    expect(useExecutionStore.getState().events).toHaveLength(1);
    expect(useExecutionStore.getState().events[0].id).toBe('e1');
  });

  it('setStartedAt / setEndedAt store timestamps', () => {
    useExecutionStore.getState().setStartedAt('t-start');
    useExecutionStore.getState().setEndedAt('t-end');
    expect(useExecutionStore.getState().startedAt).toBe('t-start');
    expect(useExecutionStore.getState().endedAt).toBe('t-end');
  });
});
