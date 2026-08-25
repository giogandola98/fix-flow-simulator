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

describe('repeating groups', () => {
  const nodes = [{
    id: 'send-swap',
    name: 'Send Multileg',
    type: 'SEND_FIX' as const,
    config: {
      msgType: 'AB',
      fields: [{ tag: 11, value: 'ORD-1' }],
      groups: [{
        counterTag: 555,
        entries: [
          { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '1' }] },
          { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '2' }] },
        ],
      }],
    },
    position: { x: 0, y: 0 },
  }];
  const meta = { id: 'x', name: 'n', description: 'd', version: '1.0', sessionRef: 's' };

  it('keeps entry fields as an ordered list, never a tag-keyed map', () => {
    const yamlStr = serializeToYaml(nodes as never, [], meta);
    expect(yamlStr).toContain('counterTag: 555');
    // The delimiter tag must stay FIRST in the entry. A tag-keyed object would be
    // re-ordered numerically by JS, moving a lower tag ahead of the delimiter.
    expect(yamlStr).toContain('- tag: 600');
    expect(yamlStr).not.toMatch(/^\s+600: EUR\/USD$/m);
  });

  it('preserves delimiter-first order through a round trip', () => {
    const withLowTag = [{
      ...nodes[0],
      config: {
        msgType: 'AB',
        groups: [{
          counterTag: 555,
          entries: [{ fields: [
            { tag: 600, value: 'EUR/USD' },   // delimiter, must stay first
            { tag: 587, value: '0' },          // lower number than the delimiter
          ] }],
        }],
      },
    }];
    const back = parseFromYaml(serializeToYaml(withLowTag as never, [], meta));
    const cfg = back.nodes[0].config as never as {
      groups: { entries: { fields: { tag: number }[] }[] }[];
    };
    expect(cfg.groups[0].entries[0].fields[0].tag).toBe(600);
  });

  it('round-trips groups back to arrays', () => {
    const back = parseFromYaml(serializeToYaml(nodes as never, [], meta));
    const cfg = back.nodes[0].config as {
      groups: { counterTag: number; entries: { fields: { tag: number; value: string }[] }[] }[];
    };
    expect(cfg.groups[0].counterTag).toBe(555);
    expect(cfg.groups[0].entries).toHaveLength(2);
    expect(cfg.groups[0].entries[1].fields).toEqual([
      { tag: 600, value: 'EUR/USD' },
      { tag: 624, value: '2' },
    ]);
  });

  it('round-trips nested groups', () => {
    const nested = [{
      ...nodes[0],
      config: {
        msgType: 'AB',
        groups: [{
          counterTag: 555,
          entries: [{
            fields: [{ tag: 600, value: 'EUR/USD' }],
            groups: [{ counterTag: 864, entries: [{ fields: [{ tag: 865, value: '13' }] }] }],
          }],
        }],
      },
    }];
    const back = parseFromYaml(serializeToYaml(nested as never, [], meta));
    const cfg = back.nodes[0].config as never as {
      groups: { entries: { groups: { counterTag: number; entries: { fields: unknown[] }[] }[] }[] }[];
    };
    expect(cfg.groups[0].entries[0].groups[0].counterTag).toBe(864);
    expect(cfg.groups[0].entries[0].groups[0].entries[0].fields).toEqual([{ tag: 865, value: '13' }]);
  });

  it('leaves a SEND_FIX config without groups untouched', () => {
    const plain = [{ ...nodes[0], config: { msgType: 'D', fields: [{ tag: 11, value: 'ORD-1' }] } }];
    const back = parseFromYaml(serializeToYaml(plain as never, [], meta));
    expect((back.nodes[0].config as { groups?: unknown }).groups).toBeUndefined();
  });

  it('accepts a hand-authored entry written with fields as a tag-keyed map', () => {
    // We always WRITE the list form (see the tests above). This covers the one
    // path our new code exercises that the old pass-through never touched: parsing
    // input we did not write ourselves, e.g. a hand-authored YAML file.
    //
    // Note what this test can and cannot promise: JS re-orders integer-like object
    // keys ascending, so a map-form entry whose delimiter is not the lowest tag
    // CANNOT round-trip with the delimiter first. Here 600 (delimiter) already sorts
    // before 624, so this particular map happens to preserve order — that is a
    // property of this input, not a guarantee of the map form in general. That
    // ambiguity is exactly why we write the list form and only accept the map form
    // as a defensive fallback on read.
    const handAuthored = yaml.dump({
      ...meta,
      nodes: [{
        id: 'send-swap',
        name: 'Send Multileg',
        type: 'SEND_FIX',
        config: {
          msgType: 'AB',
          groups: [{
            counterTag: 555,
            entries: [{ fields: { 600: 'EUR/USD', 624: '1' } }],
          }],
        },
      }],
      edges: [],
    });
    const back = parseFromYaml(handAuthored);
    const cfg = back.nodes[0].config as {
      groups: { entries: { fields: { tag: number; value: string }[] }[] }[];
    };
    expect(Array.isArray(cfg.groups[0].entries[0].fields)).toBe(true);
    expect(cfg.groups[0].entries[0].fields).toEqual([
      { tag: 600, value: 'EUR/USD' },
      { tag: 624, value: '1' },
    ]);
  });
});

describe('branch edges drawn from a named handle', () => {
  const nodes: ScenarioNode[] = [
    { id: 'v', name: 'Check fields', type: 'VALIDATE', config: { rules: [] }, position: { x: 0, y: 0 } },
    { id: 'ok', name: 'Accepted', type: 'SEND_FIX', config: {}, position: { x: 0, y: 100 } },
    { id: 'ko', name: 'Rejected', type: 'SEND_FIX', config: {}, position: { x: 200, y: 100 } },
  ];

  it('maps a VALIDATE failure edge to onFailure', () => {
    const edges: ScenarioEdge[] = [
      { from: 'v', to: 'ok', label: 'success' },
      { from: 'v', to: 'ko', label: 'failure', sourceHandle: 'failure' },
    ];
    const parsed = parseFromYaml(serializeToYaml(nodes, edges, meta));
    expect(parsed.nodes[0].onSuccess).toBe('ok');
    expect(parsed.nodes[0].onFailure).toBe('ko');
  });

  it('maps a DECISION failure edge to onFailure', () => {
    const decision: ScenarioNode[] = [
      { id: 'd', name: 'If', type: 'DECISION', config: { condition: 'a == a' }, position: { x: 0, y: 0 } },
      ...nodes.slice(1),
    ];
    const edges: ScenarioEdge[] = [
      { from: 'd', to: 'ok', label: 'success', sourceHandle: 'success' },
      { from: 'd', to: 'ko', label: 'failure', sourceHandle: 'failure' },
    ];
    const parsed = parseFromYaml(serializeToYaml(decision, edges, meta));
    expect(parsed.nodes[0].onSuccess).toBe('ok');
    expect(parsed.nodes[0].onFailure).toBe('ko');
  });

  it('still maps handle-less edges, as saved before named handles existed', () => {
    const edges: ScenarioEdge[] = [
      { from: 'v', to: 'ok', label: 'success' },
      { from: 'v', to: 'ko', label: 'failure' },
    ];
    const parsed = parseFromYaml(serializeToYaml(nodes, edges, meta));
    expect(parsed.nodes[0].onSuccess).toBe('ok');
    expect(parsed.nodes[0].onFailure).toBe('ko');
  });

  it('does not mistake a ROUTE_FIX rule handle for a branch', () => {
    const route: ScenarioNode[] = [
      { id: 'r', name: 'Route', type: 'ROUTE_FIX',
        config: { rules: [{ ruleId: 'r1', label: 'failure', matchers: [], targetNodeId: 'ko' }] },
        position: { x: 0, y: 0 } },
      ...nodes.slice(1),
    ];
    const edges: ScenarioEdge[] = [{ from: 'r', to: 'ko', label: 'failure', sourceHandle: 'r1' }];
    const parsed = parseFromYaml(serializeToYaml(route, edges, meta));
    expect(parsed.nodes[0].onFailure).toBeUndefined();
    // the rule keeps its own target instead
    const rules = parsed.nodes[0].config.rules as Array<{ targetNodeId: string }>;
    expect(rules[0].targetNodeId).toBe('ko');
  });

  it('keeps the visual edge list intact either way', () => {
    const edges: ScenarioEdge[] = [
      { from: 'v', to: 'ok', label: 'success' },
      { from: 'v', to: 'ko', label: 'failure', sourceHandle: 'failure' },
    ];
    const parsed = parseFromYaml(serializeToYaml(nodes, edges, meta));
    expect(parsed.edges).toEqual(edges);
  });
});
