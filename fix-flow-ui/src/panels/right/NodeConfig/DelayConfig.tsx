import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface DelayCfg { delayMs?: number; }

export function DelayConfig({ node }: { node: ScenarioNode }) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as DelayCfg) ?? {};

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Fixed sleep in milliseconds. Always continues on the success path — no timeout actions or branching.
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          Delay (ms)
          <span title="Duration to sleep in milliseconds before moving to the next node." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input type="number" min={0} className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.delayMs ?? 0}
          onChange={(e) => updateNode(node.id, { config: { ...cfg, delayMs: Number(e.target.value) } })} />
      </div>
    </div>
  );
}
