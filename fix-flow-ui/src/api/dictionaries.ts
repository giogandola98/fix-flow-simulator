import { apiClient, getJson, deleteJson } from './client';

export interface DictionaryInfo { name: string; sizeBytes: number; }

export const getDictionaries = () => getJson<DictionaryInfo[]>('/dictionaries');

export async function uploadDictionary(file: File): Promise<DictionaryInfo> {
  const body = new FormData();
  body.append('file', file);
  const r = await apiClient.post<DictionaryInfo>('/dictionaries', body, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export const deleteDictionary = (name: string) => deleteJson(`/dictionaries/${encodeURIComponent(name)}`);
