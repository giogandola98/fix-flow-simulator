import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { syncTargetEdge } from './edgeSync';

interface LoopCfg { targetNodeId?: string; iterations?: number; }
interface Props { node: ScenarioNode; }

export function LoopConfig({ node }: Props) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const cfg = (node.config as LoopCfg) ?? {};

  const candidates = nodes.filter((n) => n.id !== node.id);

  const patchConfig = (patch: Partial<LoopCfg>) => {
    const next = { ...cfg, ...patch };
    updateNode(node.id, { config: next });
    syncTargetEdge(node.id, next.targetNodeId, edges, setEdges);
  };

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        {t('nodeConfig.loop.desc')}
      </div>
      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.targetNode')}
          <span title="The node this Loop block will execute repeatedly." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''}
          onChange={(e) => patchConfig({ targetNodeId: e.target.value || undefined })}
        >
          <option value="">{t('nodeConfig.selectNode')}</option>
          {candidates.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
        </select>
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.loop.iterations')}
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
