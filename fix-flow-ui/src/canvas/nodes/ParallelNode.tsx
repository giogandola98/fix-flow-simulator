import { NodeProps } from '@xyflow/react';
import { useTranslation } from 'react-i18next';
import { BaseNode } from './BaseNode';
import { useScenarioStore } from '../../store/scenarioStore';
import { colors } from '../../theme/colors';

export function ParallelNode({ data, selected }: NodeProps) {
  const { t } = useTranslation();
  const cfg = (data.config as { targetScenarioId?: string; rows?: 'all' | number[]; maxConcurrency?: number }) ?? {};
  const scenarios = useScenarioStore((s) => s.scenarios);
  const targetName = cfg.targetScenarioId
    ? (scenarios.find((s) => s.id === cfg.targetScenarioId)?.name ?? cfg.targetScenarioId)
    : '—';
  const rows = Array.isArray(cfg.rows)
    ? t('nodeConfig.parallel.rowsSummary', { rows: cfg.rows.join(', ') })
    : t('nodeConfig.parallel.allRows');
  return (
    <BaseNode label={data.label as string} type="PARALLEL" borderColor={colors.node.PARALLEL}
      selected={selected} status={data.status as string} failureHandle>
      <div className="text-teal-300 opacity-80 truncate max-w-[140px]" title={targetName}>
        {targetName}
      </div>
      <div className="text-[10px] text-gray-400 truncate max-w-[140px]">
        {rows}{cfg.maxConcurrency ? ` · ≤${cfg.maxConcurrency}` : ''}
      </div>
    </BaseNode>
  );
}
