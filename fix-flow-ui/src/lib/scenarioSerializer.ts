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

function serializeConfig(type: NodeType, config: Record<string, unknown>): Record<string, unknown> {
  if (type === 'SEND_FIX' && Array.isArray(config.fields)) {
    return { ...config, fields: fieldsArrayToMap(config.fields as Array<{ tag: number; value: string }>) };
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
  if (type === 'SEND_FIX' && config.fields != null && !Array.isArray(config.fields)) {
    return { ...config, fields: fieldsMapToArray(config.fields as Record<string, string>) };
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
      const successEdge = edges.find((e) => e.from === n.id && e.label === 'success' && !e.sourceHandle);
      const failureEdge = edges.find((e) => e.from === n.id && e.label === 'failure' && !e.sourceHandle);
      const timeoutEdge = edges.find((e) => e.from === n.id && e.label === 'timeout' && !e.sourceHandle);
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
  return { nodes, edges, meta };
}
