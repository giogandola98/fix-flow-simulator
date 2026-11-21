import { apiClient, getJson, postJson, putJson, deleteJson } from './client';

export interface DataSet {
  id: string;
  name: string;
  packId?: string;
  columns: string[];
  rows?: string[][];
  rowCount?: number;
}

export type DataSetBody = Pick<DataSet, 'name' | 'columns' | 'rows'>;

export const getDataSets = () => getJson<DataSet[]>('/datasets');
export const getDataSet = (id: string) => getJson<DataSet>(`/datasets/${id}`);
export const createDataSet = (body: DataSetBody) => postJson<DataSetBody, DataSet>('/datasets', body);
export const updateDataSet = (id: string, body: DataSetBody) =>
  putJson<DataSetBody, DataSet>(`/datasets/${id}`, body);
export const deleteDataSet = (id: string) => deleteJson(`/datasets/${id}`);

export async function importDataSet(file: File, name: string): Promise<DataSet> {
  const body = new FormData();
  body.append('file', file);
  body.append('name', name);
  const r = await apiClient.post<DataSet>('/datasets/import', body, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export const dataSetExportUrl = (id: string) => `/api/v1/datasets/${id}/export`;
