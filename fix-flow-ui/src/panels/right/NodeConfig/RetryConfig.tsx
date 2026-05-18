import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface RetryCfg { targetNodeId?: string; }
interface Props { node: ScenarioNode; }

export function RetryConfig({ node }: Props) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  const nodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as RetryCfg) ?? {};
  const policy = node.retryPolicy ?? { maxAttempts: 1, delayMs: 0 };

  const patchConfig = (patch: Partial<RetryCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const patchPolicy = (patch: Partial<typeof policy>) =>
    updateNode(node.id, { retryPolicy: { ...policy, ...patch } });

  const candidates = nodes.filter((n) => n.id !== node.id);

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        {t('nodeConfig.retry.desc')}
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
          <span title="The node this Retry block will re-execute on failure." className="ml-1 text-gray-600 cursor-help">?</span>
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
          {t('nodeConfig.retry.maxAttempts')}
          <span title="Maximum number of times to try before giving up. Minimum 1." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input
          type="number"
          min={1}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.maxAttempts}
          onChange={(e) => patchPolicy({ maxAttempts: Math.max(1, Number(e.target.value)) })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.retry.delayMs')}
          <span title="Milliseconds to wait between attempts. 0 = retry immediately." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input
          type="number"
          min={0}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.delayMs}
          onChange={(e) => patchPolicy({ delayMs: Math.max(0, Number(e.target.value)) })}
        />
      </div>
    </div>
  );
}
