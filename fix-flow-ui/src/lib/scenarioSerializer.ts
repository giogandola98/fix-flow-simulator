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
}

interface YamlDoc extends ScenarioMeta {
  nodes: YamlNode[];
  edges: YamlEdge[];
}

export function serializeToYaml(
  nodes: ScenarioNode[],
  edges: ScenarioEdge[],
  meta: ScenarioMeta,
): string {
  const doc: YamlDoc = {
    ...meta,
    nodes: nodes.map((n) => ({
      id: n.id,
      name: n.name,
      type: n.type,
      config: n.config ?? {},
      timeout: n.timeout,
      retryPolicy: n.retryPolicy,
      onSuccess: n.onSuccess,
      onFailure: n.onFailure,
      onTimeout: n.onTimeout,
      position: n.position,
    })),
    edges: edges.map((e) => ({ from: e.from, to: e.to, label: e.label })),
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
  const nodes: ScenarioNode[] = (doc.nodes ?? []).map((n) => ({
    id: n.id,
    name: n.name,
    type: n.type,
    config: n.config ?? {},
    timeout: n.timeout,
    retryPolicy: n.retryPolicy,
    onSuccess: n.onSuccess,
    onFailure: n.onFailure,
    onTimeout: n.onTimeout,
    position: n.position,
  }));
  const edges: ScenarioEdge[] = (doc.edges ?? []).map((e) => ({
    from: e.from,
    to: e.to,
    label: e.label,
  }));
  return { nodes, edges, meta };
}
