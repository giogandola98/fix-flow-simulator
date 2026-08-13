import { Handle, NodeProps, Position } from '@xyflow/react';
import { ringClass } from './ringClass';

export function DecisionNode({ data, selected }: NodeProps) {
  const condition = (data.config as { condition?: string })?.condition;
  const status = data.status as string;

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
        {condition && (
          <div
            className="text-[9px] font-mono text-amber-300 text-center mt-0.5"
            style={{ maxWidth: 76, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
            title={condition}
          >
            {condition}
          </div>
        )}
      </div>
      {/* success path — bottom of diamond */}
      <Handle type="source" position={Position.Bottom} id="success" />
      {/* failure path — right of diamond */}
      <Handle type="source" position={Position.Right} id="failure" />
    </div>
  );
}
