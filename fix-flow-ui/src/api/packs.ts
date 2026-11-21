import { apiClient, getJson, deleteJson } from './client';

export interface PackCounts { scenarios: number; datasets: number; sessions: number; dictionaries: number; }

export interface InstalledPack {
  id: string;
  name: string;
  version: string;
  provider?: string;
  description?: string;
  installedAt: string;
  updatedAt: string;
  counts: PackCounts;
}

/** A template value of pack.yaml: the import form asks for it. */
export interface PackValue { name: string; description?: string; default?: string; required: boolean; }

/** The manifest of a zip that is not installed yet; installed* set when a pack with the same id is. */
export interface PackInspection {
  id: string;
  name: string;
  version: string;
  provider?: string;
  engineMin?: string;
  description?: string;
  values: PackValue[];
  installedVersion?: string;
  installedValues?: Record<string, string>;
}

export interface PackImportResult {
  packId: string;
  version: string;
  scenarios: number;
  datasets: number;
  sessions: number;
  dictionaries: number;
  warnings: string[];
}

export const getPacks = () => getJson<InstalledPack[]>('/packs');

export async function inspectPack(file: File): Promise<PackInspection> {
  const body = new FormData();
  body.append('file', file);
  const r = await apiClient.post<PackInspection>('/packs/inspect', body, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export async function importPack(file: File, values: Record<string, string>): Promise<PackImportResult> {
  const body = new FormData();
  body.append('file', file);
  body.append('values', JSON.stringify(values));
  const r = await apiClient.post<PackImportResult>('/packs', body, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120_000,
  });
  return r.data;
}

export const deletePack = (id: string) => deleteJson(`/packs/${encodeURIComponent(id)}`);

export const packExportUrl = (id: string) => `/api/v1/packs/${encodeURIComponent(id)}/export`;
