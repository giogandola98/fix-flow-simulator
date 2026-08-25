import { Handle, NodeProps, Position } from '@xyflow/react';
import { ringClass } from './ringClass';
import { useDynamicHandles } from './useDynamicHandles';

interface Branch { branchId: string; label: string; conditions?: string[]; targetNodeId?: string }

export function DecisionNode({ id, data, selected }: NodeProps) {
  const cfg = (data.config as { condition?: string; branches?: Branch[] }) ?? {};
  const branches: Branch[] = cfg.branches ?? [];
  const status = data.status as string;

  // Branch handles appear after the node was first measured; without this React Flow does not
  // know they exist and refuses to draw the edges bound to them (issue #92).
  useDynamicHandles(id, branches.map((b) => b.branchId));

  const ringColor = ringClass(status, selected);

  return (
    <div
      className={`relative rotate-45 ${ringColor}`}
      style={{ width: 110, height: 110, background: '#1a1d27', border: '2px solid #f97316' }}
    >
      <Handle type="target" position={Position.Top} />
      <div className="-rotate-45 flex flex-col items-center justify-center h-full gap-0.5 pointer-events-none">
        <div className="text-[9px] uppercase tracking-widest text-orange-400 opacity-90">if</div>
        <div
          className="text-[11px] font-medium text-center text-gray-100 leading-tight"
          style={{ maxWidth: 72 }}
        >
          {data.label as string}
        </div>
        {branches.length > 0 ? (
          <div className="text-[9px] text-amber-300 text-center mt-0.5">
            {branches.length} branches
          </div>
        ) : (
          cfg.condition && (
            <div
              className="text-[9px] font-mono text-amber-300 text-center mt-0.5"
              style={{ maxWidth: 76, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              title={cfg.condition}
            >
              {cfg.condition}
            </div>
          )
        )}
      </div>
      {branches.length === 0 ? (
        <>
          {/* success path — bottom of diamond */}
          <Handle type="source" position={Position.Bottom} id="success" />
          {/* failure path — right of diamond */}
          <Handle type="source" position={Position.Right} id="failure" />
        </>
      ) : (
        <>
          {/* One handle per branch, spread along the bottom edge, exactly as ROUTE_FIX does for
              its rules. Labels sit outside the rotated diamond so they read horizontally. */}
          {branches.map((b, i) => (
            <Handle
              key={b.branchId}
              type="source"
              position={Position.Bottom}
              id={b.branchId}
              title={b.label || b.branchId}
              style={{ left: `${((i + 1) / (branches.length + 1)) * 100}%` }}
            />
          ))}
          <div
            className="absolute -rotate-45 pointer-events-none"
            style={{ left: -20, right: -20, bottom: -34, height: 16 }}
          >
            {branches.map((b, i) => (
              <span
                key={b.branchId}
                className="absolute text-[9px] text-amber-200 whitespace-nowrap"
                style={{ left: `${((i + 1) / (branches.length + 1)) * 100}%`, transform: 'translateX(-50%)' }}
              >
                {b.label || '…'}
              </span>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
