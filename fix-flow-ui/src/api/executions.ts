import { getJson, postJson } from './client';
import { ExecutionEvent, ExecutionReport, FIXMessage } from '../types';

export const stopExecution = (id: string) => postJson<Record<string, never>, void>(`/executions/${id}/stop`, {});
export const getExecutionEvents = (id: string) => getJson<ExecutionEvent[]>(`/executions/${id}/events`);
export const getExecutionMessages = (id: string) => getJson<FIXMessage[]>(`/executions/${id}/messages`);
export interface OperatorPromptData {
  executionId: string;
  nodeId: string;
  instruction: string;
  confirmLabel?: string | null;
  failLabel?: string | null;
}

/** The open OPERATOR prompt of the execution (or of one of its children), or null when none (204). */
export const getOperatorPrompt = async (id: string): Promise<OperatorPromptData | null> => {
  const data = await getJson<OperatorPromptData | ''>(`/executions/${id}/operator`);
  return data ? data : null;
};
export const answerOperator = (executionId: string, nodeId: string, ok: boolean, note: string) =>
  postJson<{ ok: boolean; note: string }, void>(`/executions/${executionId}/operator/${nodeId}`, { ok, note });
export const getExecutionReport = (id: string) => getJson<ExecutionReport>(`/executions/${id}/report`);
