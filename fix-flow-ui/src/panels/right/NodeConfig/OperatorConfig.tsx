import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface OperatorCfg { instruction?: string; confirmLabel?: string; failLabel?: string; }
interface Props { node: ScenarioNode; }

export function OperatorConfig({ node }: Props) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  const liveNode = useScenarioStore((s) => s.nodes.find((n) => n.id === node.id)) ?? node;
  const cfg = (liveNode.config as OperatorCfg) ?? {};

  const patchConfig = (patch: Partial<OperatorCfg>) => {
    const next: Record<string, unknown> = { ...cfg, ...patch };
    for (const k of Object.keys(patch)) if (!next[k]) delete next[k];
    updateNode(node.id, { config: next });
  };

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={liveNode.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      <p className="text-[10px] text-gray-500">{t('nodeConfig.operator.desc')}</p>

      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.operator.instruction')}</label>
        <textarea data-testid="operator-instruction" rows={4}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.instruction ?? ''}
          onChange={(e) => patchConfig({ instruction: e.target.value })} />
        <p className="text-[10px] text-gray-500">{t('nodeConfig.operator.instructionHint')}</p>
      </div>

      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.operator.confirmLabel')}</label>
        <input data-testid="operator-confirm-label" type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.confirmLabel ?? ''}
          onChange={(e) => patchConfig({ confirmLabel: e.target.value })} />
      </div>

      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.operator.failLabel')}</label>
        <input data-testid="operator-fail-label" type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.failLabel ?? ''}
          onChange={(e) => patchConfig({ failLabel: e.target.value })} />
      </div>

      <TimeoutConfig value={liveNode.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
