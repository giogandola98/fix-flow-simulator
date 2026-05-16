import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function ValidateNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { rules?: unknown[] }) ?? {};
  const count = Array.isArray(cfg.rules) ? cfg.rules.length : 0;
  return (
    <BaseNode label={data.label as string} type="VALIDATE" borderColor="#a855f7" selected={selected} status={data.status as string}>
      <div className="text-gray-400">Rules: <span className="text-purple-400">{count}</span></div>
    </BaseNode>
  );
}
