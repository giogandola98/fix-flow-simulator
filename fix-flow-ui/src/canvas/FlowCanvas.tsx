import { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  Controls,
  Connection,
  Node,
  Edge,
  useReactFlow,
  applyNodeChanges,
  applyEdgeChanges,
  NodeChange,
  EdgeChange,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useScenarioStore } from '../store/scenarioStore';
import { useExecutionStore } from '../store/executionStore';
import { CanvasToolbar } from './CanvasToolbar';
import { FlowEdge } from './edges/FlowEdge';
import { nodeTypes } from './nodes/nodeTypes';
import { NodeType, ScenarioNode } from '../types';

const edgeTypes = { default: FlowEdge };

function InnerCanvas() {
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const addNode = useScenarioStore((s) => s.addNode);
  const addEdge = useScenarioStore((s) => s.addEdge);
  const setSelectedNode = useScenarioStore((s) => s.setSelectedNode);
  const nodeStatuses = useExecutionStore((s) => s.nodeStatuses);
  const { screenToFlowPosition } = useReactFlow();

  const rfNodes: Node[] = useMemo(
    () =>
      nodes.map((n) => ({
        id: n.id,
        type: n.type,
        position: n.position ?? { x: 100, y: 100 },
        data: {
          label: n.name,
          config: n.config,
          status: nodeStatuses[n.id] ?? 'idle',
        },
      })),
    [nodes, nodeStatuses],
  );

  const rfEdges: Edge[] = useMemo(
    () =>
      edges.map((e, i) => ({
        id: `e${i}-${e.from}-${e.to}-${e.label}`,
        source: e.from,
        target: e.to,
        label: e.label,
        type: 'default',
      })),
    [edges],
  );

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      const updated = applyNodeChanges(changes, rfNodes);
      setNodes(
        updated.map((rn) => {
          const orig = nodes.find((n) => n.id === rn.id);
          return { ...(orig as ScenarioNode), position: rn.position };
        }),
      );
    },
    [rfNodes, nodes, setNodes],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      const updated = applyEdgeChanges(changes, rfEdges);
      setEdges(
        updated.map((e) => {
          const orig = rfEdges.find((re) => re.id === e.id);
          return {
            from: e.source,
            to: e.target,
            label: String(orig?.label ?? 'success'),
          };
        }),
      );
    },
    [rfEdges, setEdges],
  );

  const onConnect = useCallback(
    (conn: Connection) => {
      if (conn.source && conn.target) {
        addEdge({ from: conn.source, to: conn.target, label: 'success' });
      }
    },
    [addEdge],
  );

  const onDragOver = useCallback((evt: React.DragEvent) => {
    evt.preventDefault();
    evt.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (evt: React.DragEvent) => {
      evt.preventDefault();
      const type = evt.dataTransfer.getData('application/fix-flow-node-type') as NodeType;
      if (!type) return;
      const pos = screenToFlowPosition({ x: evt.clientX, y: evt.clientY });
      const id = `node-${Date.now()}`;
      addNode({ id, name: type, type, config: {}, position: pos });
    },
    [addNode, screenToFlowPosition],
  );

  return (
    <div className="relative w-full h-full" onDrop={onDrop} onDragOver={onDragOver}>
      <ReactFlow
        nodes={rfNodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={(_, n) => setSelectedNode(n.id)}
        onPaneClick={() => setSelectedNode(null)}
        fitView
      >
        <Background color="#2a2d3a" gap={20} />
        <Controls className="!bg-[#1a1d27] !border-[#2a2d3a]" />
      </ReactFlow>
      <CanvasToolbar />
    </div>
  );
}

export default function FlowCanvas() {
  return (
    <div className="w-full h-full bg-[#0f1117]">
      <ReactFlowProvider>
        <InnerCanvas />
      </ReactFlowProvider>
    </div>
  );
}
