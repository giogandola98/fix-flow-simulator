import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function SendFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  return (
    <BaseNode label={data.label as string} type="SEND_FIX" borderColor="#22c55e" selected={selected} status={data.status as string}>
      <div className="text-gray-400">MsgType: <span className="text-green-400">{cfg.msgType ?? '?'}</span></div>
    </BaseNode>
  );
}
