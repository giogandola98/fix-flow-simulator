import { getJson, postJson } from './client';
import { Execution, ExecutionEvent, ExecutionReport } from '../types';

export const stopExecution = (id: string) => postJson<Record<string, never>, void>(`/executions/${id}/stop`, {});
export const getExecution = (id: string) => getJson<Execution>(`/executions/${id}`);
export const getExecutionEvents = (id: string) => getJson<ExecutionEvent[]>(`/executions/${id}/events`);
export const getExecutionMessages = (id: string) => getJson<ExecutionEvent[]>(`/executions/${id}/messages`);
export const getExecutionReport = (id: string) => getJson<ExecutionReport>(`/executions/${id}/report`);
