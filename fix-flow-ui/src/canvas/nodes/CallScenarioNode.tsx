import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';
import { useScenarioStore } from '../../store/scenarioStore';

export function CallScenarioNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  const scenarios = useScenarioStore((s) => s.scenarios);
  const targetId = cfg.targetScenarioId;
  const targetName = targetId
    ? (scenarios.find((s) => s.id === targetId)?.name ?? targetId)
    : '—';
  return (
    <BaseNode label={data.label as string} type="CALL_SCENARIO" borderColor="#8b5cf6" selected={selected} status={data.status as string}>
      <div className="text-purple-300 opacity-80 truncate max-w-[140px]" title={targetName}>
        {targetName}
      </div>
    </BaseNode>
  );
}
