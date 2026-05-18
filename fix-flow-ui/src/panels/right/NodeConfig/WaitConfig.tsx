import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

export function WaitConfig({ node }: { node: ScenarioNode }) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        {t('nodeConfig.wait.desc')}
      </div>
      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <TimeoutConfig
        value={node.timeout}
        onChange={(t) => updateNode(node.id, { timeout: t })}
        currentNodeId={node.id}
      />
    </div>
  );
}
