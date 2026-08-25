import { getJson, postJson, putJson, deleteJson } from './client';
import { Scenario, ScenarioCreateRequest, ScenarioUpdateRequest } from '../types';

export const getScenarios = () => getJson<Scenario[]>('/scenarios');
export const getScenario = (id: string) => getJson<Scenario>(`/scenarios/${id}`);
export const createScenario = (req: ScenarioCreateRequest) => postJson<ScenarioCreateRequest, Scenario>('/scenarios', req);
export const deleteScenario = (id: string) => deleteJson(`/scenarios/${id}`);
/**
 * Copies a scenario server-side. The copy gets a new scenario id and new node ids with every
 * reference rewritten, so it can be edited without touching the original. Omitting `name` lets
 * the server default to "<name> (copy)".
 */
export const duplicateScenario = (id: string, name?: string) =>
  postJson<{ name?: string }, Scenario>(`/scenarios/${id}/duplicate`, { name });
export const updateScenario = (id: string, req: ScenarioUpdateRequest) =>
  putJson<ScenarioUpdateRequest, Scenario>('/scenarios/' + id, req);
export async function importScenario(file: File): Promise<Scenario> {
  const form = new FormData();
  form.append('file', file);
  const r = await fetch('/api/v1/scenarios/import', { method: 'POST', body: form });
  if (!r.ok) {
    const err = await r.json().catch(() => ({}));
    throw { status: r.status, message: (err as { message?: string }).message ?? r.statusText };
  }
  return r.json();
}
export const executeScenario = (id: string, sessionId: string) =>
  postJson<{ sessionId: string }, { executionId: string }>(`/scenarios/${id}/execute`, { sessionId });
