import { describe, it, expect } from 'vitest';
import yaml from 'js-yaml';
import { serializeToYaml, parseFromYaml, ScenarioMeta } from './scenarioSerializer';
import { ScenarioNode, ScenarioEdge } from '../types';

const meta: ScenarioMeta = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'RoundTrip',
  description: 'desc',
  version: '1.0',
  sessionRef: 'sess-1',
};

describe('scenarioSerializer round-trip', () => {
  it('serializes then parses nodes/edges preserving core structure', () => {
    const nodes: ScenarioNode[] = [
      { id: 'a', name: 'Start', type: 'START', config: {}, position: { x: 0, y: 0 } },
      {
        id: 'b',
        name: 'Send',
        type: 'SEND_FIX',
        config: { msgType: 'D', fields: [{ tag: 55, value: 'AAPL' }, { tag: 54, value: '1' }] },
        position: { x: 100, y: 100 },
      },
      { id: 'c', name: 'Done', type: 'END_PASS', config: {}, position: { x: 200, y: 200 } },
    ];
    const edges: ScenarioEdge[] = [
      { from: 'a', to: 'b', label: 'success' },
      { from: 'b', to: 'c', label: 'success' },
    ];

    const yamlStr = serializeToYaml(nodes, edges, meta);
    const parsed = parseFromYaml(yamlStr);

    expect(parsed.meta).toEqual(meta);
    expect(parsed.nodes.map((n) => n.id)).toEqual(['a', 'b', 'c']);
    expect(parsed.nodes[0].onSuccess).toBe('b');
    expect(parsed.nodes[1].onSuccess).toBe('c');
    // SEND_FIX fields survive the map<->array conversion (order follows numeric key order).
    const roundTripped = parsed.nodes[1].config.fields as Array<{ tag: number; value: string }>;
    const asMap = Object.fromEntries(roundTripped.map((f) => [f.tag, f.value]));
    expect(asMap).toEqual({ 55: 'AAPL', 54: '1' });
    // edges preserved
    expect(parsed.edges).toEqual(edges);
  });

  it('SEND_FIX fields are serialized as a tag->value map in YAML', () => {
    const nodes: ScenarioNode[] = [
      { id: 'b', name: 'Send', type: 'SEND_FIX', config: { msgType: 'D', fields: [{ tag: 55, value: 'AAPL' }] } },
    ];
    const yamlStr = serializeToYaml(nodes, [], meta);
    const doc = yaml.load(yamlStr) as { nodes: Array<{ config: { fields: Record<string, string> } }> };
    expect(doc.nodes[0].config.fields).toEqual({ 55: 'AAPL' });
  });

  it('ROUTE_FIX rule matchers round-trip through map form', () => {
    const nodes: ScenarioNode[] = [
      {
        id: 'r',
        name: 'Route',
        type: 'ROUTE_FIX',
        config: { rules: [{ name: 'fill', matchers: [{ tag: 35, value: '8' }, { tag: 39, value: '2' }] }] },
      },
    ];
    const yamlStr = serializeToYaml(nodes, [], meta);
    const parsed = parseFromYaml(yamlStr);
    const rules = parsed.nodes[0].config.rules as Array<{ matchers: Array<{ tag: number; value: string }> }>;
    expect(rules[0].matchers).toEqual([
      { tag: 35, value: '8' },
      { tag: 39, value: '2' },
    ]);
  });
});

describe('parseFromYaml timeout edge synthesis', () => {
  it('synthesizes a timeout edge from timeout.jumpTo when onTimeout is JUMP', () => {
    const yamlStr = yaml.dump({
      ...meta,
      nodes: [
        {
          id: 'a',
          name: 'Wait',
          type: 'EXPECT_FIX',
          config: {},
          timeout: { value: 5, unit: 'SECONDS', onTimeout: 'JUMP', jumpTo: 'b' },
        },
        { id: 'b', name: 'Recover', type: 'END_FAIL', config: {} },
      ],
      edges: [],
    });
    const parsed = parseFromYaml(yamlStr);
    const timeoutEdge = parsed.edges.find((e) => e.label === 'timeout');
    expect(timeoutEdge).toEqual({ from: 'a', to: 'b', label: 'timeout' });
  });

  it('does not synthesize a duplicate timeout edge if one already exists', () => {
    const yamlStr = yaml.dump({
      ...meta,
      nodes: [
        { id: 'a', name: 'Wait', type: 'EXPECT_FIX', config: {}, timeout: { value: 5, unit: 'SECONDS', onTimeout: 'JUMP', jumpTo: 'b' } },
        { id: 'b', name: 'Recover', type: 'END_FAIL', config: {} },
      ],
      edges: [{ from: 'a', to: 'b', label: 'timeout' }],
    });
    const parsed = parseFromYaml(yamlStr);
    const timeoutEdges = parsed.edges.filter((e) => e.label === 'timeout');
    expect(timeoutEdges).toHaveLength(1);
  });

  it('does not synthesize a timeout edge when onTimeout is not JUMP', () => {
    const yamlStr = yaml.dump({
      ...meta,
      nodes: [
        { id: 'a', name: 'Wait', type: 'EXPECT_FIX', config: {}, timeout: { value: 5, unit: 'SECONDS', onTimeout: 'FAIL' } },
      ],
      edges: [],
    });
    const parsed = parseFromYaml(yamlStr);
    expect(parsed.edges.some((e) => e.label === 'timeout')).toBe(false);
  });

  it('returns empty structure for blank input', () => {
    const parsed = parseFromYaml('');
    expect(parsed.nodes).toEqual([]);
    expect(parsed.edges).toEqual([]);
    expect(parsed.meta.id).toBe('');
  });

  it('assigns default positions to nodes lacking them', () => {
    const yamlStr = yaml.dump({
      ...meta,
      nodes: [{ id: 'a', name: 'A', type: 'START', config: {} }],
      edges: [],
    });
    const parsed = parseFromYaml(yamlStr);
    expect(parsed.nodes[0].position).toEqual({ x: 300, y: 80 });
  });
});
