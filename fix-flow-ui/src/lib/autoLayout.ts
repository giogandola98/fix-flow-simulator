import dagre from '@dagrejs/dagre';

/**
 * Pure, framework-agnostic hierarchical auto-layout backed by dagre.
 *
 * Input: plain nodes + edges (accepts both store `from/to` and ReactFlow
 * `source/target` edge shapes). Output: each node's new TOP-LEFT position.
 *
 * dagre positions nodes by their CENTER; ReactFlow uses top-left, so we
 * convert by subtracting half the node's width/height. Deterministic — no
 * React, no DOM — so it is unit-testable in isolation.
 */

export interface LayoutInputNode {
  id: string;
  position?: { x: number; y: number };
  width?: number;
  height?: number;
  /** ReactFlow v12 measured dimensions, when available. */
  measured?: { width?: number | null; height?: number | null };
}

export interface LayoutInputEdge {
  source?: string;
  target?: string;
  from?: string;
  to?: string;
}

export interface AutoLayoutOptions {
  rankdir?: 'TB' | 'BT' | 'LR' | 'RL';
  ranksep?: number;
  nodesep?: number;
  defaultWidth?: number;
  defaultHeight?: number;
}

export interface PositionedNode {
  id: string;
  position: { x: number; y: number };
}

export function autoLayout(
  nodes: LayoutInputNode[],
  edges: LayoutInputEdge[],
  opts: AutoLayoutOptions = {},
): PositionedNode[] {
  const {
    rankdir = 'TB',
    ranksep = 90,
    nodesep = 60,
    defaultWidth = 180,
    defaultHeight = 64,
  } = opts;

  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir, ranksep, nodesep });
  g.setDefaultEdgeLabel(() => ({}));

  const dims = new Map<string, { w: number; h: number }>();
  for (const n of nodes) {
    const w = n.measured?.width ?? n.width ?? defaultWidth;
    const h = n.measured?.height ?? n.height ?? defaultHeight;
    dims.set(n.id, { w, h });
    g.setNode(n.id, { width: w, height: h });
  }

  for (const e of edges) {
    const src = e.source ?? e.from;
    const tgt = e.target ?? e.to;
    if (src && tgt && g.hasNode(src) && g.hasNode(tgt)) {
      g.setEdge(src, tgt);
    }
  }

  dagre.layout(g);

  return nodes.map((n) => {
    const gn = g.node(n.id) as { x: number; y: number } | undefined;
    const d = dims.get(n.id)!;
    if (!gn) {
      // Disconnected/unknown node — keep its current position (or origin).
      return { id: n.id, position: n.position ?? { x: 0, y: 0 } };
    }
    return {
      id: n.id,
      position: { x: gn.x - d.w / 2, y: gn.y - d.h / 2 },
    };
  });
}
