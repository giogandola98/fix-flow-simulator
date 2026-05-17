import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function HttpRequestNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  return (
    <BaseNode label={data.label as string} type="HTTP_REQUEST" borderColor="#f97316" selected={selected} status={data.status as string}>
      <div className="text-gray-400">
        <span className="text-orange-400 font-bold">{cfg.method ?? 'GET'}</span>{' '}
        <span className="opacity-70 truncate block max-w-[140px]">{cfg.url ?? '—'}</span>
      </div>
    </BaseNode>
  );
}
