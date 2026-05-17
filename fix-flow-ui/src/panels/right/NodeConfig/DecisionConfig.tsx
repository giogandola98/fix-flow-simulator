import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface DecisionCfg { condition?: string; }
interface Props { node: ScenarioNode; }

export function DecisionConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as DecisionCfg) ?? {};

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Condition</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono"
          placeholder='{{node:id:tag39}} == "0"'
          value={cfg.condition ?? ''}
          onChange={(e) => updateNode(node.id, { config: { ...cfg, condition: e.target.value } })} />
        <div className="text-[10px] text-gray-600 mt-0.5">Operators: == != contains</div>
      </div>
    </div>
  );
}
