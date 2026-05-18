import { BaseEdge, EdgeLabelRenderer, EdgeProps, getBezierPath } from '@xyflow/react';

export function FlowEdge(props: EdgeProps) {
  const { sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, label } = props;
  const [path, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  const labelStr = String(label ?? '');
  const color =
    labelStr === 'success'
      ? '#22c55e'
      : labelStr === 'failure'
        ? '#ef4444'
        : labelStr === 'timeout'
          ? '#f59e0b'
          : '#6b7280';
  const markerId = `arrow-${props.id}`;
  return (
    <>
      <defs>
        <marker id={markerId} markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
          <polygon points="0 0, 8 3, 0 6" fill={color} />
        </marker>
      </defs>
      <BaseEdge
        id={props.id}
        path={path}
        style={{ stroke: color, strokeWidth: 2 }}
        markerEnd={`url(#${markerId})`}
      />
      {props.label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              pointerEvents: 'all',
              color: color,
            }}
            className="nodrag nopan text-[10px] px-1.5 py-0.5 rounded bg-[#1a1d27] border border-[#2a2d3a]"
          >
            {props.label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}
