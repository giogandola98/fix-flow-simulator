import { Handle, Position, NodeProps } from '@xyflow/react';

interface RoutingRule { ruleId: string; label: string; matchers: Record<string, string>; targetNodeId: string; }

export function RouteFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { rules?: RoutingRule[] }) ?? {};
  const rules: RoutingRule[] = cfg.rules ?? [];
  const status = data.status as string | undefined;

  const ringColor =
    status === 'running' ? 'animate-pulse ring-2 ring-green-400'
    : status === 'passed' ? 'ring-2 ring-green-500'
    : status === 'failed' ? 'ring-2 ring-red-500'
    : selected ? 'ring-2 ring-blue-400' : '';

  return (
    <div
      className={`relative rounded ${ringColor}`}
      style={{ background: '#1a1d27', border: '2px solid #ec4899', minWidth: 180, padding: 8 }}
    >
      <Handle type="target" position={Position.Top} />
      <div className="text-gray-100">
        <div className="text-[10px] uppercase tracking-wide opacity-70">ROUTE_FIX</div>
        <div className="text-sm font-medium truncate" title={data.label as string}>{data.label as string}</div>
        {rules.length > 0 && (
          <div className="mt-1 space-y-0.5">
            {rules.map((r) => (
              <div key={r.ruleId} className="text-[10px] text-pink-300 truncate">
                {r.label || r.ruleId}
              </div>
            ))}
          </div>
        )}
      </div>
      {rules.length === 0 ? (
        <Handle type="source" position={Position.Bottom} id="default" />
      ) : (
        rules.map((r, i) => (
          <Handle
            key={r.ruleId}
            type="source"
            position={Position.Bottom}
            id={r.ruleId}
            style={{ left: `${((i + 1) / (rules.length + 1)) * 100}%` }}
          />
        ))
      )}
    </div>
  );
}
