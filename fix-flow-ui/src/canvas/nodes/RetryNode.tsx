import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function RetryNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { maxAttempts?: number }) ?? {};
  return (
    <BaseNode label={data.label as string} type="RETRY" borderColor="#06b6d4" selected={selected} status={data.status as string}>
      <div className="text-gray-400">Max attempts: <span className="text-cyan-400">{cfg.maxAttempts ?? '?'}</span></div>
    </BaseNode>
  );
}
