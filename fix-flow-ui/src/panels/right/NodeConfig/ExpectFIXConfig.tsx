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
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        Waits for an inbound FIX message matching the criteria. Stores matched fields for downstream VALIDATE nodes or cross-node references.
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          MsgType (tag 35)
          <span title="Required. FIX tag 35 of the message to wait for. e.g. 8 = Execution Report, W = Market Data Snapshot." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''} onChange={(e) => patchConfig({ msgType: e.target.value })} />
      </div>
      <div className="border border-[#2a2d3a] rounded p-2">
        <div className="text-[10px] uppercase text-gray-500 mb-1">
          Correlation
          <span title="Links this Expect block to a previously sent message. The engine only accepts an inbound message whose Source Tag value matches the Target Tag value from the referenced send node." className="ml-1 normal-case text-gray-600 cursor-help">?</span>
        </div>
        <div className="text-[10px] text-gray-500 italic mb-2">
          Optional. Use to match a reply back to a specific sent order — e.g. match ClOrdID (tag 11) in the reply against tag 11 sent in the order node.
        </div>
        <div>
          <label className="text-[10px] text-gray-500">
            Source Tag (in received message)
            <span title="The FIX tag number in the inbound message whose value is checked for correlation. e.g. 11 for ClOrdID." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.sourceTag ?? 0} onChange={(e) => patchCorr({ sourceTag: Number(e.target.value) })} />
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">
            From Node
            <span title="The Send FIX node whose outbound tag value is used as the expected correlation value." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.fromNode ?? ''} onChange={(e) => patchCorr({ fromNode: e.target.value })}>
            <option value="">-- none --</option>
            {allNodes.filter((n) => n.id !== node.id).map((n) => (
              <option key={n.id} value={n.id}>{n.name}</option>
            ))}
          </select>
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">
            Target Tag (in send node)
            <span title="The FIX tag number in the referenced send node whose outbound value must match the Source Tag in the reply." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.targetTag ?? 0} onChange={(e) => patchCorr({ targetTag: Number(e.target.value) })} />
        </div>
      </div>
      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
