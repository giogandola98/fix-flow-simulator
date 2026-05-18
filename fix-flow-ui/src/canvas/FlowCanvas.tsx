import { useCallback, useEffect, useRef, useState } from 'react';
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
import { NodeType } from '../types';

const edgeTypes = { default: FlowEdge };

function InnerCanvas() {
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const addNode = useScenarioStore((s) => s.addNode);
  const addEdge = useScenarioStore((s) => s.addEdge);
  const removeNode = useScenarioStore((s) => s.removeNode);
  const setSelectedNode = useScenarioStore((s) => s.setSelectedNode);
  const activeScenarioId = useScenarioStore((s) => s.activeScenario?.id);
  const nodeStatuses = useExecutionStore((s) => s.nodeStatuses);
  const { screenToFlowPosition, fitView } = useReactFlow();

  // Local RF state so React Flow can manage measured/selected internally.
  // Do NOT derive these as useMemo from the store — that strips React Flow's
  // internal 'measured' state and keeps nodes visibility:hidden forever.
  const [rfNodes, setRfNodes] = useState<Node[]>([]);
  const [rfEdges, setRfEdges] = useState<Edge[]>([]);

  // Track which node ids are in rfNodes to detect drag-and-drop additions.
  const trackedNodeIds = useRef(new Set<string>());

  // Reset rfNodes/rfEdges when the active scenario changes.
  // Must be declared before the nodes-sync effect so it runs first.
  useEffect(() => {
    const nextNodes = nodes.map((n) => ({
      id: n.id,
      type: n.type,
      position: n.position ?? { x: 100, y: 100 },
      data: { label: n.name, config: n.config, status: nodeStatuses[n.id] ?? 'idle' },
    }));
    setRfNodes(nextNodes);
    trackedNodeIds.current = new Set(nodes.map((n) => n.id));

    setRfEdges(
      edges.map((e, i) => ({
        id: `e${i}-${e.from}-${e.to}-${e.label}`,
        source: e.from,
        target: e.to,
        sourceHandle: e.sourceHandle ?? null,
        label: e.label,
        type: 'default',
      })),
    );

    if (nodes.length > 0) {
      const t = setTimeout(() => fitView({ padding: 0.2 }), 300);
      return () => clearTimeout(t);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeScenarioId]);

  // Append nodes added via drag-and-drop (scenario id unchanged, nodes grew).
  useEffect(() => {
    const newNodes = nodes.filter((n) => !trackedNodeIds.current.has(n.id));
    if (newNodes.length > 0) {
      setRfNodes((prev) => [
        ...prev,
        ...newNodes.map((n) => ({
          id: n.id,
          type: n.type,
          position: n.position ?? { x: 100, y: 100 },
          data: { label: n.name, config: n.config, status: nodeStatuses[n.id] ?? 'idle' },
        })),
      ]);
    }
    // Sync removals (e.g. node deleted from sidebar).
    const removedIds = [...trackedNodeIds.current].filter(
      (id) => !nodes.some((n) => n.id === id),
    );
    if (removedIds.length > 0) {
      const removedSet = new Set(removedIds);
      setRfNodes((prev) => prev.filter((n) => !removedSet.has(n.id)));
    }
    // Sync data changes for existing nodes (e.g. rename).
    setRfNodes((prev) =>
      prev.map((rfNode) => {
        const storeNode = nodes.find((n) => n.id === rfNode.id);
        if (!storeNode) return rfNode;
        const d = rfNode.data as Record<string, unknown>;
        if (d.label === storeNode.name && d.config === storeNode.config) return rfNode;
        return { ...rfNode, data: { ...d, label: storeNode.name, config: storeNode.config } };
      }),
    );
    trackedNodeIds.current = new Set(nodes.map((n) => n.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodes]);

  // Sync execution status to rfNodes without resetting measured state.
  useEffect(() => {
    setRfNodes((prev) =>
      prev.map((rfNode) => {
        const status = nodeStatuses[rfNode.id] ?? 'idle';
        if ((rfNode.data as Record<string, unknown>).status === status) return rfNode;
        return { ...rfNode, data: { ...rfNode.data, status } };
      }),
    );
  }, [nodeStatuses]);

  // Keep rfEdges in sync with store (edges have no measured state issue).
  useEffect(() => {
    setRfEdges(
      edges.map((e, i) => ({
        id: `e${i}-${e.from}-${e.to}-${e.label}`,
        source: e.from,
        target: e.to,
        sourceHandle: e.sourceHandle ?? null,
        label: e.label,
        type: 'default',
      })),
    );
  }, [edges]);

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      setRfNodes((prev) => applyNodeChanges(changes, prev));
      // Propagate node deletions to the store (cleans up all stale references).
      changes
        .filter((c) => c.type === 'remove')
        .forEach((c) => removeNode((c as { id: string }).id));
      // Only write final drag positions back to the store.
      type PosChange = { id: string; type: 'position'; position?: { x: number; y: number }; dragging?: boolean };
      const finalPositions = changes.filter(
        (c) => c.type === 'position' && !(c as PosChange).dragging,
      ) as PosChange[];
      if (finalPositions.length > 0) {
        setNodes(
          nodes.map((n) => {
            const change = finalPositions.find((c) => c.id === n.id);
            return change?.position ? { ...n, position: change.position } : n;
          }),
        );
      }
    },
    [nodes, setNodes, removeNode],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      setRfEdges((prev) => applyEdgeChanges(changes, prev));
      const removals = changes.filter((c) => c.type === 'remove') as { id: string }[];
      if (removals.length > 0) {
        const removedIds = new Set(removals.map((c) => c.id));
        setEdges(
          rfEdges
            .filter((e) => !removedIds.has(e.id))
            .map((e) => ({
              from: e.source,
              to: e.target,
              label: String(e.label ?? 'success'),
              ...(e.sourceHandle ? { sourceHandle: e.sourceHandle } : {}),
            })),
        );
      }
    },
    [rfEdges, setEdges],
  );

  const onConnect = useCallback(
    (conn: Connection) => {
      if (conn.source && conn.target) {
        const edge = { from: conn.source, to: conn.target, label: 'success' };
        addEdge(conn.sourceHandle ? { ...edge, sourceHandle: conn.sourceHandle } : edge);
      }
    },
    [addEdge],
  );

  const onDragOver = useCallback((evt: React.DragEvent) => {
    evt.preventDefault();
    evt.dataTransfer.dropEffect = activeScenarioId ? 'move' : 'none';
  }, [activeScenarioId]);

  const onDrop = useCallback(
    (evt: React.DragEvent) => {
      evt.preventDefault();
      if (!activeScenarioId) return;
      const type = evt.dataTransfer.getData('application/fix-flow-node-type') as NodeType;
      if (!type) return;
      const pos = screenToFlowPosition({ x: evt.clientX, y: evt.clientY });
      const id = `node-${Date.now()}`;
      addNode({ id, name: type, type, config: {}, position: pos });
    },
    [activeScenarioId, addNode, screenToFlowPosition],
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
        defaultViewport={{ x: 0, y: 0, zoom: 1 }}
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
