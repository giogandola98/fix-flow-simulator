import { create } from 'zustand';
import { Scenario, ScenarioNode, ScenarioEdge } from '../types';

interface ScenarioState {
  scenarios: Scenario[];
  activeScenario: Scenario | null;
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  selectedNodeId: string | null;
  isDirty: boolean;
  setScenarios: (s: Scenario[]) => void;
  setActiveScenario: (s: Scenario | null) => void;
  setNodes: (nodes: ScenarioNode[]) => void;
  setEdges: (edges: ScenarioEdge[]) => void;
  updateNode: (id: string, patch: Partial<ScenarioNode>) => void;
  addNode: (node: ScenarioNode) => void;
  removeNode: (id: string) => void;
  addEdge: (edge: ScenarioEdge) => void;
  removeEdge: (from: string, to: string, label: string) => void;
  setSelectedNode: (id: string | null) => void;
  markDirty: () => void;
  markClean: () => void;
}

export const useScenarioStore = create<ScenarioState>((set) => ({
  scenarios: [],
  activeScenario: null,
  nodes: [],
  edges: [],
  selectedNodeId: null,
  isDirty: false,
  setScenarios: (scenarios) => set({ scenarios }),
  setActiveScenario: (activeScenario) => set({ activeScenario, isDirty: false }),
  setNodes: (nodes) => set({ nodes }),
  setEdges: (edges) => set({ edges }),
  updateNode: (id, patch) =>
    set((s) => ({
      nodes: s.nodes.map((n) => (n.id === id ? { ...n, ...patch } : n)),
      isDirty: true,
    })),
  addNode: (node) => set((s) => ({ nodes: [...s.nodes, node], isDirty: true })),
  removeNode: (id) =>
    set((s) => ({
      nodes: s.nodes
        .filter((n) => n.id !== id)
        .map((n) => {
          const cfg = n.config as Record<string, unknown>;
          const corr = cfg?.correlation as Record<string, unknown> | undefined;
          let next = { ...n };
          if (n.onSuccess === id) next = { ...next, onSuccess: undefined };
          if (n.onFailure === id) next = { ...next, onFailure: undefined };
          if (n.onTimeout === id) next = { ...next, onTimeout: undefined };
          if (n.timeout?.jumpTo === id)
            next = { ...next, timeout: { ...n.timeout!, jumpTo: undefined } };
          if (cfg?.targetNodeId === id)
            next = { ...next, config: { ...cfg, targetNodeId: undefined } };
          if (corr?.fromNode === id)
            next = { ...next, config: { ...cfg, correlation: { ...corr, fromNode: undefined } } };
          return next;
        }),
      edges: s.edges.filter((e) => e.from !== id && e.to !== id),
      isDirty: true,
    })),
  addEdge: (edge) => set((s) => ({ edges: [...s.edges, edge], isDirty: true })),
  removeEdge: (from, to, label) =>
    set((s) => ({
      edges: s.edges.filter((e) => !(e.from === from && e.to === to && e.label === label)),
      isDirty: true,
    })),
  setSelectedNode: (id) => set({ selectedNodeId: id }),
  markDirty: () => set({ isDirty: true }),
  markClean: () => set({ isDirty: false }),
}));
