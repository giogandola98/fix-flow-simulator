import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function WaitNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { value?: number; unit?: string }) ?? {};
  return (
    <BaseNode label={data.label as string} type="WAIT" borderColor="#6b7280" selected={selected} status={data.status as string}>
      <div className="text-gray-400">{cfg.value ?? '?'} {cfg.unit ?? ''}</div>
    </BaseNode>
  );
}
