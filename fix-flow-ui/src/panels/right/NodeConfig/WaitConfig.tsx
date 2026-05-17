import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

export function WaitConfig({ node }: { node: ScenarioNode }) {
  const updateNode = useScenarioStore((s) => s.updateNode);

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <TimeoutConfig
        value={node.timeout}
        onChange={(t) => updateNode(node.id, { timeout: t })}
        currentNodeId={node.id}
      />
    </div>
  );
}
