import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function EndFailNode({ data, selected }: NodeProps) {
  return (
    <BaseNode label={data.label as string} type="END_FAIL" borderColor="#ef4444" selected={selected} status={data.status as string} filled handles={{ target: true, source: false }} />
  );
}
