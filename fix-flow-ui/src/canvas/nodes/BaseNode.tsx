import { Handle, Position } from '@xyflow/react';
import { ReactNode } from 'react';
import { ringClass } from './ringClass';

interface Props {
  label: string;
  type: string;
  borderColor: string;
  selected?: boolean;
  status?: string;
  filled?: boolean;
  shape?: 'rect' | 'diamond' | 'circle';
  children?: ReactNode;
  handles?: { target: boolean; source: boolean };
  /**
   * Adds a second, red source handle on the right for the node's failure branch.
   * The success handle deliberately keeps NO id: edges saved before this existed carry
   * `sourceHandle: null` and bind to the id-less handle, so they keep rendering.
   */
  failureHandle?: boolean;
}

export function BaseNode({
  label,
  type,
  borderColor,
  selected,
  status,
  filled,
  shape = 'rect',
  children,
  handles = { target: true, source: true },
  failureHandle = false,
}: Props) {
  const ringColor = ringClass(status, selected);

  const bg = filled ? borderColor : '#1a1d27';
  const textColor = filled ? 'text-white' : 'text-gray-100';
  const isDiamond = shape === 'diamond';
  const isCircle = shape === 'circle';

  return (
    <div
      className={`relative ${isDiamond ? 'rotate-45' : ''} ${isCircle ? 'rounded-full' : 'rounded'} ${ringColor}`}
      style={{
        background: bg,
        border: `2px solid ${borderColor}`,
        minWidth: isCircle ? 60 : 160,
        minHeight: isCircle ? 60 : 60,
        padding: isDiamond ? 16 : 8,
      }}
    >
      {handles.target && <Handle type="target" position={Position.Top} />}
      <div className={`${isDiamond ? '-rotate-45' : ''} ${textColor}`}>
        <div className="text-[10px] uppercase tracking-wide opacity-70">{type}</div>
        <div className="text-sm font-medium truncate" title={label}>{label}</div>
        {children && <div className="mt-1 text-xs">{children}</div>}
      </div>
      {handles.source && <Handle type="source" position={Position.Bottom} />}
      {failureHandle && (
        <Handle
          type="source"
          position={Position.Right}
          id="failure"
          title="failure branch"
          style={{ background: '#ef4444', width: 9, height: 9 }}
        />
      )}
    </div>
  );
}
