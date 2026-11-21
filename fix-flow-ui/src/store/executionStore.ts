import { create } from 'zustand';
import { ExecutionEvent, ExecutionStatus, FIXMessage } from '../types';
import { getOperatorPrompt, type OperatorPromptData } from '../api/executions';

export type NodeRuntimeStatus = 'idle' | 'running' | 'passed' | 'failed';
type Terminal = 'PASSED' | 'FAILED' | 'STOPPED';

interface ExecutionState {
  activeExecutionId: string | null;
  /**
   * Every execution started by the current run, in order: one id for a single run, one per row
   * for an all-rows run. The rows run one after the other on the server; the GUI follows them by
   * moving {@link activeExecutionId} to the next id when the active one finishes.
   */
  executionIds: string[];
  /** Ids of the current run that have finished, with their own status. */
  finishedStatuses: Record<string, Terminal>;
  executionStatus: ExecutionStatus | 'IDLE';
  events: ExecutionEvent[];
  messages: FIXMessage[];
  // O(1) dedup sets — absorb backfill duplicates without scanning the arrays.
  seenEventIds: Set<string>;
  seenMsgIds: Set<string>;
  nodeStatuses: Record<string, NodeRuntimeStatus>;
  startedAt: string | null;
  endedAt: string | null;
  /** The OPERATOR prompt the active execution (or one of its children) is waiting on, if any. */
  operatorPrompt: OperatorPromptData | null;
  setOperatorPrompt: (p: OperatorPromptData | null) => void;
  /** Reads the open prompt from the server; a result older than the latest read or set is dropped. */
  refreshOperatorPrompt: (executionId: string) => Promise<void>;
  setActiveExecution: (id: string | null) => void;
  updateStatus: (status: ExecutionStatus | 'IDLE') => void;
  /**
   * Records the end of one execution. While later rows of the run remain, the active id moves to
   * the next row and the run stays RUNNING; after the last row the run's status is FAILED if any
   * row failed, else STOPPED if any row was stopped, else PASSED. An id that is not the active one
   * (a replayed event) is ignored once the run has moved past it.
   */
  finishExecution: (executionId: string, status: Terminal, at: string) => void;
  /** Ids of the current run that have not finished yet (what Stop has to stop). */
  unfinishedExecutionIds: () => string[];
  addEvent: (event: ExecutionEvent) => void;
  addMessage: (msg: FIXMessage) => void;
  setNodeStatus: (nodeId: string, status: NodeRuntimeStatus) => void;
  setStartedAt: (iso: string) => void;
  setEndedAt: (iso: string) => void;
}

let operatorSeq = 0;

function batchStatus(statuses: Terminal[]): Terminal {
  if (statuses.includes('FAILED')) return 'FAILED';
  if (statuses.includes('STOPPED')) return 'STOPPED';
  return 'PASSED';
}

export const useExecutionStore = create<ExecutionState>((set, get) => ({
  activeExecutionId: null,
  executionIds: [],
  finishedStatuses: {},
  executionStatus: 'IDLE',
  events: [],
  messages: [],
  seenEventIds: new Set<string>(),
  seenMsgIds: new Set<string>(),
  nodeStatuses: {},
  startedAt: null,
  endedAt: null,
  operatorPrompt: null,
  setOperatorPrompt: (p) => { operatorSeq++; set({ operatorPrompt: p }); },
  refreshOperatorPrompt: async (executionId) => {
    const mine = ++operatorSeq;
    try {
      const p = (await getOperatorPrompt(executionId)) ?? null;
      if (mine === operatorSeq) set({ operatorPrompt: p });
    } catch { /* best-effort */ }
  },
  setActiveExecution: (id) => set({ activeExecutionId: id }),
  updateStatus: (status) => set({ executionStatus: status }),
  finishExecution: (executionId, status, at) => {
    operatorSeq++; // drop a prompt read still in flight for the row that just ended
    const s = get();
    const idx = s.executionIds.indexOf(executionId);
    if (idx < 0) {
      // Not part of a tracked run (single run started elsewhere): plain terminal status.
      set({ executionStatus: status, endedAt: at, operatorPrompt: null });
      return;
    }
    if (executionId !== s.activeExecutionId) return; // replay of a row already left behind
    const finishedStatuses = { ...s.finishedStatuses, [executionId]: status };
    const next = s.executionIds[idx + 1];
    if (next !== undefined) {
      // One atomic update: the subscription hook switches to the next row without ever seeing
      // a null id. The status is left as is (RUNNING, or STOPPED after a Stop click).
      set({ activeExecutionId: next, finishedStatuses, nodeStatuses: {}, operatorPrompt: null });
      return;
    }
    set({
      finishedStatuses,
      executionStatus: batchStatus(s.executionIds.map((id) => finishedStatuses[id] ?? status)),
      endedAt: at,
      operatorPrompt: null,
    });
  },
  unfinishedExecutionIds: () => {
    const s = get();
    if (s.executionIds.length === 0) return s.activeExecutionId ? [s.activeExecutionId] : [];
    return s.executionIds.filter((id) => !(id in s.finishedStatuses));
  },
  addEvent: (event) => set((s) => {
    if (s.seenEventIds.has(event.id)) return s;
    s.seenEventIds.add(event.id);
    return { events: [...s.events, event] };
  }),
  addMessage: (msg) => set((s) => {
    if (s.seenMsgIds.has(msg.id)) return s;
    s.seenMsgIds.add(msg.id);
    return { messages: [...s.messages, msg] };
  }),
  setNodeStatus: (nodeId, status) =>
    set((s) => ({ nodeStatuses: { ...s.nodeStatuses, [nodeId]: status } })),
  setStartedAt: (iso) => set({ startedAt: iso }),
  setEndedAt: (iso) => set({ endedAt: iso }),
}));
