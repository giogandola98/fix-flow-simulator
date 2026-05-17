import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface CorrelationCfg { sourceTag?: number; fromNode?: string; targetTag?: number; }
interface ExpectCfg { msgType?: string; correlation?: CorrelationCfg; }

export function ExpectFIXConfig({ node }: { node: ScenarioNode }) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as ExpectCfg) ?? {};
  const corr = cfg.correlation ?? {};

  const patchConfig = (patch: Partial<ExpectCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });
  const patchCorr = (patch: Partial<CorrelationCfg>) =>
    patchConfig({ correlation: { ...corr, ...patch } });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">MsgType (tag 35)</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''} onChange={(e) => patchConfig({ msgType: e.target.value })} />
      </div>
      <div className="border border-[#2a2d3a] rounded p-2">
        <div className="text-[10px] uppercase text-gray-500 mb-1">Correlation</div>
        <div>
          <label className="text-[10px] text-gray-500">Source Tag (in received)</label>
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.sourceTag ?? 0} onChange={(e) => patchCorr({ sourceTag: Number(e.target.value) })} />
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">From Node</label>
          <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.fromNode ?? ''} onChange={(e) => patchCorr({ fromNode: e.target.value })}>
            <option value="">-- select --</option>
            {allNodes.filter((n) => n.id !== node.id).map((n) => (
              <option key={n.id} value={n.id}>{n.name} ({n.type})</option>
            ))}
          </select>
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">Target Tag (in source node)</label>
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.targetTag ?? 0} onChange={(e) => patchCorr({ targetTag: Number(e.target.value) })} />
        </div>
      </div>
      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
