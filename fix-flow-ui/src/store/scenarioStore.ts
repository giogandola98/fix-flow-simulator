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
      nodes: s.nodes.filter((n) => n.id !== id),
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
