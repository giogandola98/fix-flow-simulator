import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';
import { TimeoutConfig } from '../../types';

interface WaitCfg {
  /** DELAY blocks keep their duration here — it is what DelayHandler reads. */
  delayMs?: number;
  /** Hand-written scenarios sometimes carry the duration inline instead of in `timeout`. */
  value?: number;
  unit?: string;
}

/**
 * Serves WAIT, DELAY and TIMEOUT. Each keeps its duration in a different place, and the node used
 * to read `config.value` — which no editor-created block ever has, so every WAIT block showed `?`
 * however it was configured (issue #89). WAIT and TIMEOUT keep it in `node.timeout`, edited through
 * the shared timeout panel and read by `WaitHandler`; DELAY keeps `config.delayMs`.
 */
export function describeDuration(
  type: string | undefined,
  config: WaitCfg | undefined,
  timeout: TimeoutConfig | undefined,
): string {
  const cfg = config ?? {};
  if (type === 'DELAY') {
    return typeof cfg.delayMs === 'number' ? `${cfg.delayMs} ms` : '—';
  }
  if (timeout && typeof timeout.value === 'number') {
    return `${timeout.value} ${timeout.unit ?? ''}`.trim();
  }
  if (typeof cfg.value === 'number') {
    return `${cfg.value} ${cfg.unit ?? ''}`.trim();
  }
  return '—';
}

export function WaitNode({ data, type, selected }: NodeProps) {
  const duration = describeDuration(
    type,
    data.config as WaitCfg | undefined,
    data.timeout as TimeoutConfig | undefined,
  );
  return (
    <BaseNode
      label={data.label as string}
      type={type ?? 'WAIT'}
      borderColor="#6b7280"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">{duration}</div>
    </BaseNode>
  );
}
