import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { VarRefPanel } from './VarRefPanel';

interface DecisionCfg { condition?: string; }
interface Props { node: ScenarioNode; }

export function DecisionConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as DecisionCfg) ?? {};

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-yellow-300 bg-yellow-900/20 border border-yellow-800/40 rounded px-2 py-1.5">
        Evaluates a condition expression. True → success path (onSuccess). False → failure path (onFailure).
      </div>

      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      <div>
        <label className="text-[10px] text-gray-500">
          Condition
          <span title="Expression: LEFT OP RIGHT. Operators: == (exact match), != (not equal), contains (substring). Use {{node:id:tagN}} to reference FIX fields from previous nodes. Example: {{node:expect-er:tag39}} == 0" className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono"
          placeholder='{{node:expect-er:tag39}} == "0"'
          value={cfg.condition ?? ''}
          onChange={(e) => updateNode(node.id, { config: { ...cfg, condition: e.target.value } })} />
        <div className="text-[10px] text-gray-600 mt-0.5">Operators: <code>==</code> <code>!=</code> <code>contains</code></div>
      </div>

      <VarRefPanel />
    </div>
  );
}
