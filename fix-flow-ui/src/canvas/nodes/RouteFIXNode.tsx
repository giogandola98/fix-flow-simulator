import { Handle, Position, NodeProps } from '@xyflow/react';
import { ringClass } from './ringClass';

interface RoutingRule { ruleId: string; label: string; matchers: Array<{tag: number; value: string}>; targetNodeId: string; }

export function RouteFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { rules?: RoutingRule[] }) ?? {};
  const rules: RoutingRule[] = cfg.rules ?? [];
  const status = data.status as string | undefined;

  const ringColor = ringClass(status, selected);

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
            {rules.map((r) => {
              const matchStr = (r.matchers ?? []).map((m) => `${m.tag}=${m.value}`).join(' ');
              return (
                <div key={r.ruleId} className="text-[10px] text-pink-300 truncate">
                  {r.label || r.ruleId}{matchStr ? ` (${matchStr})` : ''}
                </div>
              );
            })}
          </div>
        )}
      </div>
      {rules.length === 0 ? (
        <Handle type="source" position={Position.Bottom} id="default" />
      ) : (
        <>
          <div className="relative" style={{ height: 18 }}>
            {rules.map((r, i) => (
              <span
                key={r.ruleId}
                className="absolute text-[9px] text-pink-200 whitespace-nowrap"
                style={{ left: `${((i + 1) / (rules.length + 1)) * 100}%`, transform: 'translateX(-50%)', bottom: 0 }}
              >
                {r.label || '…'}
              </span>
            ))}
          </div>
          {rules.map((r, i) => (
            <Handle
              key={r.ruleId}
              type="source"
              position={Position.Bottom}
              id={r.ruleId}
              style={{ left: `${((i + 1) / (rules.length + 1)) * 100}%` }}
            />
          ))}
        </>
      )}
    </div>
  );
}
