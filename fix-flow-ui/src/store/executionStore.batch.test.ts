import { describe, it, expect, beforeEach } from 'vitest';
import { useExecutionStore } from './executionStore';

const startBatch = (ids: string[]) =>
  useExecutionStore.setState({
    activeExecutionId: ids[0],
    executionIds: ids,
    finishedStatuses: {},
    executionStatus: 'RUNNING',
    nodeStatuses: {},
    startedAt: null,
    endedAt: null,
    operatorPrompt: null,
  });

describe('executionStore all-rows batch', () => {
  beforeEach(() => startBatch(['r0', 'r1', 'r2']));

  it('moves the active id to the next row and stays RUNNING until the last row ends', () => {
    const { finishExecution } = useExecutionStore.getState();
    useExecutionStore.setState({ nodeStatuses: { n1: 'passed' } });

    finishExecution('r0', 'PASSED', 't0');
    let s = useExecutionStore.getState();
    expect(s.activeExecutionId).toBe('r1');
    expect(s.executionStatus).toBe('RUNNING');
    expect(s.nodeStatuses).toEqual({});
    expect(s.endedAt).toBeNull();

    finishExecution('r1', 'PASSED', 't1');
    s = useExecutionStore.getState();
    expect(s.activeExecutionId).toBe('r2');
    expect(s.executionStatus).toBe('RUNNING');

    finishExecution('r2', 'PASSED', 't2');
    s = useExecutionStore.getState();
    expect(s.activeExecutionId).toBe('r2');
    expect(s.executionStatus).toBe('PASSED');
    expect(s.endedAt).toBe('t2');
  });

  it('ends FAILED when any row failed, even if the last one passed', () => {
    const { finishExecution } = useExecutionStore.getState();
    finishExecution('r0', 'PASSED', 't0');
    finishExecution('r1', 'FAILED', 't1');
    expect(useExecutionStore.getState().executionStatus).toBe('RUNNING');
    finishExecution('r2', 'PASSED', 't2');
    expect(useExecutionStore.getState().executionStatus).toBe('FAILED');
  });

  it('ends STOPPED when a row was stopped and none failed', () => {
    const { finishExecution } = useExecutionStore.getState();
    finishExecution('r0', 'PASSED', 't0');
    finishExecution('r1', 'STOPPED', 't1');
    finishExecution('r2', 'STOPPED', 't2');
    expect(useExecutionStore.getState().executionStatus).toBe('STOPPED');
  });

  it('ignores a replayed finish of a row already left behind', () => {
    const { finishExecution } = useExecutionStore.getState();
    finishExecution('r0', 'PASSED', 't0');
    finishExecution('r0', 'PASSED', 't0');
    expect(useExecutionStore.getState().activeExecutionId).toBe('r1');
  });

  it('lists the rows that have not ended as unfinished', () => {
    const { finishExecution, unfinishedExecutionIds } = useExecutionStore.getState();
    expect(unfinishedExecutionIds()).toEqual(['r0', 'r1', 'r2']);
    finishExecution('r0', 'PASSED', 't0');
    expect(useExecutionStore.getState().unfinishedExecutionIds()).toEqual(['r1', 'r2']);
  });

  it('a single run (one id) ends with its own status', () => {
    startBatch(['only']);
    useExecutionStore.getState().finishExecution('only', 'STOPPED', 't');
    expect(useExecutionStore.getState().executionStatus).toBe('STOPPED');
  });
});
