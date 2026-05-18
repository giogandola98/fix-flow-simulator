import { apiClient, getJson, postJson, putJson, deleteJson } from './client';
import { Scenario, ScenarioCreateRequest, ScenarioUpdateRequest } from '../types';

export const getScenarios = () => getJson<Scenario[]>('/scenarios');
export const getScenario = (id: string) => getJson<Scenario>(`/scenarios/${id}`);
export const createScenario = (req: ScenarioCreateRequest) => postJson<ScenarioCreateRequest, Scenario>('/scenarios', req);
export const deleteScenario = (id: string) => deleteJson(`/scenarios/${id}`);
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
export async function exportScenario(id: string): Promise<Blob> {
  const r = await apiClient.get(`/scenarios/${id}/export`, { responseType: 'blob' });
  return r.data as Blob;
}
export const executeScenario = (id: string, sessionId: string) =>
  postJson<{ sessionId: string }, { executionId: string }>(`/scenarios/${id}/execute`, { sessionId });
