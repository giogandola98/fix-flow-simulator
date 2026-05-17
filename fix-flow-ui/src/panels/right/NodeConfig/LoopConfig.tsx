import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface LoopCfg { targetNodeId?: string; iterations?: number; }

export function LoopConfig({ node }: { node: ScenarioNode }) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as LoopCfg) ?? {};

  const patch = (p: Partial<LoopCfg>) => updateNode(node.id, { config: { ...cfg, ...p } });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Target Node (loop body)</label>
        <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''} onChange={(e) => patch({ targetNodeId: e.target.value })}>
          <option value="">-- select --</option>
          {allNodes.filter((n) => n.id !== node.id).map((n) => (
            <option key={n.id} value={n.id}>{n.name} ({n.type})</option>
          ))}
        </select>
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Iterations</label>
        <input type="number" min={1} className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.iterations ?? 1}
          onChange={(e) => patch({ iterations: Number(e.target.value) })} />
      </div>
    </div>
  );
}
