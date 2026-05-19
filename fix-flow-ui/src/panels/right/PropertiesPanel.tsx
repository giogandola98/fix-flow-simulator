import { useTranslation } from 'react-i18next';
import { useScenarioStore } from '../../store/scenarioStore';
import { SendFIXConfig } from './NodeConfig/SendFIXConfig';
import { ExpectFIXConfig } from './NodeConfig/ExpectFIXConfig';
import { ValidateConfig } from './NodeConfig/ValidateConfig';
import { RetryConfig } from './NodeConfig/RetryConfig';
import { LoopConfig } from './NodeConfig/LoopConfig';
import { DelayConfig } from './NodeConfig/DelayConfig';
import { DecisionConfig } from './NodeConfig/DecisionConfig';
import { WaitConfig } from './NodeConfig/WaitConfig';
import { HttpRequestConfig } from './NodeConfig/HttpRequestConfig';
import { RouteFIXConfig } from './NodeConfig/RouteFIXConfig';

const NAME_ONLY_TYPES = ['START', 'END_PASS', 'END_FAIL'];

function NameOnlyConfig({ node }: { node: { id: string; name: string } }) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">{t('properties.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
    </div>
  );
}

export function PropertiesPanel() {
  const { t } = useTranslation();
  const selectedNodeId = useScenarioStore((s) => s.selectedNodeId);
  const node = useScenarioStore((s) =>
    selectedNodeId ? s.nodes.find((n) => n.id === selectedNodeId) ?? null : null,
  );

  return (
    <div className="p-2 border-b border-[#2a2d3a]">
      <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">{t('properties.title')}</div>
      {!node && <div className="text-xs text-gray-500 italic">{t('properties.noNode')}</div>}
      {node?.type === 'SEND_FIX' && <SendFIXConfig node={node} />}
      {node?.type === 'EXPECT_FIX' && <ExpectFIXConfig node={node} />}
      {node?.type === 'VALIDATE' && <ValidateConfig node={node} />}
      {node?.type === 'RETRY' && <RetryConfig node={node} />}
      {node?.type === 'LOOP' && <LoopConfig node={node} />}
      {node?.type === 'DELAY' && <DelayConfig node={node} />}
      {(node?.type === 'WAIT' || node?.type === 'TIMEOUT') && <WaitConfig node={node} />}
      {(node?.type === 'DECISION' || node?.type === 'BRANCH') && <DecisionConfig node={node} />}
      {node?.type === 'HTTP_REQUEST' && <HttpRequestConfig node={node} />}
      {node?.type === 'ROUTE_FIX' && <RouteFIXConfig node={node} />}
      {node && NAME_ONLY_TYPES.includes(node.type) && <NameOnlyConfig node={node} />}
    </div>
  );
}
