import yaml from 'js-yaml';
import {
  NodeType,
  RetryPolicy,
  ScenarioEdge,
  ScenarioNode,
  TimeoutConfig,
} from '../types';

export interface ScenarioMeta {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
}

interface YamlNode {
  id: string;
  name: string;
  type: NodeType;
  config?: Record<string, unknown>;
  timeout?: TimeoutConfig;
  retryPolicy?: RetryPolicy;
  onSuccess?: string;
  onFailure?: string;
  onTimeout?: string;
  position?: { x: number; y: number };
}

interface YamlEdge {
  from: string;
  to: string;
  label: string;
  sourceHandle?: string;
}

interface YamlDoc extends ScenarioMeta {
  nodes: YamlNode[];
  edges: YamlEdge[];
}

function fieldsArrayToMap(fields: Array<{ tag: number; value: string }>): Record<number, string> {
  const map: Record<number, string> = {};
  for (const f of fields) map[f.tag] = f.value;
  return map;
}

function fieldsMapToArray(fields: Record<string, string>): Array<{ tag: number; value: string }> {
  return Object.entries(fields).map(([k, v]) => ({ tag: Number(k), value: v }));
}

function matcherArrayToMap(matchers: Array<{ tag: number; value: string }>): Record<string, string> {
  const m: Record<string, string> = {};
  for (const row of matchers) m[String(row.tag)] = row.value;
  return m;
}

function matcherMapToArray(matchers: Record<string, string>): Array<{ tag: number; value: string }> {
  return Object.entries(matchers).map(([k, v]) => ({ tag: Number(k), value: v }));
}

interface YamlGroupEntry { fields?: Array<{ tag: number; value: string }> | Record<string, string>; groups?: YamlGroupSpec[] }
interface YamlGroupSpec { counterTag: number; entries: YamlGroupEntry[] }

function serializeGroups(groups: Array<Record<string, unknown>>): YamlGroupSpec[] {
  return groups.map((g) => ({
    counterTag: Number(g.counterTag),
    entries: ((g.entries ?? []) as Array<Record<string, unknown>>).map((e) => {
      // Entry fields stay a LIST. Converting to a tag-keyed object would let JS
      // re-order the keys numerically, and the delimiter tag must stay first.
      const out: YamlGroupEntry = {
        fields: (e.fields ?? []) as Array<{ tag: number; value: string }>,
      };
      if (Array.isArray(e.groups) && e.groups.length > 0) {
        out.groups = serializeGroups(e.groups as Array<Record<string, unknown>>);
      }
      return out;
    }),
  }));
}

function parseGroups(groups: YamlGroupSpec[]): Array<Record<string, unknown>> {
  return groups.map((g) => ({
    counterTag: Number(g.counterTag),
    entries: (g.entries ?? []).map((e) => {
      // Accept the list form (what we write) and the map form (hand-authored YAML),
      // but always hand the store a list so delimiter-first order survives.
      const out: Record<string, unknown> = {
        fields: Array.isArray(e.fields)
          ? (e.fields as Array<{ tag: number; value: string }>)
          : fieldsMapToArray((e.fields ?? {}) as Record<string, string>),
      };
      if (Array.isArray(e.groups) && e.groups.length > 0) out.groups = parseGroups(e.groups);
      return out;
    }),
  }));
}

function serializeConfig(type: NodeType, config: Record<string, unknown>): Record<string, unknown> {
  if (type === 'SEND_FIX') {
    const out = { ...config };
    if (Array.isArray(config.fields)) {
      out.fields = fieldsArrayToMap(config.fields as Array<{ tag: number; value: string }>);
    }
    if (Array.isArray(config.groups) && config.groups.length > 0) {
      out.groups = serializeGroups(config.groups as Array<Record<string, unknown>>);
    } else {
      delete out.groups;
    }
    return out;
  }
  if (type === 'ROUTE_FIX' && Array.isArray(config.rules)) {
    const rules = (config.rules as Array<Record<string, unknown>>).map((r) => ({
      ...r,
      matchers: Array.isArray(r.matchers)
        ? matcherArrayToMap(r.matchers as Array<{ tag: number; value: string }>)
        : r.matchers,
    }));
    return { ...config, rules };
  }
  return config;
}

function parseConfig(type: NodeType, config: Record<string, unknown>): Record<string, unknown> {
  if (type === 'SEND_FIX') {
    const out = { ...config };
    if (config.fields != null && !Array.isArray(config.fields)) {
      out.fields = fieldsMapToArray(config.fields as Record<string, string>);
    }
    if (Array.isArray(config.groups) && config.groups.length > 0) {
      out.groups = parseGroups(config.groups as YamlGroupSpec[]);
    } else {
      delete out.groups;
    }
    return out;
  }
  if (type === 'ROUTE_FIX' && Array.isArray(config.rules)) {
    const rules = (config.rules as Array<Record<string, unknown>>).map((r) => ({
      ...r,
      matchers: r.matchers != null && !Array.isArray(r.matchers)
        ? matcherMapToArray(r.matchers as Record<string, string>)
        : (r.matchers ?? []),
    }));
    return { ...config, rules };
  }
  return config;
}

export function serializeToYaml(
  nodes: ScenarioNode[],
  edges: ScenarioEdge[],
  meta: ScenarioMeta,
): string {
  const doc: YamlDoc = {
    ...meta,
    nodes: nodes.map((n) => {
      // A branch edge counts whether it was drawn from an anonymous handle (older nodes, whose
      // single source handle has no id) or from one named after the branch. Requiring NO handle
      // dropped every DECISION failure edge on the floor — it round-tripped in the visual `edges`
      // array but never reached `onFailure`, which is the only thing the engine traverses (#76).
      const branchEdge = (label: string) =>
        edges.find(
          (e) =>
            e.from === n.id &&
            e.label === label &&
            (!e.sourceHandle || e.sourceHandle === label),
        );
      const successEdge = branchEdge('success');
      const failureEdge = branchEdge('failure');
      const timeoutEdge = branchEdge('timeout');
      return {
        id: n.id,
        name: n.name,
        type: n.type,
        config: serializeConfig(n.type, n.config ?? {}),
        timeout: n.timeout,
        retryPolicy: n.retryPolicy,
        onSuccess: successEdge?.to ?? n.onSuccess,
        onFailure: failureEdge?.to ?? n.onFailure,
        onTimeout: timeoutEdge?.to ?? n.onTimeout,
        position: n.position,
      };
    }),
    edges: edges.map((e) => ({ from: e.from, to: e.to, label: e.label, ...(e.sourceHandle ? { sourceHandle: e.sourceHandle } : {}) })),
  };
  return yaml.dump(doc, { noRefs: true, sortKeys: false, lineWidth: 120 });
}

export function parseFromYaml(yamlStr: string): {
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  meta: ScenarioMeta;
} {
  if (!yamlStr || !yamlStr.trim()) {
    return {
      nodes: [],
      edges: [],
      meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
    };
  }
  const doc = yaml.load(yamlStr) as YamlDoc | null;
  if (!doc) {
    return {
      nodes: [],
      edges: [],
      meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
    };
  }
  const meta: ScenarioMeta = {
    id: doc.id ?? '',
    name: doc.name ?? '',
    description: doc.description ?? '',
    version: String(doc.version ?? '1.0'),
    sessionRef: doc.sessionRef ?? '',
  };
  const nodes: ScenarioNode[] = (doc.nodes ?? []).map((n, idx) => ({
    id: n.id,
    name: n.name,
    type: n.type,
    config: parseConfig(n.type, n.config ?? {}),
    timeout: n.timeout,
    retryPolicy: n.retryPolicy,
    onSuccess: n.onSuccess,
    onFailure: n.onFailure,
    onTimeout: n.onTimeout,
    position: n.position ?? { x: 300, y: 80 + idx * 160 },
  }));
  const edges: ScenarioEdge[] = (doc.edges ?? []).map((e) => ({
    from: e.from,
    to: e.to,
    label: e.label,
    ...(e.sourceHandle ? { sourceHandle: e.sourceHandle } : {}),
  }));

  // Synthesize timeout edges from node.timeout.jumpTo / node.onTimeout when missing
  for (const n of doc.nodes ?? []) {
    const jumpTarget = n.timeout?.jumpTo ?? (n.onTimeout as string | undefined);
    if (jumpTarget && n.timeout?.onTimeout === 'JUMP') {
      const already = edges.some((e) => e.from === n.id && e.label === 'timeout');
      if (!already) edges.push({ from: n.id, to: jumpTarget, label: 'timeout' });
    }
  }

  return { nodes, edges, meta };
}
