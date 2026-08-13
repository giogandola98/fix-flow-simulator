import { describe, it, expect } from 'vitest';
import { autoLayout, LayoutInputNode, LayoutInputEdge } from './autoLayout';

// Small DAG:  start → a → end, plus a branch start → b
const nodes: LayoutInputNode[] = [
  { id: 'start', position: { x: 0, y: 0 } },
  { id: 'a', position: { x: 0, y: 0 } },
  { id: 'b', position: { x: 0, y: 0 } },
  { id: 'end', position: { x: 0, y: 0 } },
];
const edges: LayoutInputEdge[] = [
  { from: 'start', to: 'a' },
  { from: 'start', to: 'b' },
  { from: 'a', to: 'end' },
];

describe('autoLayout', () => {
  it('returns a position for every node', () => {
    const out = autoLayout(nodes, edges);
    expect(out).toHaveLength(nodes.length);
    for (const n of nodes) {
      const p = out.find((o) => o.id === n.id);
      expect(p).toBeDefined();
      expect(Number.isFinite(p!.position.x)).toBe(true);
      expect(Number.isFinite(p!.position.y)).toBe(true);
    }
  });

  it('places the start node above its successors (lower y, top-to-bottom)', () => {
    const out = autoLayout(nodes, edges);
    const y = (id: string) => out.find((o) => o.id === id)!.position.y;
    expect(y('start')).toBeLessThan(y('a'));
    expect(y('start')).toBeLessThan(y('b'));
    expect(y('a')).toBeLessThan(y('end'));
  });

  it('produces distinct, non-overlapping node positions', () => {
    const out = autoLayout(nodes, edges);
    const W = 180;
    const H = 64;
    const seen = new Set<string>();
    for (const o of out) {
      const key = `${o.position.x},${o.position.y}`;
      expect(seen.has(key)).toBe(false);
      seen.add(key);
    }
    // No two boxes overlap (axis-aligned bounding-box check).
    for (let i = 0; i < out.length; i++) {
      for (let j = i + 1; j < out.length; j++) {
        const p = out[i].position;
        const q = out[j].position;
        const overlap = p.x < q.x + W && p.x + W > q.x && p.y < q.y + H && p.y + H > q.y;
        expect(overlap).toBe(false);
      }
    }
  });

  it('is deterministic across runs', () => {
    const a = autoLayout(nodes, edges);
    const b = autoLayout(nodes, edges);
    expect(a).toEqual(b);
  });

  it('honours measured dimensions when present', () => {
    const measured: LayoutInputNode[] = [
      { id: 'start', measured: { width: 200, height: 80 } },
      { id: 'a', measured: { width: 200, height: 80 } },
    ];
    const out = autoLayout(measured, [{ source: 'start', target: 'a' }]);
    expect(out).toHaveLength(2);
    // top-left = center - half size; positions must still be finite & ordered
    expect(out.find((o) => o.id === 'start')!.position.y).toBeLessThan(
      out.find((o) => o.id === 'a')!.position.y,
    );
  });
});
