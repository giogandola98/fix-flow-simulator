import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function DecisionNode({ data, selected }: NodeProps) {
  return (
    <BaseNode label={data.label as string} type="DECISION" borderColor="#f97316" selected={selected} status={data.status as string} shape="diamond" />
  );
}
