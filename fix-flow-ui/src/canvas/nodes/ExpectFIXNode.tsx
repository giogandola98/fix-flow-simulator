import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function ExpectFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  return (
    <BaseNode label={data.label as string} type="EXPECT_FIX" borderColor="#eab308" selected={selected} status={data.status as string}>
      <div className="text-gray-400">MsgType: <span className="text-yellow-400">{cfg.msgType ?? '?'}</span></div>
    </BaseNode>
  );
}
