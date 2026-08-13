import { describe, it, expect, beforeEach } from 'vitest';
import { useScenarioStore } from './scenarioStore';
import { ScenarioNode, ScenarioEdge } from '../types';

const resetStore = () =>
  useScenarioStore.setState({
    scenarios: [],
    activeScenario: null,
    nodes: [],
    edges: [],
    selectedNodeId: null,
    isDirty: false,
  });

const node = (id: string, patch: Partial<ScenarioNode> = {}): ScenarioNode => ({
  id,
  name: id,
  type: 'SEND_FIX',
  config: {},
  ...patch,
});

describe('scenarioStore', () => {
  beforeEach(resetStore);

  it('addNode appends and marks dirty', () => {
    useScenarioStore.getState().addNode(node('a'));
    expect(useScenarioStore.getState().nodes.map((n) => n.id)).toEqual(['a']);
    expect(useScenarioStore.getState().isDirty).toBe(true);
  });

  it('updateNode patches matching node and marks dirty', () => {
    useScenarioStore.setState({ nodes: [node('a'), node('b')] });
    useScenarioStore.getState().updateNode('b', { name: 'renamed' });
    const b = useScenarioStore.getState().nodes.find((n) => n.id === 'b');
    expect(b?.name).toBe('renamed');
    expect(useScenarioStore.getState().isDirty).toBe(true);
  });

  it('removeNode drops node, its edges, and dangling references', () => {
    useScenarioStore.setState({
      nodes: [node('a', { onSuccess: 'b' }), node('b'), node('c', { onFailure: 'b' })],
      edges: [
        { from: 'a', to: 'b', label: 'success' },
        { from: 'c', to: 'b', label: 'failure' },
      ],
    });
    useScenarioStore.getState().removeNode('b');
    const st = useScenarioStore.getState();
    expect(st.nodes.map((n) => n.id)).toEqual(['a', 'c']);
    expect(st.nodes.find((n) => n.id === 'a')?.onSuccess).toBeUndefined();
    expect(st.nodes.find((n) => n.id === 'c')?.onFailure).toBeUndefined();
    expect(st.edges).toEqual([]);
    expect(st.isDirty).toBe(true);
  });

  it('setEdges replaces the edge array', () => {
    const edges: ScenarioEdge[] = [{ from: 'a', to: 'b', label: 'success' }];
    useScenarioStore.getState().setEdges(edges);
    expect(useScenarioStore.getState().edges).toEqual(edges);
  });

  it('addEdge appends and marks dirty', () => {
    useScenarioStore.getState().addEdge({ from: 'a', to: 'b', label: 'success' });
    expect(useScenarioStore.getState().edges).toHaveLength(1);
    expect(useScenarioStore.getState().isDirty).toBe(true);
  });

  it('removeEdge removes only the matching from/to/label triple', () => {
    useScenarioStore.setState({
      edges: [
        { from: 'a', to: 'b', label: 'success' },
        { from: 'a', to: 'c', label: 'failure' },
      ],
    });
    useScenarioStore.getState().removeEdge('a', 'b', 'success');
    expect(useScenarioStore.getState().edges).toEqual([{ from: 'a', to: 'c', label: 'failure' }]);
  });

  it('markClean clears dirty, markDirty sets it; setActiveScenario resets dirty', () => {
    useScenarioStore.getState().markDirty();
    expect(useScenarioStore.getState().isDirty).toBe(true);
    useScenarioStore.getState().markClean();
    expect(useScenarioStore.getState().isDirty).toBe(false);
    useScenarioStore.setState({ isDirty: true });
    useScenarioStore.getState().setActiveScenario({
      id: 's1', name: 'S', description: '', version: '1.0', sessionRef: '', nodeCount: 0,
    });
    expect(useScenarioStore.getState().isDirty).toBe(false);
  });
});
