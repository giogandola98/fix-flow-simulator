import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface RetryCfg { targetNodeId?: string; }

export function RetryConfig({ node }: { node: ScenarioNode }) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as RetryCfg) ?? {};
  const policy = node.retryPolicy ?? { maxAttempts: 3, delayMs: 1000 };

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Max Attempts</label>
        <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.maxAttempts}
          onChange={(e) => updateNode(node.id, { retryPolicy: { ...policy, maxAttempts: Number(e.target.value) } })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Delay (ms)</label>
        <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.delayMs}
          onChange={(e) => updateNode(node.id, { retryPolicy: { ...policy, delayMs: Number(e.target.value) } })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Target Node</label>
        <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''} onChange={(e) => updateNode(node.id, { config: { ...cfg, targetNodeId: e.target.value } })}>
          <option value="">-- select --</option>
          {allNodes.filter((n) => n.id !== node.id).map((n) => (
            <option key={n.id} value={n.id}>{n.name}</option>
          ))}
        </select>
      </div>
    </div>
  );
}
