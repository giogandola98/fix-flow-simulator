import axios, { AxiosInstance, AxiosResponse } from 'axios';

export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
});

apiClient.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response) {
      const data = error.response.data;
      return Promise.reject({ status: error.response.status, code: data?.code ?? 'UNKNOWN', message: data?.message ?? error.message, details: data?.details });
    }
    return Promise.reject({ status: 0, code: 'NETWORK_ERROR', message: error.message });
  },
);

export async function getJson<T>(url: string): Promise<T> {
  const r: AxiosResponse<T> = await apiClient.get(url);
  return r.data;
}
export async function postJson<TReq, TRes>(url: string, body: TReq): Promise<TRes> {
  const r: AxiosResponse<TRes> = await apiClient.post(url, body);
  return r.data;
}
export async function putJson<TReq, TRes>(url: string, body: TReq): Promise<TRes> {
  const r: AxiosResponse<TRes> = await apiClient.put(url, body);
  return r.data;
}
export async function deleteJson(url: string): Promise<void> {
  await apiClient.delete(url);
}
