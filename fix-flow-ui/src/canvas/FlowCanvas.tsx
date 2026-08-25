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
import { handleRoutesOf, withRouteTarget } from '../lib/handleRoutes';

const edgeTypes = { default: FlowEdge };

function InnerCanvas() {
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);

  const setEdges = useScenarioStore((s) => s.setEdges);
  const addNode = useScenarioStore((s) => s.addNode);
  const removeNode = useScenarioStore((s) => s.removeNode);
  const updateNode = useScenarioStore((s) => s.updateNode);
  const setSelectedNode = useScenarioStore((s) => s.setSelectedNode);
  const markDirty = useScenarioStore((s) => s.markDirty);
  const layoutVersion = useScenarioStore((s) => s.layoutVersion);
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
  // Track the last applied layout version to detect auto-layout bumps.
  const appliedLayoutVersion = useRef(layoutVersion);

  // Reset rfNodes/rfEdges when the active scenario changes.
  // Must be declared before the nodes-sync effect so it runs first.
  useEffect(() => {
    const nextNodes = nodes.map((n) => ({
      id: n.id,
      type: n.type,
      position: n.position ?? { x: 100, y: 100 },
      data: { label: n.name, config: n.config, timeout: n.timeout, status: nodeStatuses[n.id] ?? 'idle' },
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
          data: { label: n.name, config: n.config, timeout: n.timeout, status: nodeStatuses[n.id] ?? 'idle' },
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
        // `timeout` is compared too: a WAIT block's duration lives there, so leaving it out
        // meant editing the duration never re-rendered the node (issue #89).
        if (d.label === storeNode.name && d.config === storeNode.config && d.timeout === storeNode.timeout) {
          return rfNode;
        }
        return {
          ...rfNode,
          data: { ...d, label: storeNode.name, config: storeNode.config, timeout: storeNode.timeout },
        };
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

  // Apply auto-layout: the store recomputed node positions and bumped
  // layoutVersion. Re-apply ONLY the position to each local rfNode, preserving
  // React Flow's internal 'measured' field (a full reset would strip it and
  // leave nodes visibility:hidden — see CLAUDE.md ReactFlow v12 gotcha).
  useEffect(() => {
    if (layoutVersion === appliedLayoutVersion.current) return;
    appliedLayoutVersion.current = layoutVersion;
    const posMap = new Map(
      useScenarioStore.getState().nodes.map((n) => [n.id, n.position]),
    );
    setRfNodes((prev) =>
      prev.map((rfNode) => {
        const pos = posMap.get(rfNode.id);
        return pos ? { ...rfNode, position: pos } : rfNode;
      }),
    );
    const t = setTimeout(() => fitView({ padding: 0.2 }), 50);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layoutVersion]);

  // Keep rfEdges in sync with store (edges have no measured state issue).
  useEffect(() => {
    setRfEdges(
      edges.map((e, i) => {
        const srcNode = nodes.find((n) => n.id === e.from);
        const route = handleRoutesOf(srcNode?.type, srcNode?.config as Record<string, unknown>)
          .find((r) => r.id === e.sourceHandle);
        const derivedLabel = route?.label || e.label;
        return {
          id: `e${i}-${e.from}-${e.to}-${e.label}`,
          source: e.from,
          target: e.to,
          sourceHandle: e.sourceHandle ?? null,
          label: derivedLabel,
          type: 'default',
        };
      }),
    );
  }, [edges, nodes]);

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      setRfNodes((prev) => applyNodeChanges(changes, prev));
      // Propagate node deletions to the store (cleans up all stale references).
      changes
        .filter((c) => c.type === 'remove')
        .forEach((c) => removeNode((c as { id: string }).id));
    },
    [removeNode],
  );

  // RF v12 sends position:undefined at drag-end in onNodesChange — use onNodeDragStop instead.
  const onNodeDragStop = useCallback(
    (_: React.MouseEvent, node: Node) => {
      updateNode(node.id, { position: node.position });
      markDirty();
    },
    [updateNode, markDirty],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      setRfEdges((prev) => applyEdgeChanges(changes, prev));
      const removals = changes.filter((c) => c.type === 'remove') as { id: string }[];
      if (removals.length > 0) {
        const removedIds = new Set(removals.map((c) => c.id));
        for (const re of rfEdges.filter((e) => removedIds.has(e.id))) {
          const srcNode = nodes.find((n) => n.id === re.source);
          // Deleting the line must also clear the route it stood for, or the engine keeps
          // following a branch the canvas no longer shows.
          const cleared = withRouteTarget(
            srcNode?.type, srcNode?.config as Record<string, unknown>, re.sourceHandle, '',
          );
          if (srcNode && cleared) updateNode(srcNode.id, { config: cleared });
        }
        // Rebuild from the store's canonical edges minus the removed ids. rfEdges labels
        // are display-mutated for ROUTE_FIX, so rebuilding from them would corrupt the
        // surviving edges' canonical labels. rfEdge ids are derived from store edges
        // (index + from/to/canonical label) in the sync effect, so reconstruct to match.
        setEdges(
          edges
            .filter((e, i) => !removedIds.has(`e${i}-${e.from}-${e.to}-${e.label}`))
            .map((e) => ({
              from: e.from,
              to: e.to,
              label: e.label,
              ...(e.sourceHandle ? { sourceHandle: e.sourceHandle } : {}),
            })),
        );
      }
    },
    [rfEdges, edges, setEdges, nodes, updateNode],
  );

  const onConnect = useCallback(
    (conn: Connection) => {
      if (conn.source && conn.target) {
        let label = 'success';
        if (conn.sourceHandle && conn.sourceHandle !== 'default') {
          const sourceRfNode = rfNodes.find((n) => n.id === conn.source);
          // A handle literally called success/failure names the branch, whatever the node type.
          // This used to be checked for DECISION only, so a failure handle on any other node
          // silently produced a 'success' edge (issue #76).
          if (conn.sourceHandle === 'success' || conn.sourceHandle === 'failure') {
            label = conn.sourceHandle;
          } else {
            // A ROUTE_FIX rule handle or a DECISION branch handle: the drawn target has to land
            // in the node config, because that is what the engine traverses.
            const cfg = sourceRfNode?.data?.config as Record<string, unknown> | undefined;
            const route = handleRoutesOf(sourceRfNode?.type, cfg).find((r) => r.id === conn.sourceHandle);
            if (route?.label) label = route.label;
            const patched = withRouteTarget(sourceRfNode?.type, cfg, conn.sourceHandle, conn.target);
            if (patched) updateNode(conn.source, { config: patched });
          }
        }
        const edge = { from: conn.source, to: conn.target, label };
        const newEdge = conn.sourceHandle ? { ...edge, sourceHandle: conn.sourceHandle } : edge;
        // Replace-in-place: one edge per source+sourceHandle. The serializer keeps only the
        // first on save, so a second edge from the same handle would silently diverge on reload.
        const sh = conn.sourceHandle ?? null;
        setEdges([
          ...edges.filter((e) => !(e.from === conn.source && (e.sourceHandle ?? null) === sh)),
          newEdge,
        ]);
      }
    },
    [edges, setEdges, rfNodes, updateNode],
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
        onNodeDragStop={onNodeDragStop}
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
