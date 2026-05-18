import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface LoopCfg { targetNodeId?: string; iterations?: number; }
interface Props { node: ScenarioNode; }

export function LoopConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const nodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as LoopCfg) ?? {};

  const candidates = nodes.filter((n) => n.id !== node.id);

  const patchConfig = (patch: Partial<LoopCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Executes the target node exactly N times. All iterations must succeed — fails immediately on the first failure.
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Target Node
          <span title="The node this Loop block will execute repeatedly." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''}
          onChange={(e) => patchConfig({ targetNodeId: e.target.value || undefined })}
        >
          <option value="">-- select node --</option>
          {candidates.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
        </select>
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Iterations
          <span title="Number of times to execute the target node. Minimum 1." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input
          type="number"
          min={1}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.iterations ?? 1}
          onChange={(e) => patchConfig({ iterations: Math.max(1, Number(e.target.value)) })}
        />
      </div>
    </div>
  );
}
