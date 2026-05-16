import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function StartNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="START"
      borderColor="#3b82f6"
      selected={selected}
      status={data.status as string}
      shape="circle"
      handles={{ target: false, source: true }}
    />
  );
}
