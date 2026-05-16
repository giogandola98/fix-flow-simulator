import { useScenarioStore } from '../../store/scenarioStore';
import { SendFIXConfig } from './NodeConfig/SendFIXConfig';
import { ExpectFIXConfig } from './NodeConfig/ExpectFIXConfig';
import { ValidateConfig } from './NodeConfig/ValidateConfig';
import { RetryConfig } from './NodeConfig/RetryConfig';

export function PropertiesPanel() {
  const selectedNodeId = useScenarioStore((s) => s.selectedNodeId);
  const node = useScenarioStore((s) =>
    selectedNodeId ? s.nodes.find((n) => n.id === selectedNodeId) ?? null : null,
  );

  return (
    <div className="p-2 overflow-y-auto border-b border-[#2a2d3a]">
      <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">Properties</div>
      {!node && <div className="text-xs text-gray-500 italic">Select a node to configure</div>}
      {node?.type === 'SEND_FIX' && <SendFIXConfig node={node} />}
      {node?.type === 'EXPECT_FIX' && <ExpectFIXConfig node={node} />}
      {node?.type === 'VALIDATE' && <ValidateConfig node={node} />}
      {(node?.type === 'RETRY' || node?.type === 'LOOP') && <RetryConfig node={node} />}
      {node && !['SEND_FIX', 'EXPECT_FIX', 'VALIDATE', 'RETRY', 'LOOP'].includes(node.type) && (
        <div className="text-xs text-gray-500 italic">No configuration available for {node.type}</div>
      )}
    </div>
  );
}
