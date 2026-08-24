import { postJson } from './client';

export const shutdownSimulator = () =>
  postJson<Record<string, never>, void>('/system/shutdown', {});
