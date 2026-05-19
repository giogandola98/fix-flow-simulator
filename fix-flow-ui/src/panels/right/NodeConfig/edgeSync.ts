import { ScenarioEdge } from '../../../types';

export function syncTargetEdge(
  nodeId: string,
  targetNodeId: string | undefined,
  edges: ScenarioEdge[],
  setEdges: (e: ScenarioEdge[]) => void,
) {
  const without = edges.filter((e) => !(e.from === nodeId && e.label === 'target'));
  if (targetNodeId) {
    setEdges([...without, { from: nodeId, to: targetNodeId, label: 'target' }]);
  } else {
    setEdges(without);
  }
}
