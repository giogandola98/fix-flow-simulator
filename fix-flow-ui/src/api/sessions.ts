import { getJson, postJson, putJson, deleteJson } from './client';
import { FIXSessionConfig, FIXSessionCreateRequest } from '../types';

export const getSessions = () => getJson<FIXSessionConfig[]>('/sessions');
export const getSession = (id: string) => getJson<FIXSessionConfig>(`/sessions/${id}`);
export const createSession = (req: FIXSessionCreateRequest) => postJson<FIXSessionCreateRequest, FIXSessionConfig>('/sessions', req);
export const updateSession = (id: string, req: FIXSessionCreateRequest) => putJson<FIXSessionCreateRequest, FIXSessionConfig>(`/sessions/${id}`, req);
export const deleteSession = (id: string) => deleteJson(`/sessions/${id}`);
export const connectSession = (id: string) => putJson<Record<string, never>, void>(`/sessions/${id}/connect`, {});
export const disconnectSession = (id: string) => putJson<Record<string, never>, void>(`/sessions/${id}/disconnect`, {});
export const getSessionStatus = (id: string) => getJson<{ sessionId: string; connected: boolean }>(`/sessions/${id}/status`);
