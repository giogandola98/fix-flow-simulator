import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';
import { colors } from '../../theme/colors';

export function OperatorNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { instruction?: string }) ?? {};
  return (
    <BaseNode label={data.label as string} type="OPERATOR" borderColor={colors.node.OPERATOR}
      selected={selected} status={data.status as string} failureHandle>
      <div className="text-rose-300 opacity-80 truncate max-w-[140px]" title={cfg.instruction}>
        {cfg.instruction || '-'}
      </div>
    </BaseNode>
  );
}
