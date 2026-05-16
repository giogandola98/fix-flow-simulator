import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function EndPassNode({ data, selected }: NodeProps) {
  return (
    <BaseNode label={data.label as string} type="END_PASS" borderColor="#22c55e" selected={selected} status={data.status as string} filled handles={{ target: true, source: false }} />
  );
}
