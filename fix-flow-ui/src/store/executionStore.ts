import { create } from 'zustand';
import { ExecutionEvent, ExecutionStatus, FIXMessage } from '../types';

export type NodeRuntimeStatus = 'idle' | 'running' | 'passed' | 'failed';

interface ExecutionState {
  activeExecutionId: string | null;
  executionStatus: ExecutionStatus | 'IDLE';
  events: ExecutionEvent[];
  messages: FIXMessage[];
  // O(1) dedup sets — absorb backfill duplicates without scanning the arrays.
  seenEventIds: Set<string>;
  seenMsgIds: Set<string>;
  nodeStatuses: Record<string, NodeRuntimeStatus>;
  startedAt: string | null;
  endedAt: string | null;
  setActiveExecution: (id: string | null) => void;
  updateStatus: (status: ExecutionStatus | 'IDLE') => void;
  addEvent: (event: ExecutionEvent) => void;
  addMessage: (msg: FIXMessage) => void;
  setNodeStatus: (nodeId: string, status: NodeRuntimeStatus) => void;
  setStartedAt: (iso: string) => void;
  setEndedAt: (iso: string) => void;
}

export const useExecutionStore = create<ExecutionState>((set) => ({
  activeExecutionId: null,
  executionStatus: 'IDLE',
  events: [],
  messages: [],
  seenEventIds: new Set<string>(),
  seenMsgIds: new Set<string>(),
  nodeStatuses: {},
  startedAt: null,
  endedAt: null,
  setActiveExecution: (id) => set({ activeExecutionId: id }),
  updateStatus: (status) => set({ executionStatus: status }),
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
