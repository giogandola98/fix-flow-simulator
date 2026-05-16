export type NodeType = 'START' | 'SEND_FIX' | 'EXPECT_FIX' | 'VALIDATE' | 'WAIT' | 'TIMEOUT' | 'DECISION' | 'BRANCH' | 'RETRY' | 'LOOP' | 'DELAY' | 'END' | 'END_PASS' | 'END_FAIL';
export type ExecutionStatus = 'RUNNING' | 'PASSED' | 'FAILED' | 'STOPPED';
export type FIXVersion = 'FIX_42' | 'FIX_44' | 'FIXT_11';
export type FIXMode = 'INITIATOR' | 'ACCEPTOR';
export type TimeUnit = 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS';
export type TimeoutAction = 'FAIL' | 'RETRY' | 'CONTINUE' | 'JUMP';

export interface TimeoutConfig { value: number; unit: TimeUnit; onTimeout: TimeoutAction; jumpTo?: string; }
export interface RetryPolicy { maxAttempts: number; delayMs: number; }

export interface ScenarioNode {
  id: string; name: string; type: NodeType; config: Record<string, unknown>;
  timeout?: TimeoutConfig; retryPolicy?: RetryPolicy;
  onSuccess?: string; onFailure?: string; onTimeout?: string;
  position?: { x: number; y: number };
}
export interface ScenarioEdge { from: string; to: string; label: string; }

export interface Scenario {
  id: string; name: string; description: string; version: string;
  sessionRef: string; nodeCount: number;
}

export interface ScenarioCreateRequest { name: string; description: string; sessionRef: string; yamlDsl: string; }
export interface ScenarioUpdateRequest { name?: string; description?: string; sessionRef?: string; yamlDsl: string; }

export interface FIXSessionConfig {
  id: string; name: string; mode: FIXMode; fixVersion: FIXVersion;
  defaultApplVerID: string; senderCompID: string; targetCompID: string;
  host: string; port: number; heartbeatInterval: number; reconnectInterval: number;
  resetOnLogon: boolean; resetOnLogout: boolean; connected: boolean;
}

export interface FIXSessionCreateRequest {
  name: string; mode: string; fixVersion: string; defaultApplVerID: string;
  senderCompID: string; targetCompID: string; host: string; port: number;
  heartbeatInterval: number; reconnectInterval: number;
  resetOnLogon: boolean; resetOnLogout: boolean;
}

export interface Execution {
  id: string; scenarioId: string; scenarioVersion: string; sessionId: string;
  status: ExecutionStatus; startTime: string; endTime?: string; currentNodeId?: string;
}

export interface ExecutionEvent {
  id: string; executionId: string; type: string; nodeId?: string;
  timestamp: string; detail?: string; rawFix?: string;
}

export interface FIXMessage {
  id: string; executionId: string; direction: 'INBOUND' | 'OUTBOUND';
  rawFix: string; fields: Record<number, string>; receivedAt: string;
}

export interface NodeResult { nodeId: string; nodeName: string; status: string; durationMs: number; }

export interface ExecutionReport {
  execution: Execution; events: ExecutionEvent[]; nodeResults: NodeResult[];
}

export interface ErrorResponse { status: number; error: string; message: string; timestamp: string; }

export interface ValidationError { tag: number; rule: string; expected: string; actual: string; message?: string; }
