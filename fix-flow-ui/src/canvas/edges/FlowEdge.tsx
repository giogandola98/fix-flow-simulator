import { BaseEdge, EdgeProps, getBezierPath } from '@xyflow/react';

export function FlowEdge(props: EdgeProps) {
  const { sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, label } = props;
  const [path] = getBezierPath({
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
  return <BaseEdge id={props.id} path={path} style={{ stroke: color, strokeWidth: 2 }} />;
}
