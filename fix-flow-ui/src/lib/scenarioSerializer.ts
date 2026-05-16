import { ScenarioNode, ScenarioEdge } from '../types';

export interface ScenarioMeta {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
}

export function serializeToYaml(
  _nodes: ScenarioNode[],
  _edges: ScenarioEdge[],
  _meta: ScenarioMeta,
): string {
  return '';
}

export function parseFromYaml(_yaml: string): {
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  meta: ScenarioMeta;
} {
  return {
    nodes: [],
    edges: [],
    meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
  };
}
