# FIX Flow Simulator Implementation Plan — Part 3 of 3

*Tasks 34-52, Phases 10-15: React UI, Config Forms, Reporting, Tests, Documentation*

---

## Phase 10: React UI Shell + Canvas + Node Components (Tasks 34-38)

### Task 34: Vite project scaffold + dependencies

**Files:**
- Create: `fix-flow-ui/package.json`
- Create: `fix-flow-ui/vite.config.ts`
- Create: `fix-flow-ui/tailwind.config.ts`
- Create: `fix-flow-ui/postcss.config.js`
- Create: `fix-flow-ui/tsconfig.json`
- Create: `fix-flow-ui/tsconfig.node.json`
- Create: `fix-flow-ui/index.html`
- Create: `fix-flow-ui/src/main.tsx`
- Create: `fix-flow-ui/src/App.tsx`
- Create: `fix-flow-ui/src/index.css`
- Create: `fix-flow-ui/src/theme/colors.ts`

**Steps:**

1. Create `fix-flow-ui/package.json`:
```json
{
  "name": "fix-flow-ui",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "@xyflow/react": "^12.0.0",
    "zustand": "^4.5.2",
    "@tanstack/react-query": "^5.40.0",
    "@tanstack/react-query-devtools": "^5.40.0",
    "axios": "^1.7.2",
    "@stomp/stompjs": "^7.0.0",
    "sockjs-client": "^1.6.1",
    "react-router-dom": "^6.23.1",
    "react-hook-form": "^7.52.0",
    "js-yaml": "^4.1.0"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@types/sockjs-client": "^1.5.4",
    "@types/js-yaml": "^4.0.9",
    "@vitejs/plugin-react": "^4.3.1",
    "autoprefixer": "^10.4.19",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.4.5",
    "vite": "^5.3.1"
  }
}
```

2. Create `fix-flow-ui/vite.config.ts`:
```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    outDir: 'target/dist',
    emptyOutDir: true,
    sourcemap: true,
  },
});
```

3. Create `fix-flow-ui/tailwind.config.ts`:
```typescript
import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: {
          base: '#0f1117',
          panel: '#1a1d27',
          border: '#2a2d3a',
        },
        node: {
          start: '#3b82f6',
          send: '#22c55e',
          expect: '#eab308',
          validate: '#a855f7',
          decision: '#f97316',
          retry: '#06b6d4',
          wait: '#6b7280',
          endPass: '#22c55e',
          endFail: '#ef4444',
        },
        accent: {
          blue: '#3b82f6',
          green: '#22c55e',
          red: '#ef4444',
          amber: '#f59e0b',
        },
      },
    },
  },
  plugins: [],
};

export default config;
```

4. Create `fix-flow-ui/postcss.config.js`:
```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

5. Create `fix-flow-ui/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "useDefineForClassFields": true,
    "noEmit": true
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

6. Create `fix-flow-ui/tsconfig.node.json`:
```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

7. Create `fix-flow-ui/index.html`:
```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>FIX Flow Simulator</title>
  </head>
  <body class="bg-bg-base">
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

8. Create `fix-flow-ui/src/theme/colors.ts`:
```typescript
export const colors = {
  bgBase: '#0f1117',
  bgPanel: '#1a1d27',
  bgBorder: '#2a2d3a',
  accent: {
    blue: '#3b82f6',
    green: '#22c55e',
    red: '#ef4444',
    amber: '#f59e0b',
    yellow: '#eab308',
    purple: '#a855f7',
    orange: '#f97316',
    cyan: '#06b6d4',
    gray: '#6b7280',
  },
  node: {
    START: '#3b82f6',
    SEND_FIX: '#22c55e',
    EXPECT_FIX: '#eab308',
    VALIDATE: '#a855f7',
    DECISION: '#f97316',
    BRANCH: '#f97316',
    RETRY: '#06b6d4',
    LOOP: '#06b6d4',
    WAIT: '#6b7280',
    DELAY: '#6b7280',
    TIMEOUT: '#6b7280',
    END_PASS: '#22c55e',
    END_FAIL: '#ef4444',
  },
} as const;

export type NodeColorKey = keyof typeof colors.node;
```

9. Create `fix-flow-ui/src/index.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

html, body, #root {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

* { box-sizing: border-box; }

.react-flow__node { font-family: inherit; }
.react-flow__background { background: #0f1117; }
```

10. Create `fix-flow-ui/src/App.tsx` (initial placeholder, replaced in Task 37):
```typescript
export default function App() {
  return (
    <div className="h-screen w-screen flex items-center justify-center bg-bg-base text-gray-100">
      <div className="text-xl">FIX Flow Simulator — boot</div>
    </div>
  );
}
```

11. Create `fix-flow-ui/src/main.tsx`:
```typescript
import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5_000,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>,
);
```

12. Run:
```bash
cd fix-flow-ui && npm install && npm run build
```

13. Commit:
```bash
git add fix-flow-ui
git commit -m "feat(ui): scaffold Vite+React+TS+Tailwind project with dependencies"
```

---

### Task 35: API client + type definitions

**Files:**
- Create: `fix-flow-ui/src/types/index.ts`
- Create: `fix-flow-ui/src/api/client.ts`
- Create: `fix-flow-ui/src/api/scenarios.ts`
- Create: `fix-flow-ui/src/api/executions.ts`
- Create: `fix-flow-ui/src/api/sessions.ts`

**Steps:**

1. Create `fix-flow-ui/src/types/index.ts`:
```typescript
export type NodeType =
  | 'START'
  | 'SEND_FIX'
  | 'EXPECT_FIX'
  | 'VALIDATE'
  | 'WAIT'
  | 'TIMEOUT'
  | 'DECISION'
  | 'BRANCH'
  | 'RETRY'
  | 'LOOP'
  | 'DELAY'
  | 'END_PASS'
  | 'END_FAIL';

export type ExecutionStatus = 'RUNNING' | 'PASSED' | 'FAILED' | 'STOPPED';
export type FIXVersion = 'FIX_42' | 'FIX_44' | 'FIXT_11';
export type FIXMode = 'INITIATOR' | 'ACCEPTOR';
export type TimeUnit = 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS';
export type TimeoutAction = 'FAIL' | 'RETRY' | 'CONTINUE' | 'JUMP';

export interface TimeoutConfig {
  value: number;
  unit: TimeUnit;
  onTimeout: TimeoutAction;
  jumpTo?: string;
}

export interface RetryPolicy {
  maxAttempts: number;
  delayMs: number;
}

export interface ScenarioNode {
  id: string;
  name: string;
  type: NodeType;
  config: Record<string, unknown>;
  timeout?: TimeoutConfig;
  retryPolicy?: RetryPolicy;
  onSuccess?: string;
  onFailure?: string;
  onTimeout?: string;
  position?: { x: number; y: number };
}

export interface ScenarioEdge {
  from: string;
  to: string;
  label: string;
}

export interface Scenario {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
  yamlDsl: string;
  createdAt: string;
  updatedAt: string;
}

export interface ScenarioCreateRequest {
  name: string;
  description: string;
  sessionRef: string;
  yamlDsl: string;
}

export interface ScenarioUpdateRequest {
  name?: string;
  description?: string;
  sessionRef?: string;
  yamlDsl: string;
}

export interface FIXSessionConfig {
  id: string;
  name: string;
  mode: FIXMode;
  fixVersion: FIXVersion;
  defaultApplVerID: string;
  senderCompID: string;
  targetCompID: string;
  host: string;
  port: number;
  heartbeatInterval: number;
  reconnectInterval: number;
  resetOnLogon: boolean;
  resetOnLogout: boolean;
  connected: boolean;
}

export interface FIXSessionCreateRequest {
  name: string;
  mode: FIXMode;
  fixVersion: FIXVersion;
  defaultApplVerID: string;
  senderCompID: string;
  targetCompID: string;
  host: string;
  port: number;
  heartbeatInterval: number;
  reconnectInterval: number;
  resetOnLogon: boolean;
  resetOnLogout: boolean;
}

export interface Execution {
  id: string;
  scenarioId: string;
  scenarioVersion: string;
  sessionId: string;
  status: ExecutionStatus;
  startTime: string;
  endTime?: string;
  currentNodeId?: string;
}

export interface ExecutionEvent {
  id: string;
  executionId: string;
  type: string;
  nodeId?: string;
  timestamp: string;
  detail?: string;
  rawFix?: string;
}

export interface FIXMessage {
  id: string;
  executionId: string;
  direction: 'INBOUND' | 'OUTBOUND';
  rawFix: string;
  fields: Record<number, string>;
  receivedAt: string;
}

export interface ValidationError {
  tag: number;
  rule: string;
  expected: string;
  actual: string;
  message?: string;
}

export interface ExecutionReport {
  executionId: string;
  scenarioName: string;
  scenarioVersion: string;
  sessionName: string;
  status: ExecutionStatus;
  startTime: string;
  endTime: string;
  durationMs: number;
  nodeResults: Array<{
    nodeId: string;
    nodeName: string;
    status: string;
    durationMs: number;
  }>;
  rawFIXMessages: string[];
  validationErrors: ValidationError[];
  statistics: Record<string, unknown>;
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  details?: Record<string, unknown>;
}
```

2. Create `fix-flow-ui/src/api/client.ts`:
```typescript
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
      return Promise.reject({
        status: error.response.status,
        code: data?.code ?? 'UNKNOWN',
        message: data?.message ?? error.message,
        details: data?.details,
      });
    }
    return Promise.reject({
      status: 0,
      code: 'NETWORK_ERROR',
      message: error.message,
    });
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
```

3. Create `fix-flow-ui/src/api/scenarios.ts`:
```typescript
import { apiClient, getJson, postJson, putJson, deleteJson } from './client';
import {
  Scenario,
  ScenarioCreateRequest,
  ScenarioUpdateRequest,
  Execution,
} from '../types';

export function getScenarios(): Promise<Scenario[]> {
  return getJson<Scenario[]>('/scenarios');
}

export function getScenario(id: string): Promise<Scenario> {
  return getJson<Scenario>(`/scenarios/${id}`);
}

export function createScenario(req: ScenarioCreateRequest): Promise<Scenario> {
  return postJson<ScenarioCreateRequest, Scenario>('/scenarios', req);
}

export function updateScenario(id: string, req: ScenarioUpdateRequest): Promise<Scenario> {
  return putJson<ScenarioUpdateRequest, Scenario>(`/scenarios/${id}`, req);
}

export function deleteScenario(id: string): Promise<void> {
  return deleteJson(`/scenarios/${id}`);
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

export function validateScenario(id: string): Promise<ValidationResult> {
  return postJson<Record<string, never>, ValidationResult>(`/scenarios/${id}/validate`, {});
}

export async function importScenario(file: File): Promise<Scenario> {
  const form = new FormData();
  form.append('file', file);
  const r = await apiClient.post<Scenario>('/scenarios/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export async function exportScenario(id: string): Promise<Blob> {
  const r = await apiClient.get(`/scenarios/${id}/export`, { responseType: 'blob' });
  return r.data as Blob;
}

export interface ExecuteRequest {
  sessionId: string;
}

export function executeScenario(id: string, sessionId: string): Promise<Execution> {
  return postJson<ExecuteRequest, Execution>(`/scenarios/${id}/execute`, { sessionId });
}

export function reloadScenario(id: string): Promise<Scenario> {
  return postJson<Record<string, never>, Scenario>(`/scenarios/${id}/reload`, {});
}
```

4. Create `fix-flow-ui/src/api/executions.ts`:
```typescript
import { getJson, postJson } from './client';
import { Execution, ExecutionEvent, FIXMessage, ExecutionReport } from '../types';

export function stopExecution(id: string): Promise<Execution> {
  return postJson<Record<string, never>, Execution>(`/executions/${id}/stop`, {});
}

export function getExecution(id: string): Promise<Execution> {
  return getJson<Execution>(`/executions/${id}`);
}

export function getExecutionEvents(id: string): Promise<ExecutionEvent[]> {
  return getJson<ExecutionEvent[]>(`/executions/${id}/events`);
}

export function getExecutionMessages(id: string): Promise<FIXMessage[]> {
  return getJson<FIXMessage[]>(`/executions/${id}/messages`);
}

export function getExecutionReport(id: string): Promise<ExecutionReport> {
  return getJson<ExecutionReport>(`/executions/${id}/report`);
}

export function getExecutionReportDownloadUrl(id: string): string {
  return `/api/v1/executions/${id}/report/download`;
}
```

5. Create `fix-flow-ui/src/api/sessions.ts`:
```typescript
import { getJson, postJson, putJson, deleteJson } from './client';
import { FIXSessionConfig, FIXSessionCreateRequest } from '../types';

export function getSessions(): Promise<FIXSessionConfig[]> {
  return getJson<FIXSessionConfig[]>('/sessions');
}

export function getSession(id: string): Promise<FIXSessionConfig> {
  return getJson<FIXSessionConfig>(`/sessions/${id}`);
}

export function createSession(req: FIXSessionCreateRequest): Promise<FIXSessionConfig> {
  return postJson<FIXSessionCreateRequest, FIXSessionConfig>('/sessions', req);
}

export function updateSession(
  id: string,
  req: FIXSessionCreateRequest,
): Promise<FIXSessionConfig> {
  return putJson<FIXSessionCreateRequest, FIXSessionConfig>(`/sessions/${id}`, req);
}

export function deleteSession(id: string): Promise<void> {
  return deleteJson(`/sessions/${id}`);
}

export function connectSession(id: string): Promise<FIXSessionConfig> {
  return postJson<Record<string, never>, FIXSessionConfig>(`/sessions/${id}/connect`, {});
}

export function disconnectSession(id: string): Promise<FIXSessionConfig> {
  return postJson<Record<string, never>, FIXSessionConfig>(`/sessions/${id}/disconnect`, {});
}

export interface SessionStatus {
  id: string;
  connected: boolean;
  lastHeartbeat?: string;
  msgSeqNumIn: number;
  msgSeqNumOut: number;
}

export function getSessionStatus(id: string): Promise<SessionStatus> {
  return getJson<SessionStatus>(`/sessions/${id}/status`);
}
```

6. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no TypeScript errors.

7. Commit:
```bash
git add fix-flow-ui/src
git commit -m "feat(ui): add API client, type definitions, and REST functions"
```

---

### Task 36: Zustand stores + WebSocket client

**Files:**
- Create: `fix-flow-ui/src/store/scenarioStore.ts`
- Create: `fix-flow-ui/src/store/executionStore.ts`
- Create: `fix-flow-ui/src/store/sessionStore.ts`
- Create: `fix-flow-ui/src/app/wsClient.ts`

**Steps:**

1. Create `fix-flow-ui/src/store/scenarioStore.ts`:
```typescript
import { create } from 'zustand';
import { Scenario, ScenarioNode, ScenarioEdge } from '../types';

interface ScenarioState {
  scenarios: Scenario[];
  activeScenario: Scenario | null;
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  selectedNodeId: string | null;
  isDirty: boolean;
  setScenarios: (s: Scenario[]) => void;
  setActiveScenario: (s: Scenario | null) => void;
  setNodes: (nodes: ScenarioNode[]) => void;
  setEdges: (edges: ScenarioEdge[]) => void;
  updateNode: (id: string, patch: Partial<ScenarioNode>) => void;
  addNode: (node: ScenarioNode) => void;
  removeNode: (id: string) => void;
  addEdge: (edge: ScenarioEdge) => void;
  removeEdge: (from: string, to: string, label: string) => void;
  setSelectedNode: (id: string | null) => void;
  markDirty: () => void;
  markClean: () => void;
}

export const useScenarioStore = create<ScenarioState>((set) => ({
  scenarios: [],
  activeScenario: null,
  nodes: [],
  edges: [],
  selectedNodeId: null,
  isDirty: false,
  setScenarios: (scenarios) => set({ scenarios }),
  setActiveScenario: (activeScenario) => set({ activeScenario, isDirty: false }),
  setNodes: (nodes) => set({ nodes }),
  setEdges: (edges) => set({ edges }),
  updateNode: (id, patch) =>
    set((s) => ({
      nodes: s.nodes.map((n) => (n.id === id ? { ...n, ...patch } : n)),
      isDirty: true,
    })),
  addNode: (node) => set((s) => ({ nodes: [...s.nodes, node], isDirty: true })),
  removeNode: (id) =>
    set((s) => ({
      nodes: s.nodes.filter((n) => n.id !== id),
      edges: s.edges.filter((e) => e.from !== id && e.to !== id),
      isDirty: true,
    })),
  addEdge: (edge) => set((s) => ({ edges: [...s.edges, edge], isDirty: true })),
  removeEdge: (from, to, label) =>
    set((s) => ({
      edges: s.edges.filter((e) => !(e.from === from && e.to === to && e.label === label)),
      isDirty: true,
    })),
  setSelectedNode: (id) => set({ selectedNodeId: id }),
  markDirty: () => set({ isDirty: true }),
  markClean: () => set({ isDirty: false }),
}));
```

2. Create `fix-flow-ui/src/store/executionStore.ts`:
```typescript
import { create } from 'zustand';
import { ExecutionEvent, ExecutionStatus, FIXMessage } from '../types';

export type NodeRuntimeStatus = 'idle' | 'running' | 'passed' | 'failed';

interface ExecutionState {
  activeExecutionId: string | null;
  executionStatus: ExecutionStatus | 'IDLE';
  events: ExecutionEvent[];
  messages: FIXMessage[];
  nodeStatuses: Record<string, NodeRuntimeStatus>;
  startedAt: string | null;
  endedAt: string | null;
  setActiveExecution: (id: string | null) => void;
  updateStatus: (status: ExecutionStatus | 'IDLE') => void;
  addEvent: (event: ExecutionEvent) => void;
  addMessage: (msg: FIXMessage) => void;
  setNodeStatus: (nodeId: string, status: NodeRuntimeStatus) => void;
  setStartedAt: (iso: string) => void;
  setEndedAt: (iso: string) => void;
  reset: () => void;
}

export const useExecutionStore = create<ExecutionState>((set) => ({
  activeExecutionId: null,
  executionStatus: 'IDLE',
  events: [],
  messages: [],
  nodeStatuses: {},
  startedAt: null,
  endedAt: null,
  setActiveExecution: (id) => set({ activeExecutionId: id }),
  updateStatus: (status) => set({ executionStatus: status }),
  addEvent: (event) => set((s) => ({ events: [...s.events, event] })),
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  setNodeStatus: (nodeId, status) =>
    set((s) => ({ nodeStatuses: { ...s.nodeStatuses, [nodeId]: status } })),
  setStartedAt: (iso) => set({ startedAt: iso }),
  setEndedAt: (iso) => set({ endedAt: iso }),
  reset: () =>
    set({
      activeExecutionId: null,
      executionStatus: 'IDLE',
      events: [],
      messages: [],
      nodeStatuses: {},
      startedAt: null,
      endedAt: null,
    }),
}));
```

3. Create `fix-flow-ui/src/store/sessionStore.ts`:
```typescript
import { create } from 'zustand';
import { FIXSessionConfig } from '../types';

interface SessionState {
  sessions: FIXSessionConfig[];
  activeSession: FIXSessionConfig | null;
  setSessions: (s: FIXSessionConfig[]) => void;
  setActiveSession: (s: FIXSessionConfig | null) => void;
  updateSession: (id: string, patch: Partial<FIXSessionConfig>) => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  sessions: [],
  activeSession: null,
  setSessions: (sessions) => set({ sessions }),
  setActiveSession: (activeSession) => set({ activeSession }),
  updateSession: (id, patch) =>
    set((s) => ({
      sessions: s.sessions.map((sess) => (sess.id === id ? { ...sess, ...patch } : sess)),
      activeSession:
        s.activeSession?.id === id ? { ...s.activeSession, ...patch } : s.activeSession,
    })),
}));
```

4. Create `fix-flow-ui/src/app/wsClient.ts`:
```typescript
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ExecutionEvent, FIXMessage } from '../types';

export interface SessionStatusEvent {
  sessionId: string;
  status: 'UP' | 'DOWN' | 'LOGON' | 'LOGOUT';
  timestamp: string;
}

class WsClient {
  private client: Client | null = null;
  private connectPromise: Promise<void> | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();

  connect(): Promise<void> {
    if (this.connectPromise) return this.connectPromise;
    this.connectPromise = new Promise((resolve, reject) => {
      this.client = new Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5_000,
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        debug: () => undefined,
        onConnect: () => resolve(),
        onStompError: (frame) => reject(new Error(frame.headers['message'] ?? 'STOMP error')),
        onWebSocketError: (err) => reject(err),
      });
      this.client.activate();
    });
    return this.connectPromise;
  }

  async subscribeExecution(
    executionId: string,
    onEvent: (event: ExecutionEvent) => void,
    onMessage: (msg: FIXMessage) => void,
  ): Promise<() => void> {
    await this.connect();
    if (!this.client) throw new Error('STOMP client not initialised');
    const eventsKey = `events:${executionId}`;
    const messagesKey = `messages:${executionId}`;
    const eventsSub = this.client.subscribe(
      `/topic/executions/${executionId}/events`,
      (frame: IMessage) => onEvent(JSON.parse(frame.body) as ExecutionEvent),
    );
    const messagesSub = this.client.subscribe(
      `/topic/executions/${executionId}/messages`,
      (frame: IMessage) => onMessage(JSON.parse(frame.body) as FIXMessage),
    );
    this.subscriptions.set(eventsKey, eventsSub);
    this.subscriptions.set(messagesKey, messagesSub);
    return () => {
      eventsSub.unsubscribe();
      messagesSub.unsubscribe();
      this.subscriptions.delete(eventsKey);
      this.subscriptions.delete(messagesKey);
    };
  }

  async subscribeSession(
    sessionId: string,
    onStatus: (status: SessionStatusEvent) => void,
  ): Promise<() => void> {
    await this.connect();
    if (!this.client) throw new Error('STOMP client not initialised');
    const key = `session:${sessionId}`;
    const sub = this.client.subscribe(
      `/topic/sessions/${sessionId}/status`,
      (frame: IMessage) => onStatus(JSON.parse(frame.body) as SessionStatusEvent),
    );
    this.subscriptions.set(key, sub);
    return () => {
      sub.unsubscribe();
      this.subscriptions.delete(key);
    };
  }

  disconnect(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions.clear();
    if (this.client) this.client.deactivate();
    this.client = null;
    this.connectPromise = null;
  }
}

export const wsClient = new WsClient();
```

5. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no errors.

6. Commit:
```bash
git add fix-flow-ui/src/store fix-flow-ui/src/app
git commit -m "feat(ui): add Zustand stores and STOMP/SockJS WebSocket client"
```

---

### Task 37: App layout + TopBar + FlowCanvas skeleton

**Files:**
- Modify: `fix-flow-ui/src/App.tsx`
- Create: `fix-flow-ui/src/components/TopBar.tsx`
- Create: `fix-flow-ui/src/canvas/FlowCanvas.tsx`
- Create: `fix-flow-ui/src/canvas/CanvasToolbar.tsx`
- Create: `fix-flow-ui/src/canvas/edges/FlowEdge.tsx`
- Create: `fix-flow-ui/src/panels/left/LeftPanel.tsx` (stub)
- Create: `fix-flow-ui/src/panels/right/RightPanel.tsx` (stub)
- Create: `fix-flow-ui/src/panels/bottom/BottomPanel.tsx` (stub)

**Steps:**

1. Replace `fix-flow-ui/src/App.tsx`:
```typescript
import TopBar from './components/TopBar';
import FlowCanvas from './canvas/FlowCanvas';
import LeftPanel from './panels/left/LeftPanel';
import RightPanel from './panels/right/RightPanel';
import BottomPanel from './panels/bottom/BottomPanel';

export default function App() {
  return (
    <div className="h-screen flex flex-col bg-[#0f1117] text-gray-100">
      <TopBar />
      <div
        className="flex-1 overflow-hidden"
        style={{
          display: 'grid',
          gridTemplateColumns: '240px 1fr 320px',
          gridTemplateRows: '1fr',
        }}
      >
        <LeftPanel />
        <FlowCanvas />
        <RightPanel />
      </div>
      <BottomPanel />
    </div>
  );
}
```

2. Create `fix-flow-ui/src/components/TopBar.tsx`:
```typescript
import { useMutation } from '@tanstack/react-query';
import { useScenarioStore } from '../store/scenarioStore';
import { useSessionStore } from '../store/sessionStore';
import { useExecutionStore } from '../store/executionStore';
import { executeScenario, validateScenario, updateScenario } from '../api/scenarios';
import { stopExecution } from '../api/executions';
import { serializeToYaml } from '../lib/scenarioSerializer';

export default function TopBar() {
  const activeScenario = useScenarioStore((s) => s.activeScenario);
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const isDirty = useScenarioStore((s) => s.isDirty);
  const markClean = useScenarioStore((s) => s.markClean);
  const activeSession = useSessionStore((s) => s.activeSession);
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  const executionStatus = useExecutionStore((s) => s.executionStatus);
  const setActiveExecution = useExecutionStore((s) => s.setActiveExecution);
  const updateStatus = useExecutionStore((s) => s.updateStatus);

  const runMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario || !activeSession) throw new Error('No scenario or session');
      return executeScenario(activeScenario.id, activeSession.id);
    },
    onSuccess: (exec) => {
      setActiveExecution(exec.id);
      updateStatus('RUNNING');
    },
  });

  const stopMutation = useMutation({
    mutationFn: async () => {
      if (!activeExecutionId) throw new Error('No active execution');
      return stopExecution(activeExecutionId);
    },
    onSuccess: () => updateStatus('STOPPED'),
  });

  const validateMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario) throw new Error('No active scenario');
      return validateScenario(activeScenario.id);
    },
    onSuccess: (res) => {
      if (res.valid) alert('Scenario is valid');
      else alert(`Validation errors:\n${res.errors.join('\n')}`);
    },
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario) throw new Error('No active scenario');
      const yaml = serializeToYaml(nodes, edges, {
        id: activeScenario.id,
        name: activeScenario.name,
        description: activeScenario.description,
        version: activeScenario.version,
        sessionRef: activeScenario.sessionRef,
      });
      return updateScenario(activeScenario.id, {
        name: activeScenario.name,
        description: activeScenario.description,
        sessionRef: activeScenario.sessionRef,
        yamlDsl: yaml,
      });
    },
    onSuccess: () => markClean(),
  });

  const isRunning = executionStatus === 'RUNNING';

  return (
    <div className="h-12 bg-[#1a1d27] border-b border-[#2a2d3a] flex items-center px-4 gap-4">
      <div className="font-semibold text-blue-400">FIX Flow Simulator</div>
      <div className="text-sm text-gray-400">{activeScenario?.name ?? 'No scenario loaded'}</div>
      <div className="flex-1" />
      <button
        className="px-3 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40 text-sm"
        onClick={() => runMutation.mutate()}
        disabled={!activeScenario || !activeSession || isRunning}
      >
        Run
      </button>
      <button
        className="px-3 py-1 rounded bg-red-600 hover:bg-red-500 disabled:opacity-40 text-sm"
        onClick={() => stopMutation.mutate()}
        disabled={!isRunning}
      >
        Stop
      </button>
      <button
        className="px-3 py-1 rounded bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-sm"
        onClick={() => validateMutation.mutate()}
        disabled={!activeScenario}
      >
        Validate
      </button>
      <button
        className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 disabled:opacity-40 text-sm"
        onClick={() => saveMutation.mutate()}
        disabled={!activeScenario}
      >
        Save{isDirty ? ' •' : ''}
      </button>
      <div className="w-px h-6 bg-[#2a2d3a]" />
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Import</button>
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Export</button>
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Settings</button>
    </div>
  );
}
```

3. Create `fix-flow-ui/src/canvas/edges/FlowEdge.tsx`:
```typescript
import { BaseEdge, EdgeProps, getBezierPath } from '@xyflow/react';

export function FlowEdge(props: EdgeProps) {
  const { sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, label } = props;
  const [path] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  const labelStr = String(label ?? '');
  const color =
    labelStr === 'success'
      ? '#22c55e'
      : labelStr === 'failure'
        ? '#ef4444'
        : labelStr === 'timeout'
          ? '#f59e0b'
          : '#6b7280';
  return <BaseEdge id={props.id} path={path} style={{ stroke: color, strokeWidth: 2 }} />;
}
```

4. Create `fix-flow-ui/src/canvas/CanvasToolbar.tsx`:
```typescript
import { useReactFlow } from '@xyflow/react';

export function CanvasToolbar() {
  const { zoomIn, zoomOut, fitView } = useReactFlow();
  return (
    <div className="absolute top-2 right-2 z-10 flex gap-1 bg-[#1a1d27] border border-[#2a2d3a] rounded p-1">
      <button
        className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]"
        onClick={() => zoomIn()}
      >
        +
      </button>
      <button
        className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]"
        onClick={() => zoomOut()}
      >
        -
      </button>
      <button
        className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]"
        onClick={() => fitView()}
      >
        Fit
      </button>
    </div>
  );
}
```

5. Create `fix-flow-ui/src/canvas/FlowCanvas.tsx`:
```typescript
import { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  Controls,
  Connection,
  Node,
  Edge,
  useReactFlow,
  applyNodeChanges,
  applyEdgeChanges,
  NodeChange,
  EdgeChange,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useScenarioStore } from '../store/scenarioStore';
import { useExecutionStore } from '../store/executionStore';
import { CanvasToolbar } from './CanvasToolbar';
import { FlowEdge } from './edges/FlowEdge';
import { nodeTypes } from './nodes/nodeTypes';
import { NodeType, ScenarioNode } from '../types';

const edgeTypes = { default: FlowEdge };

function InnerCanvas() {
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const addNode = useScenarioStore((s) => s.addNode);
  const addEdge = useScenarioStore((s) => s.addEdge);
  const setSelectedNode = useScenarioStore((s) => s.setSelectedNode);
  const nodeStatuses = useExecutionStore((s) => s.nodeStatuses);
  const { screenToFlowPosition } = useReactFlow();

  const rfNodes: Node[] = useMemo(
    () =>
      nodes.map((n) => ({
        id: n.id,
        type: n.type,
        position: n.position ?? { x: 100, y: 100 },
        data: {
          label: n.name,
          config: n.config,
          status: nodeStatuses[n.id] ?? 'idle',
        },
      })),
    [nodes, nodeStatuses],
  );

  const rfEdges: Edge[] = useMemo(
    () =>
      edges.map((e, i) => ({
        id: `e${i}-${e.from}-${e.to}-${e.label}`,
        source: e.from,
        target: e.to,
        label: e.label,
        type: 'default',
      })),
    [edges],
  );

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      const updated = applyNodeChanges(changes, rfNodes);
      setNodes(
        updated.map((rn) => {
          const orig = nodes.find((n) => n.id === rn.id);
          return {
            ...(orig as ScenarioNode),
            position: rn.position,
          };
        }),
      );
    },
    [rfNodes, nodes, setNodes],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      const updated = applyEdgeChanges(changes, rfEdges);
      setEdges(
        updated.map((e) => ({
          from: e.source,
          to: e.target,
          label: String(e.label ?? 'success'),
        })),
      );
    },
    [rfEdges, setEdges],
  );

  const onConnect = useCallback(
    (conn: Connection) => {
      if (conn.source && conn.target) {
        addEdge({ from: conn.source, to: conn.target, label: 'success' });
      }
    },
    [addEdge],
  );

  const onDragOver = useCallback((evt: React.DragEvent) => {
    evt.preventDefault();
    evt.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (evt: React.DragEvent) => {
      evt.preventDefault();
      const type = evt.dataTransfer.getData('application/fix-flow-node-type') as NodeType;
      if (!type) return;
      const pos = screenToFlowPosition({ x: evt.clientX, y: evt.clientY });
      const id = `node-${Date.now()}`;
      addNode({
        id,
        name: type,
        type,
        config: {},
        position: pos,
      });
    },
    [addNode, screenToFlowPosition],
  );

  return (
    <div className="relative w-full h-full" onDrop={onDrop} onDragOver={onDragOver}>
      <ReactFlow
        nodes={rfNodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={(_, n) => setSelectedNode(n.id)}
        onPaneClick={() => setSelectedNode(null)}
        fitView
      >
        <Background color="#2a2d3a" gap={20} />
        <Controls className="!bg-[#1a1d27] !border-[#2a2d3a]" />
      </ReactFlow>
      <CanvasToolbar />
    </div>
  );
}

export default function FlowCanvas() {
  return (
    <div className="w-full h-full bg-[#0f1117]">
      <ReactFlowProvider>
        <InnerCanvas />
      </ReactFlowProvider>
    </div>
  );
}
```

6. Create stub `fix-flow-ui/src/panels/left/LeftPanel.tsx`:
```typescript
export default function LeftPanel() {
  return (
    <div className="bg-[#1a1d27] border-r border-[#2a2d3a] p-2 text-sm text-gray-400">
      Left Panel
    </div>
  );
}
```

7. Create stub `fix-flow-ui/src/panels/right/RightPanel.tsx`:
```typescript
export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] p-2 text-sm text-gray-400">
      Right Panel
    </div>
  );
}
```

8. Create stub `fix-flow-ui/src/panels/bottom/BottomPanel.tsx`:
```typescript
export default function BottomPanel() {
  return (
    <div className="h-48 bg-[#1a1d27] border-t border-[#2a2d3a] p-2 text-sm text-gray-400">
      Bottom Panel
    </div>
  );
}
```

9. Create empty placeholder `fix-flow-ui/src/lib/scenarioSerializer.ts` (full impl in Task 45):
```typescript
import { ScenarioNode, ScenarioEdge } from '../types';

export interface ScenarioMeta {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
}

export function serializeToYaml(
  _nodes: ScenarioNode[],
  _edges: ScenarioEdge[],
  _meta: ScenarioMeta,
): string {
  return '';
}

export function parseFromYaml(_yaml: string): {
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  meta: ScenarioMeta;
} {
  return {
    nodes: [],
    edges: [],
    meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
  };
}
```

10. Create empty placeholder `fix-flow-ui/src/canvas/nodes/nodeTypes.ts` (full impl in Task 38):
```typescript
import { NodeTypes } from '@xyflow/react';

export const nodeTypes: NodeTypes = {};
```

11. Run:
```bash
cd fix-flow-ui && npm run dev
```
Open http://localhost:5173 — verify dark background and top bar render.

12. Commit:
```bash
git add fix-flow-ui/src
git commit -m "feat(ui): add App layout, TopBar, FlowCanvas skeleton with edges"
```

---

### Task 38: Custom node components

**Files:**
- Create: `fix-flow-ui/src/canvas/nodes/BaseNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/StartNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/SendFIXNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/ExpectFIXNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/ValidateNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/DecisionNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/EndPassNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/EndFailNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/RetryNode.tsx`
- Create: `fix-flow-ui/src/canvas/nodes/WaitNode.tsx`
- Modify: `fix-flow-ui/src/canvas/nodes/nodeTypes.ts`

**Steps:**

1. Create `fix-flow-ui/src/canvas/nodes/BaseNode.tsx`:
```typescript
import { Handle, Position } from '@xyflow/react';
import { ReactNode } from 'react';

interface Props {
  label: string;
  type: string;
  borderColor: string;
  selected?: boolean;
  status?: string;
  filled?: boolean;
  shape?: 'rect' | 'diamond' | 'circle';
  children?: ReactNode;
  handles?: { target: boolean; source: boolean };
}

export function BaseNode({
  label,
  type,
  borderColor,
  selected,
  status,
  filled,
  shape = 'rect',
  children,
  handles = { target: true, source: true },
}: Props) {
  const ringColor =
    status === 'running'
      ? 'animate-pulse ring-2 ring-green-400'
      : status === 'passed'
        ? 'ring-2 ring-green-500'
        : status === 'failed'
          ? 'ring-2 ring-red-500'
          : selected
            ? 'ring-2 ring-blue-400'
            : '';

  const bg = filled ? borderColor : '#1a1d27';
  const textColor = filled ? 'text-white' : 'text-gray-100';
  const isDiamond = shape === 'diamond';
  const isCircle = shape === 'circle';

  return (
    <div
      className={`relative ${isDiamond ? 'rotate-45' : ''} ${isCircle ? 'rounded-full' : 'rounded'} ${ringColor}`}
      style={{
        background: bg,
        border: `2px solid ${borderColor}`,
        minWidth: isCircle ? 60 : 160,
        minHeight: isCircle ? 60 : 60,
        padding: isDiamond ? 16 : 8,
      }}
    >
      {handles.target && <Handle type="target" position={Position.Top} />}
      <div className={`${isDiamond ? '-rotate-45' : ''} ${textColor}`}>
        <div className="text-[10px] uppercase tracking-wide opacity-70">{type}</div>
        <div className="text-sm font-medium truncate" title={label}>
          {label}
        </div>
        {children && <div className="mt-1 text-xs">{children}</div>}
      </div>
      {handles.source && <Handle type="source" position={Position.Bottom} />}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/canvas/nodes/StartNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function StartNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="START"
      borderColor="#3b82f6"
      selected={selected}
      status={data.status as string}
      shape="circle"
      handles={{ target: false, source: true }}
    />
  );
}
```

3. Create `fix-flow-ui/src/canvas/nodes/SendFIXNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function SendFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="SEND_FIX"
      borderColor="#22c55e"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        MsgType: <span className="text-green-400">{cfg.msgType ?? '?'}</span>
      </div>
    </BaseNode>
  );
}
```

4. Create `fix-flow-ui/src/canvas/nodes/ExpectFIXNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function ExpectFIXNode({ data, selected }: NodeProps) {
  const cfg = (data.config as Record<string, string>) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="EXPECT_FIX"
      borderColor="#eab308"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        MsgType: <span className="text-yellow-400">{cfg.msgType ?? '?'}</span>
      </div>
    </BaseNode>
  );
}
```

5. Create `fix-flow-ui/src/canvas/nodes/ValidateNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function ValidateNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { rules?: unknown[] }) ?? {};
  const count = Array.isArray(cfg.rules) ? cfg.rules.length : 0;
  return (
    <BaseNode
      label={data.label as string}
      type="VALIDATE"
      borderColor="#a855f7"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        Rules: <span className="text-purple-400">{count}</span>
      </div>
    </BaseNode>
  );
}
```

6. Create `fix-flow-ui/src/canvas/nodes/DecisionNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function DecisionNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="DECISION"
      borderColor="#f97316"
      selected={selected}
      status={data.status as string}
      shape="diamond"
    />
  );
}
```

7. Create `fix-flow-ui/src/canvas/nodes/EndPassNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function EndPassNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="END_PASS"
      borderColor="#22c55e"
      selected={selected}
      status={data.status as string}
      filled
      handles={{ target: true, source: false }}
    />
  );
}
```

8. Create `fix-flow-ui/src/canvas/nodes/EndFailNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function EndFailNode({ data, selected }: NodeProps) {
  return (
    <BaseNode
      label={data.label as string}
      type="END_FAIL"
      borderColor="#ef4444"
      selected={selected}
      status={data.status as string}
      filled
      handles={{ target: true, source: false }}
    />
  );
}
```

9. Create `fix-flow-ui/src/canvas/nodes/RetryNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function RetryNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { maxAttempts?: number }) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="RETRY"
      borderColor="#06b6d4"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        Max attempts: <span className="text-cyan-400">{cfg.maxAttempts ?? '?'}</span>
      </div>
    </BaseNode>
  );
}
```

10. Create `fix-flow-ui/src/canvas/nodes/WaitNode.tsx`:
```typescript
import { NodeProps } from '@xyflow/react';
import { BaseNode } from './BaseNode';

export function WaitNode({ data, selected }: NodeProps) {
  const cfg = (data.config as { value?: number; unit?: string }) ?? {};
  return (
    <BaseNode
      label={data.label as string}
      type="WAIT"
      borderColor="#6b7280"
      selected={selected}
      status={data.status as string}
    >
      <div className="text-gray-400">
        {cfg.value ?? '?'} {cfg.unit ?? ''}
      </div>
    </BaseNode>
  );
}
```

11. Replace `fix-flow-ui/src/canvas/nodes/nodeTypes.ts`:
```typescript
import { NodeTypes } from '@xyflow/react';
import { StartNode } from './StartNode';
import { SendFIXNode } from './SendFIXNode';
import { ExpectFIXNode } from './ExpectFIXNode';
import { ValidateNode } from './ValidateNode';
import { DecisionNode } from './DecisionNode';
import { EndPassNode } from './EndPassNode';
import { EndFailNode } from './EndFailNode';
import { RetryNode } from './RetryNode';
import { WaitNode } from './WaitNode';

export const nodeTypes: NodeTypes = {
  START: StartNode,
  SEND_FIX: SendFIXNode,
  EXPECT_FIX: ExpectFIXNode,
  VALIDATE: ValidateNode,
  DECISION: DecisionNode,
  BRANCH: DecisionNode,
  END_PASS: EndPassNode,
  END_FAIL: EndFailNode,
  RETRY: RetryNode,
  LOOP: RetryNode,
  WAIT: WaitNode,
  DELAY: WaitNode,
  TIMEOUT: WaitNode,
};
```

12. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no errors.

13. Commit:
```bash
git add fix-flow-ui/src/canvas/nodes
git commit -m "feat(ui): add custom ReactFlow node components for all node types"
```

---

## Phase 11: Left Panel + Runtime Panel (Tasks 39-41)

### Task 39: NodePalette + ScenarioList

**Files:**
- Create: `fix-flow-ui/src/panels/left/NodePalette.tsx`
- Create: `fix-flow-ui/src/panels/left/ScenarioList.tsx`
- Modify: `fix-flow-ui/src/panels/left/LeftPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/left/NodePalette.tsx`:
```typescript
import { NodeType } from '../../types';
import { colors } from '../../theme/colors';

interface PaletteItem {
  type: NodeType;
  label: string;
}

const GROUPS: Array<{ title: string; items: PaletteItem[] }> = [
  {
    title: 'Messages',
    items: [
      { type: 'SEND_FIX', label: 'Send FIX' },
      { type: 'EXPECT_FIX', label: 'Expect FIX' },
      { type: 'VALIDATE', label: 'Validate' },
    ],
  },
  {
    title: 'Flow Control',
    items: [
      { type: 'DECISION', label: 'Decision' },
      { type: 'RETRY', label: 'Retry' },
      { type: 'LOOP', label: 'Loop' },
      { type: 'WAIT', label: 'Wait' },
      { type: 'DELAY', label: 'Delay' },
    ],
  },
  {
    title: 'Terminals',
    items: [
      { type: 'START', label: 'Start' },
      { type: 'END_PASS', label: 'End Pass' },
      { type: 'END_FAIL', label: 'End Fail' },
    ],
  },
];

export function NodePalette() {
  const onDragStart = (evt: React.DragEvent, type: NodeType) => {
    evt.dataTransfer.setData('application/fix-flow-node-type', type);
    evt.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="p-2 overflow-y-auto">
      <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">Palette</div>
      {GROUPS.map((g) => (
        <div key={g.title} className="mb-3">
          <div className="text-[10px] uppercase text-gray-400 mb-1">{g.title}</div>
          <div className="flex flex-col gap-1">
            {g.items.map((it) => (
              <div
                key={it.type}
                draggable
                onDragStart={(e) => onDragStart(e, it.type)}
                className="px-2 py-1 rounded cursor-grab bg-[#0f1117] border text-xs hover:bg-[#22252f]"
                style={{ borderColor: colors.node[it.type] }}
              >
                {it.label}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/panels/left/ScenarioList.tsx`:
```typescript
import { useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getScenarios, createScenario } from '../../api/scenarios';
import { useScenarioStore } from '../../store/scenarioStore';
import { parseFromYaml } from '../../lib/scenarioSerializer';
import { Scenario } from '../../types';

const EMPTY_YAML = `id: new-scenario
name: New Scenario
description: ''
version: '1.0'
sessionRef: default
nodes: []
edges: []
`;

export function ScenarioList() {
  const queryClient = useQueryClient();
  const setScenarios = useScenarioStore((s) => s.setScenarios);
  const setActiveScenario = useScenarioStore((s) => s.setActiveScenario);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const activeScenario = useScenarioStore((s) => s.activeScenario);

  const { data } = useQuery({
    queryKey: ['scenarios'],
    queryFn: getScenarios,
  });

  useEffect(() => {
    if (data) setScenarios(data);
  }, [data, setScenarios]);

  const createMutation = useMutation({
    mutationFn: () =>
      createScenario({
        name: 'New Scenario',
        description: '',
        sessionRef: 'default',
        yamlDsl: EMPTY_YAML,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  });

  const onSelect = (s: Scenario) => {
    setActiveScenario(s);
    try {
      const parsed = parseFromYaml(s.yamlDsl);
      setNodes(parsed.nodes);
      setEdges(parsed.edges);
    } catch {
      setNodes([]);
      setEdges([]);
    }
  };

  return (
    <div className="p-2 overflow-y-auto border-t border-[#2a2d3a]">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs uppercase tracking-wider text-gray-500">Scenarios</div>
        <button
          className="text-xs px-2 py-0.5 rounded bg-blue-600 hover:bg-blue-500"
          onClick={() => createMutation.mutate()}
        >
          + New
        </button>
      </div>
      <div className="flex flex-col gap-1">
        {(data ?? []).map((s) => (
          <button
            key={s.id}
            className={`text-left px-2 py-1 rounded text-xs ${
              activeScenario?.id === s.id
                ? 'bg-blue-700 text-white'
                : 'bg-[#0f1117] hover:bg-[#22252f] text-gray-200'
            }`}
            onClick={() => onSelect(s)}
          >
            <div className="font-medium truncate">{s.name}</div>
            <div className="text-[10px] opacity-70">v{s.version}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
```

3. Replace `fix-flow-ui/src/panels/left/LeftPanel.tsx`:
```typescript
import { NodePalette } from './NodePalette';
import { ScenarioList } from './ScenarioList';

export default function LeftPanel() {
  return (
    <div className="bg-[#1a1d27] border-r border-[#2a2d3a] flex flex-col">
      <div className="flex-1 min-h-0 overflow-hidden">
        <NodePalette />
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <ScenarioList />
      </div>
    </div>
  );
}
```

4. Run:
```bash
cd fix-flow-ui && npm run dev
```
Drag node from palette to canvas — node appears.

5. Commit:
```bash
git add fix-flow-ui/src/panels/left
git commit -m "feat(ui): add NodePalette drag source and ScenarioList loader"
```

---

### Task 40: RuntimePanel + EventLog + FIXMessageLog

**Files:**
- Create: `fix-flow-ui/src/panels/bottom/EventLog.tsx`
- Create: `fix-flow-ui/src/panels/bottom/FIXMessageLog.tsx`
- Create: `fix-flow-ui/src/panels/bottom/ValidationErrors.tsx`
- Create: `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx`
- Modify: `fix-flow-ui/src/panels/bottom/BottomPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/bottom/EventLog.tsx`:
```typescript
import { useEffect, useRef } from 'react';
import { useExecutionStore } from '../../store/executionStore';

const TYPE_COLORS: Record<string, string> = {
  NODE_STARTED: 'bg-blue-700',
  NODE_COMPLETED: 'bg-green-700',
  NODE_FAILED: 'bg-red-700',
  SCENARIO_STARTED: 'bg-blue-700',
  SCENARIO_PASSED: 'bg-green-700',
  SCENARIO_FAILED: 'bg-red-700',
  VALIDATION_FAILED: 'bg-red-700',
  MESSAGE_SENT: 'bg-cyan-700',
  MESSAGE_RECEIVED: 'bg-purple-700',
  TIMEOUT: 'bg-amber-700',
};

export function EventLog() {
  const events = useExecutionStore((s) => s.events);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [events]);

  return (
    <div ref={ref} className="h-full overflow-y-auto px-2 py-1 font-mono text-[11px]">
      {events.length === 0 && <div className="text-gray-500 italic">No events</div>}
      {events.map((e) => (
        <div key={e.id} className="flex gap-2 py-0.5 border-b border-[#2a2d3a]">
          <div className="text-gray-500">{new Date(e.timestamp).toLocaleTimeString()}</div>
          <div
            className={`px-1.5 rounded text-[10px] uppercase ${
              TYPE_COLORS[e.type] ?? 'bg-gray-700'
            }`}
          >
            {e.type}
          </div>
          <div className="text-blue-300">{e.nodeId ?? '-'}</div>
          <div className="text-gray-300 truncate">{e.detail ?? ''}</div>
        </div>
      ))}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/panels/bottom/FIXMessageLog.tsx`:
```typescript
import { useEffect, useMemo, useRef, useState } from 'react';
import { useExecutionStore } from '../../store/executionStore';

export function FIXMessageLog() {
  const messages = useExecutionStore((s) => s.messages);
  const [hideHeartbeats, setHideHeartbeats] = useState(true);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const ref = useRef<HTMLDivElement>(null);

  const visible = useMemo(() => {
    if (!hideHeartbeats) return messages;
    return messages.filter((m) => m.fields[35] !== '0' && m.fields[35] !== '1');
  }, [messages, hideHeartbeats]);

  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [visible]);

  return (
    <div className="h-full flex flex-col">
      <div className="px-2 py-1 border-b border-[#2a2d3a] flex items-center gap-2">
        <label className="text-xs flex items-center gap-1">
          <input
            type="checkbox"
            checked={hideHeartbeats}
            onChange={(e) => setHideHeartbeats(e.target.checked)}
          />
          Hide Heartbeats
        </label>
        <div className="ml-auto text-[10px] text-gray-500">
          {visible.length} / {messages.length} messages
        </div>
      </div>
      <div ref={ref} className="flex-1 overflow-y-auto px-2 py-1 font-mono text-[11px]">
        {visible.length === 0 && <div className="text-gray-500 italic">No messages</div>}
        {visible.map((m) => {
          const isExp = expanded[m.id];
          const display = isExp ? m.rawFix : m.rawFix.slice(0, 80);
          return (
            <div
              key={m.id}
              className="py-0.5 border-b border-[#2a2d3a] cursor-pointer"
              onClick={() => setExpanded((p) => ({ ...p, [m.id]: !p[m.id] }))}
            >
              <div className="flex gap-2">
                <div className="text-gray-500">
                  {new Date(m.receivedAt).toLocaleTimeString()}
                </div>
                <div
                  className={`px-1.5 rounded text-[10px] ${
                    m.direction === 'INBOUND' ? 'bg-green-700' : 'bg-blue-700'
                  }`}
                >
                  {m.direction === 'INBOUND' ? 'IN' : 'OUT'}
                </div>
                <div className="text-amber-300">35={m.fields[35] ?? '?'}</div>
                <div className="text-gray-300 truncate">{display}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
```

3. Create `fix-flow-ui/src/panels/bottom/ValidationErrors.tsx`:
```typescript
import { useMemo } from 'react';
import { useExecutionStore } from '../../store/executionStore';
import { ValidationError } from '../../types';

export function ValidationErrors() {
  const events = useExecutionStore((s) => s.events);

  const errors: ValidationError[] = useMemo(() => {
    const collected: ValidationError[] = [];
    for (const e of events) {
      if (e.type !== 'VALIDATION_FAILED' || !e.detail) continue;
      try {
        const parsed = JSON.parse(e.detail) as ValidationError | ValidationError[];
        if (Array.isArray(parsed)) collected.push(...parsed);
        else collected.push(parsed);
      } catch {
        collected.push({ tag: 0, rule: 'UNKNOWN', expected: '', actual: e.detail });
      }
    }
    return collected;
  }, [events]);

  return (
    <div className="h-full overflow-y-auto px-2 py-1">
      {errors.length === 0 && (
        <div className="text-gray-500 italic text-xs">No validation errors</div>
      )}
      <table className="w-full text-xs">
        <thead className="text-left text-gray-500">
          <tr>
            <th className="py-1 pr-2">Tag</th>
            <th className="py-1 pr-2">Rule</th>
            <th className="py-1 pr-2">Expected</th>
            <th className="py-1 pr-2">Actual</th>
            <th className="py-1">Message</th>
          </tr>
        </thead>
        <tbody>
          {errors.map((e, i) => (
            <tr key={i} className="border-t border-[#2a2d3a]">
              <td className="py-1 pr-2 text-amber-300">{e.tag}</td>
              <td className="py-1 pr-2 text-blue-300">{e.rule}</td>
              <td className="py-1 pr-2 text-green-300">{e.expected}</td>
              <td className="py-1 pr-2 text-red-300">{e.actual}</td>
              <td className="py-1 text-gray-300">{e.message ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

4. Create `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx`:
```typescript
import { useMemo } from 'react';
import { useExecutionStore } from '../../store/executionStore';

export function ExecutionStats() {
  const events = useExecutionStore((s) => s.events);
  const startedAt = useExecutionStore((s) => s.startedAt);
  const endedAt = useExecutionStore((s) => s.endedAt);
  const executionStatus = useExecutionStore((s) => s.executionStatus);

  const stats = useMemo(() => {
    let nodesPassed = 0;
    let nodesFailed = 0;
    const nodeDurations: number[] = [];
    const nodeStartTimes: Record<string, number> = {};
    for (const e of events) {
      if (e.type === 'NODE_STARTED' && e.nodeId) {
        nodeStartTimes[e.nodeId] = new Date(e.timestamp).getTime();
      }
      if (e.type === 'NODE_COMPLETED' && e.nodeId) {
        nodesPassed += 1;
        if (nodeStartTimes[e.nodeId]) {
          nodeDurations.push(new Date(e.timestamp).getTime() - nodeStartTimes[e.nodeId]);
        }
      }
      if (e.type === 'NODE_FAILED' && e.nodeId) {
        nodesFailed += 1;
      }
    }
    const avgNodeMs =
      nodeDurations.length > 0
        ? Math.round(nodeDurations.reduce((a, b) => a + b, 0) / nodeDurations.length)
        : 0;
    const duration =
      startedAt && endedAt
        ? new Date(endedAt).getTime() - new Date(startedAt).getTime()
        : startedAt
          ? Date.now() - new Date(startedAt).getTime()
          : 0;
    return { nodesPassed, nodesFailed, avgNodeMs, duration };
  }, [events, startedAt, endedAt]);

  return (
    <div className="h-full overflow-y-auto px-3 py-2 grid grid-cols-2 md:grid-cols-4 gap-2 text-xs">
      <Stat label="Status" value={executionStatus} />
      <Stat label="Nodes passed" value={String(stats.nodesPassed)} />
      <Stat label="Nodes failed" value={String(stats.nodesFailed)} />
      <Stat label="Avg node time" value={`${stats.avgNodeMs} ms`} />
      <Stat label="Total duration" value={`${stats.duration} ms`} />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-[#0f1117] border border-[#2a2d3a] rounded p-2">
      <div className="text-[10px] uppercase text-gray-500">{label}</div>
      <div className="text-base text-gray-100 mt-1">{value}</div>
    </div>
  );
}
```

5. Replace `fix-flow-ui/src/panels/bottom/BottomPanel.tsx`:
```typescript
import { useState } from 'react';
import { EventLog } from './EventLog';
import { FIXMessageLog } from './FIXMessageLog';
import { ValidationErrors } from './ValidationErrors';
import { ExecutionStats } from './ExecutionStats';

type Tab = 'events' | 'messages' | 'validation' | 'stats';

const TABS: Array<{ id: Tab; label: string }> = [
  { id: 'events', label: 'Events' },
  { id: 'messages', label: 'FIX Messages' },
  { id: 'validation', label: 'Validation Errors' },
  { id: 'stats', label: 'Statistics' },
];

export default function BottomPanel() {
  const [tab, setTab] = useState<Tab>('events');
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div
      className="bg-[#1a1d27] border-t border-[#2a2d3a] flex flex-col"
      style={{ height: collapsed ? 32 : 240 }}
    >
      <div className="h-8 flex items-center border-b border-[#2a2d3a]">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => {
              setTab(t.id);
              setCollapsed(false);
            }}
            className={`px-3 h-full text-xs ${
              tab === t.id
                ? 'text-blue-400 border-b-2 border-blue-400'
                : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            {t.label}
          </button>
        ))}
        <button
          className="ml-auto px-3 text-xs text-gray-400 hover:text-gray-200"
          onClick={() => setCollapsed((c) => !c)}
        >
          {collapsed ? 'Expand' : 'Collapse'}
        </button>
      </div>
      {!collapsed && (
        <div className="flex-1 min-h-0">
          {tab === 'events' && <EventLog />}
          {tab === 'messages' && <FIXMessageLog />}
          {tab === 'validation' && <ValidationErrors />}
          {tab === 'stats' && <ExecutionStats />}
        </div>
      )}
    </div>
  );
}
```

6. Run:
```bash
cd fix-flow-ui && npm run dev
```
Bottom panel opens/collapses, tabs switch.

7. Commit:
```bash
git add fix-flow-ui/src/panels/bottom
git commit -m "feat(ui): add bottom runtime panel with events, messages, validation, stats tabs"
```

---

### Task 41: Live execution wiring (WS → node highlighting)

**Files:**
- Create: `fix-flow-ui/src/hooks/useExecutionSubscription.ts`
- Modify: `fix-flow-ui/src/App.tsx`
- Modify: `fix-flow-ui/src/components/TopBar.tsx`

**Steps:**

1. Create `fix-flow-ui/src/hooks/useExecutionSubscription.ts`:
```typescript
import { useEffect } from 'react';
import { useExecutionStore } from '../store/executionStore';
import { wsClient } from '../app/wsClient';
import { ExecutionEvent, FIXMessage } from '../types';

export function useExecutionSubscription(executionId: string | null): void {
  const addEvent = useExecutionStore((s) => s.addEvent);
  const addMessage = useExecutionStore((s) => s.addMessage);
  const setNodeStatus = useExecutionStore((s) => s.setNodeStatus);
  const updateStatus = useExecutionStore((s) => s.updateStatus);
  const setStartedAt = useExecutionStore((s) => s.setStartedAt);
  const setEndedAt = useExecutionStore((s) => s.setEndedAt);

  useEffect(() => {
    if (!executionId) return;
    let disposer: (() => void) | null = null;
    let cancelled = false;

    const handleEvent = (event: ExecutionEvent) => {
      addEvent(event);
      if (event.type === 'SCENARIO_STARTED') {
        setStartedAt(event.timestamp);
        updateStatus('RUNNING');
      }
      if (event.type === 'NODE_STARTED' && event.nodeId) {
        setNodeStatus(event.nodeId, 'running');
      }
      if (event.type === 'NODE_COMPLETED' && event.nodeId) {
        setNodeStatus(event.nodeId, 'passed');
      }
      if (event.type === 'NODE_FAILED' && event.nodeId) {
        setNodeStatus(event.nodeId, 'failed');
      }
      if (event.type === 'SCENARIO_PASSED') {
        updateStatus('PASSED');
        setEndedAt(event.timestamp);
      }
      if (event.type === 'SCENARIO_FAILED') {
        updateStatus('FAILED');
        setEndedAt(event.timestamp);
      }
      if (event.type === 'SCENARIO_STOPPED') {
        updateStatus('STOPPED');
        setEndedAt(event.timestamp);
      }
    };

    const handleMessage = (msg: FIXMessage) => {
      addMessage(msg);
    };

    wsClient
      .subscribeExecution(executionId, handleEvent, handleMessage)
      .then((d) => {
        if (cancelled) d();
        else disposer = d;
      })
      .catch((err) => console.error('WS subscribe failed', err));

    return () => {
      cancelled = true;
      if (disposer) disposer();
    };
  }, [executionId, addEvent, addMessage, setNodeStatus, updateStatus, setStartedAt, setEndedAt]);
}
```

2. Modify `fix-flow-ui/src/App.tsx` to wire the subscription:
```typescript
import TopBar from './components/TopBar';
import FlowCanvas from './canvas/FlowCanvas';
import LeftPanel from './panels/left/LeftPanel';
import RightPanel from './panels/right/RightPanel';
import BottomPanel from './panels/bottom/BottomPanel';
import { useExecutionStore } from './store/executionStore';
import { useExecutionSubscription } from './hooks/useExecutionSubscription';

export default function App() {
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  useExecutionSubscription(activeExecutionId);

  return (
    <div className="h-screen flex flex-col bg-[#0f1117] text-gray-100">
      <TopBar />
      <div
        className="flex-1 overflow-hidden"
        style={{
          display: 'grid',
          gridTemplateColumns: '240px 1fr 320px',
          gridTemplateRows: '1fr',
        }}
      >
        <LeftPanel />
        <FlowCanvas />
        <RightPanel />
      </div>
      <BottomPanel />
    </div>
  );
}
```

3. Update `fix-flow-ui/src/components/TopBar.tsx` Run mutation to also reset execution state before subscribing:
```typescript
// Replace the runMutation onSuccess body with:
onSuccess: (exec) => {
  useExecutionStore.getState().reset();
  setActiveExecution(exec.id);
  updateStatus('RUNNING');
},
```
The full Run handler now:
```typescript
const runMutation = useMutation({
  mutationFn: async () => {
    if (!activeScenario || !activeSession) throw new Error('No scenario or session');
    return executeScenario(activeScenario.id, activeSession.id);
  },
  onSuccess: (exec) => {
    useExecutionStore.getState().reset();
    setActiveExecution(exec.id);
    updateStatus('RUNNING');
  },
});
```

4. Run:
```bash
cd fix-flow-ui && npm run dev
```
Click Run on a scenario — nodes turn amber while running, green when done.

5. Commit:
```bash
git add fix-flow-ui/src
git commit -m "feat(ui): wire WebSocket execution events to live node status highlighting"
```

---

## Phase 12: Right Panel Config Forms (Tasks 42-45)

### Task 42: PropertiesPanel + SendFIXConfig

**Files:**
- Create: `fix-flow-ui/src/panels/right/PropertiesPanel.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/RightPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/right/NodeConfig/TimeoutConfig.tsx`:
```typescript
import { TimeUnit, TimeoutAction, TimeoutConfig as Cfg } from '../../../types';

const UNITS: TimeUnit[] = ['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS'];
const ACTIONS: TimeoutAction[] = ['FAIL', 'RETRY', 'CONTINUE', 'JUMP'];

interface Props {
  value: Cfg | undefined;
  onChange: (next: Cfg | undefined) => void;
}

export function TimeoutConfig({ value, onChange }: Props) {
  const cfg: Cfg = value ?? { value: 30, unit: 'SECONDS', onTimeout: 'FAIL' };

  const update = (patch: Partial<Cfg>) => onChange({ ...cfg, ...patch });

  return (
    <div className="border border-[#2a2d3a] rounded p-2 mt-2">
      <div className="text-[10px] uppercase text-gray-500 mb-1">Timeout</div>
      <div className="flex gap-1">
        <input
          type="number"
          className="w-20 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={cfg.value}
          onChange={(e) => update({ value: Number(e.target.value) })}
        />
        <select
          className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={cfg.unit}
          onChange={(e) => update({ unit: e.target.value as TimeUnit })}
        >
          {UNITS.map((u) => (
            <option key={u}>{u}</option>
          ))}
        </select>
      </div>
      <div className="mt-1">
        <label className="text-[10px] text-gray-500">On Timeout</label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={cfg.onTimeout}
          onChange={(e) => update({ onTimeout: e.target.value as TimeoutAction })}
        >
          {ACTIONS.map((a) => (
            <option key={a}>{a}</option>
          ))}
        </select>
      </div>
      {cfg.onTimeout === 'JUMP' && (
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">Jump To Node</label>
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
            value={cfg.jumpTo ?? ''}
            onChange={(e) => update({ jumpTo: e.target.value })}
          />
        </div>
      )}
    </div>
  );
}
```

2. Create `fix-flow-ui/src/panels/right/NodeConfig/SendFIXConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface FieldRow {
  tag: number;
  value: string;
}

interface SendCfg {
  msgType?: string;
  fields?: FieldRow[];
}

interface Props {
  node: ScenarioNode;
}

export function SendFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as SendCfg) ?? {};
  const fields = cfg.fields ?? [];

  const patchConfig = (patch: Partial<SendCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateField = (i: number, patch: Partial<FieldRow>) => {
    const next = fields.map((f, idx) => (idx === i ? { ...f, ...patch } : f));
    patchConfig({ fields: next });
  };

  const addField = () => patchConfig({ fields: [...fields, { tag: 0, value: '' }] });
  const removeField = (i: number) =>
    patchConfig({ fields: fields.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">MsgType (tag 35)</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''}
          onChange={(e) => patchConfig({ msgType: e.target.value })}
        />
      </div>
      <div>
        <div className="flex items-center justify-between">
          <label className="text-[10px] text-gray-500">Fields</label>
          <button
            className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
            onClick={addField}
          >
            + Field
          </button>
        </div>
        <table className="w-full mt-1">
          <thead className="text-[10px] text-gray-500">
            <tr>
              <th className="text-left">Tag</th>
              <th className="text-left">Value</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {fields.map((f, i) => (
              <tr key={i}>
                <td className="pr-1">
                  <input
                    type="number"
                    className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.tag}
                    onChange={(e) =>
                      updateField(i, { tag: Number(e.target.value) })
                    }
                  />
                </td>
                <td className="pr-1">
                  <input
                    type="text"
                    className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.value}
                    onChange={(e) => updateField(i, { value: e.target.value })}
                  />
                </td>
                <td>
                  <button
                    className="text-red-400 hover:text-red-300 text-xs"
                    onClick={() => removeField(i)}
                  >
                    x
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <TimeoutConfig
        value={node.timeout}
        onChange={(next) => updateNode(node.id, { timeout: next })}
      />
    </div>
  );
}
```

3. Create `fix-flow-ui/src/panels/right/PropertiesPanel.tsx`:
```typescript
import { useScenarioStore } from '../../store/scenarioStore';
import { SendFIXConfig } from './NodeConfig/SendFIXConfig';
import { ExpectFIXConfig } from './NodeConfig/ExpectFIXConfig';
import { ValidateConfig } from './NodeConfig/ValidateConfig';
import { RetryConfig } from './NodeConfig/RetryConfig';

export function PropertiesPanel() {
  const selectedNodeId = useScenarioStore((s) => s.selectedNodeId);
  const node = useScenarioStore((s) =>
    selectedNodeId ? s.nodes.find((n) => n.id === selectedNodeId) ?? null : null,
  );

  return (
    <div className="p-2 overflow-y-auto border-b border-[#2a2d3a]">
      <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">Properties</div>
      {!node && <div className="text-xs text-gray-500 italic">Select a node to configure</div>}
      {node?.type === 'SEND_FIX' && <SendFIXConfig node={node} />}
      {node?.type === 'EXPECT_FIX' && <ExpectFIXConfig node={node} />}
      {node?.type === 'VALIDATE' && <ValidateConfig node={node} />}
      {(node?.type === 'RETRY' || node?.type === 'LOOP') && <RetryConfig node={node} />}
      {node &&
        !['SEND_FIX', 'EXPECT_FIX', 'VALIDATE', 'RETRY', 'LOOP'].includes(node.type) && (
          <div className="text-xs text-gray-500 italic">
            No configuration available for {node.type}
          </div>
        )}
    </div>
  );
}
```

4. Add stub files (overridden in Task 43) so PropertiesPanel compiles. Create `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';

export function ExpectFIXConfig({ node }: { node: ScenarioNode }) {
  return <div className="text-xs text-gray-500">ExpectFIX config — see Task 43 ({node.id})</div>;
}
```

5. Create `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';

export function ValidateConfig({ node }: { node: ScenarioNode }) {
  return <div className="text-xs text-gray-500">Validate config — see Task 43 ({node.id})</div>;
}
```

6. Create `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';

export function RetryConfig({ node }: { node: ScenarioNode }) {
  return <div className="text-xs text-gray-500">Retry config — see Task 43 ({node.id})</div>;
}
```

7. Replace `fix-flow-ui/src/panels/right/RightPanel.tsx`:
```typescript
import { PropertiesPanel } from './PropertiesPanel';

export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] flex flex-col">
      <div className="flex-1 min-h-0 overflow-hidden">
        <PropertiesPanel />
      </div>
    </div>
  );
}
```

8. Run:
```bash
cd fix-flow-ui && npm run dev
```
Click a SEND_FIX node — right panel shows config form, edits persist.

9. Commit:
```bash
git add fix-flow-ui/src/panels/right
git commit -m "feat(ui): add PropertiesPanel with SendFIX config and timeout editor"
```

---

### Task 43: ExpectFIXConfig + ValidateConfig + DateRulesEditor

**Files:**
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`
- Create: `fix-flow-ui/src/panels/right/NodeConfig/DateRulesEditor.tsx`
- Modify: `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx`

**Steps:**

1. Replace `fix-flow-ui/src/panels/right/NodeConfig/ExpectFIXConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface CorrelationCfg {
  sourceTag?: number;
  fromNode?: string;
  targetTag?: number;
}

interface ExpectCfg {
  msgType?: string;
  correlation?: CorrelationCfg;
}

interface Props {
  node: ScenarioNode;
}

export function ExpectFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as ExpectCfg) ?? {};
  const corr = cfg.correlation ?? {};

  const patchConfig = (patch: Partial<ExpectCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });
  const patchCorr = (patch: Partial<CorrelationCfg>) =>
    patchConfig({ correlation: { ...corr, ...patch } });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">MsgType (tag 35)</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''}
          onChange={(e) => patchConfig({ msgType: e.target.value })}
        />
      </div>
      <div className="border border-[#2a2d3a] rounded p-2">
        <div className="text-[10px] uppercase text-gray-500 mb-1">Correlation</div>
        <div>
          <label className="text-[10px] text-gray-500">Source Tag (in received)</label>
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.sourceTag ?? 0}
            onChange={(e) => patchCorr({ sourceTag: Number(e.target.value) })}
          />
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">From Node</label>
          <select
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.fromNode ?? ''}
            onChange={(e) => patchCorr({ fromNode: e.target.value })}
          >
            <option value="">-- select --</option>
            {allNodes
              .filter((n) => n.id !== node.id)
              .map((n) => (
                <option key={n.id} value={n.id}>
                  {n.name} ({n.type})
                </option>
              ))}
          </select>
        </div>
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">Target Tag (in source node)</label>
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            value={corr.targetTag ?? 0}
            onChange={(e) => patchCorr({ targetTag: Number(e.target.value) })}
          />
        </div>
      </div>
      <TimeoutConfig
        value={node.timeout}
        onChange={(next) => updateNode(node.id, { timeout: next })}
      />
    </div>
  );
}
```

2. Replace `fix-flow-ui/src/panels/right/NodeConfig/ValidateConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { DateRulesEditor, DateRule } from './DateRulesEditor';

type RuleKind =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'ENUM'
  | 'REGEX'
  | 'NUMERIC_MIN'
  | 'NUMERIC_MAX'
  | 'FIELD_PRESENT'
  | 'FIELD_ABSENT'
  | 'DATE_RULE';

interface ValidationRule {
  tag: number;
  rule: RuleKind;
  value?: string;
  values?: string[];
  pattern?: string;
  numericValue?: number;
  ref?: string;
  dateRuleId?: string;
}

interface ValidateCfg {
  strictMode?: boolean;
  rules?: ValidationRule[];
  dateRules?: DateRule[];
}

interface Props {
  node: ScenarioNode;
}

const RULES: RuleKind[] = [
  'EQUALS',
  'NOT_EQUALS',
  'ENUM',
  'REGEX',
  'NUMERIC_MIN',
  'NUMERIC_MAX',
  'FIELD_PRESENT',
  'FIELD_ABSENT',
  'DATE_RULE',
];

export function ValidateConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as ValidateCfg) ?? {};
  const rules = cfg.rules ?? [];
  const dateRules = cfg.dateRules ?? [];

  const patchConfig = (patch: Partial<ValidateCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateRule = (i: number, patch: Partial<ValidationRule>) => {
    const next = rules.map((r, idx) => (idx === i ? { ...r, ...patch } : r));
    patchConfig({ rules: next });
  };

  const addRule = () =>
    patchConfig({ rules: [...rules, { tag: 0, rule: 'EQUALS', value: '' }] });
  const removeRule = (i: number) =>
    patchConfig({ rules: rules.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={cfg.strictMode ?? false}
          onChange={(e) => patchConfig({ strictMode: e.target.checked })}
        />
        Strict Mode
      </label>
      <div className="flex items-center justify-between">
        <div className="text-[10px] uppercase text-gray-500">Rules</div>
        <button
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={addRule}
        >
          + Rule
        </button>
      </div>
      <div className="space-y-1">
        {rules.map((r, i) => (
          <div key={i} className="border border-[#2a2d3a] rounded p-2">
            <div className="flex gap-1 items-center">
              <input
                type="number"
                className="w-16 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                value={r.tag}
                onChange={(e) => updateRule(i, { tag: Number(e.target.value) })}
                placeholder="tag"
              />
              <select
                className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                value={r.rule}
                onChange={(e) => updateRule(i, { rule: e.target.value as RuleKind })}
              >
                {RULES.map((rk) => (
                  <option key={rk}>{rk}</option>
                ))}
              </select>
              <button
                className="text-red-400 hover:text-red-300"
                onClick={() => removeRule(i)}
              >
                x
              </button>
            </div>
            {(r.rule === 'EQUALS' || r.rule === 'NOT_EQUALS') && (
              <input
                type="text"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.value ?? ''}
                onChange={(e) => updateRule(i, { value: e.target.value })}
                placeholder="value"
              />
            )}
            {r.rule === 'ENUM' && (
              <input
                type="text"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={(r.values ?? []).join(',')}
                onChange={(e) =>
                  updateRule(i, {
                    values: e.target.value.split(',').map((s) => s.trim()),
                  })
                }
                placeholder="comma,separated,values"
              />
            )}
            {r.rule === 'REGEX' && (
              <input
                type="text"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.pattern ?? ''}
                onChange={(e) => updateRule(i, { pattern: e.target.value })}
                placeholder="regex pattern"
              />
            )}
            {(r.rule === 'NUMERIC_MIN' || r.rule === 'NUMERIC_MAX') && (
              <input
                type="number"
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.numericValue ?? 0}
                onChange={(e) => updateRule(i, { numericValue: Number(e.target.value) })}
                placeholder="numeric value"
              />
            )}
            {r.rule === 'DATE_RULE' && (
              <select
                className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.dateRuleId ?? ''}
                onChange={(e) => updateRule(i, { dateRuleId: e.target.value })}
              >
                <option value="">-- select date rule --</option>
                {dateRules.map((dr) => (
                  <option key={dr.ruleId} value={dr.ruleId}>
                    {dr.ruleId}
                  </option>
                ))}
              </select>
            )}
            <input
              type="text"
              className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
              value={r.ref ?? ''}
              onChange={(e) => updateRule(i, { ref: e.target.value })}
              placeholder="cross-node ref (optional)"
            />
          </div>
        ))}
      </div>
      <DateRulesEditor
        value={dateRules}
        onChange={(next) => patchConfig({ dateRules: next })}
      />
    </div>
  );
}
```

3. Create `fix-flow-ui/src/panels/right/NodeConfig/DateRulesEditor.tsx`:
```typescript
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeUnit } from '../../../types';

export type DateRuleType = 'CURRENT_TIMESTAMP' | 'FIELD_OFFSET';

export interface DateRule {
  ruleId: string;
  type: DateRuleType;
  sourceNode?: string;
  sourceTag?: number;
  offsetValue?: number;
  offsetUnit?: TimeUnit;
  toleranceValue: number;
  toleranceUnit: TimeUnit;
}

const UNITS: TimeUnit[] = ['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS'];

interface Props {
  value: DateRule[];
  onChange: (next: DateRule[]) => void;
}

export function DateRulesEditor({ value, onChange }: Props) {
  const allNodes = useScenarioStore((s) => s.nodes);

  const add = () =>
    onChange([
      ...value,
      {
        ruleId: `dr-${Date.now()}`,
        type: 'CURRENT_TIMESTAMP',
        toleranceValue: 1,
        toleranceUnit: 'SECONDS',
      },
    ]);
  const remove = (i: number) => onChange(value.filter((_, idx) => idx !== i));
  const update = (i: number, patch: Partial<DateRule>) =>
    onChange(value.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  return (
    <div className="border border-[#2a2d3a] rounded p-2 mt-2">
      <div className="flex items-center justify-between mb-1">
        <div className="text-[10px] uppercase text-gray-500">Date Rules</div>
        <button
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={add}
        >
          + Date Rule
        </button>
      </div>
      <div className="space-y-2">
        {value.map((r, i) => (
          <div key={i} className="border border-[#2a2d3a] rounded p-2 space-y-1">
            <div className="flex gap-1">
              <input
                type="text"
                className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.ruleId}
                onChange={(e) => update(i, { ruleId: e.target.value })}
                placeholder="ruleId"
              />
              <button
                className="text-red-400 hover:text-red-300"
                onClick={() => remove(i)}
              >
                x
              </button>
            </div>
            <select
              className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
              value={r.type}
              onChange={(e) => update(i, { type: e.target.value as DateRuleType })}
            >
              <option value="CURRENT_TIMESTAMP">CURRENT_TIMESTAMP</option>
              <option value="FIELD_OFFSET">FIELD_OFFSET</option>
            </select>
            {r.type === 'FIELD_OFFSET' && (
              <>
                <select
                  className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                  value={r.sourceNode ?? ''}
                  onChange={(e) => update(i, { sourceNode: e.target.value })}
                >
                  <option value="">-- source node --</option>
                  {allNodes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.name}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                  value={r.sourceTag ?? 0}
                  onChange={(e) => update(i, { sourceTag: Number(e.target.value) })}
                  placeholder="source tag"
                />
                <div className="flex gap-1">
                  <input
                    type="number"
                    className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                    value={r.offsetValue ?? 0}
                    onChange={(e) =>
                      update(i, { offsetValue: Number(e.target.value) })
                    }
                    placeholder="offset"
                  />
                  <select
                    className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                    value={r.offsetUnit ?? 'SECONDS'}
                    onChange={(e) =>
                      update(i, { offsetUnit: e.target.value as TimeUnit })
                    }
                  >
                    {UNITS.map((u) => (
                      <option key={u}>{u}</option>
                    ))}
                  </select>
                </div>
              </>
            )}
            <div className="flex gap-1">
              <input
                type="number"
                className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.toleranceValue}
                onChange={(e) => update(i, { toleranceValue: Number(e.target.value) })}
                placeholder="tolerance"
              />
              <select
                className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.toleranceUnit}
                onChange={(e) => update(i, { toleranceUnit: e.target.value as TimeUnit })}
              >
                {UNITS.map((u) => (
                  <option key={u}>{u}</option>
                ))}
              </select>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

4. Replace `fix-flow-ui/src/panels/right/NodeConfig/RetryConfig.tsx`:
```typescript
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface RetryCfg {
  targetNodeId?: string;
}

interface Props {
  node: ScenarioNode;
}

export function RetryConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as RetryCfg) ?? {};
  const policy = node.retryPolicy ?? { maxAttempts: 3, delayMs: 1000 };

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input
          type="text"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name}
          onChange={(e) => updateNode(node.id, { name: e.target.value })}
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Max Attempts</label>
        <input
          type="number"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.maxAttempts}
          onChange={(e) =>
            updateNode(node.id, {
              retryPolicy: { ...policy, maxAttempts: Number(e.target.value) },
            })
          }
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Delay (ms)</label>
        <input
          type="number"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={policy.delayMs}
          onChange={(e) =>
            updateNode(node.id, {
              retryPolicy: { ...policy, delayMs: Number(e.target.value) },
            })
          }
        />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Target Node</label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetNodeId ?? ''}
          onChange={(e) =>
            updateNode(node.id, {
              config: { ...cfg, targetNodeId: e.target.value },
            })
          }
        >
          <option value="">-- select --</option>
          {allNodes
            .filter((n) => n.id !== node.id)
            .map((n) => (
              <option key={n.id} value={n.id}>
                {n.name}
              </option>
            ))}
        </select>
      </div>
    </div>
  );
}
```

5. Run:
```bash
cd fix-flow-ui && npm run dev
```
Click a VALIDATE node — rules editor with date rules appears.

6. Commit:
```bash
git add fix-flow-ui/src/panels/right/NodeConfig
git commit -m "feat(ui): add ExpectFIX, Validate, DateRules and Retry config editors"
```

---

### Task 44: SessionPanel (FIX version + CompIDs configurable)

**Files:**
- Create: `fix-flow-ui/src/panels/right/SessionPanel.tsx`
- Modify: `fix-flow-ui/src/panels/right/RightPanel.tsx`

**Steps:**

1. Create `fix-flow-ui/src/panels/right/SessionPanel.tsx`:
```typescript
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getSessions,
  createSession,
  updateSession,
  connectSession,
  disconnectSession,
} from '../../api/sessions';
import { useSessionStore } from '../../store/sessionStore';
import { FIXSessionConfig, FIXSessionCreateRequest, FIXVersion, FIXMode } from '../../types';

const FIX_VERSIONS: Array<{ value: FIXVersion; label: string }> = [
  { value: 'FIX_42', label: 'FIX 4.2' },
  { value: 'FIX_44', label: 'FIX 4.4' },
  { value: 'FIXT_11', label: 'FIX 5.0 SP2 (FIXT.1.1)' },
];

const MODES: FIXMode[] = ['INITIATOR', 'ACCEPTOR'];

type FormValues = FIXSessionCreateRequest;

const DEFAULTS: FormValues = {
  name: 'default',
  mode: 'INITIATOR',
  fixVersion: 'FIX_44',
  defaultApplVerID: 'FIX.5.0SP2',
  senderCompID: 'CLIENT',
  targetCompID: 'SERVER',
  host: 'localhost',
  port: 9876,
  heartbeatInterval: 30,
  reconnectInterval: 5,
  resetOnLogon: true,
  resetOnLogout: false,
};

export function SessionPanel() {
  const queryClient = useQueryClient();
  const setSessions = useSessionStore((s) => s.setSessions);
  const activeSession = useSessionStore((s) => s.activeSession);
  const setActiveSession = useSessionStore((s) => s.setActiveSession);
  const [editingId, setEditingId] = useState<string | null>(null);

  const { register, handleSubmit, reset, watch, setValue } = useForm<FormValues>({
    defaultValues: DEFAULTS,
  });
  const fixVersion = watch('fixVersion');

  const { data: sessions } = useQuery({
    queryKey: ['sessions'],
    queryFn: getSessions,
  });

  useEffect(() => {
    if (sessions) setSessions(sessions);
  }, [sessions, setSessions]);

  useEffect(() => {
    if (activeSession) {
      reset({
        name: activeSession.name,
        mode: activeSession.mode,
        fixVersion: activeSession.fixVersion,
        defaultApplVerID: activeSession.defaultApplVerID,
        senderCompID: activeSession.senderCompID,
        targetCompID: activeSession.targetCompID,
        host: activeSession.host,
        port: activeSession.port,
        heartbeatInterval: activeSession.heartbeatInterval,
        reconnectInterval: activeSession.reconnectInterval,
        resetOnLogon: activeSession.resetOnLogon,
        resetOnLogout: activeSession.resetOnLogout,
      });
      setEditingId(activeSession.id);
    } else {
      reset(DEFAULTS);
      setEditingId(null);
    }
  }, [activeSession, reset]);

  const saveMutation = useMutation({
    mutationFn: async (values: FormValues): Promise<FIXSessionConfig> => {
      return editingId ? updateSession(editingId, values) : createSession(values);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(saved);
    },
  });

  const connectMutation = useMutation({
    mutationFn: async () => {
      if (!editingId) throw new Error('Save session first');
      return connectSession(editingId);
    },
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(s);
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: async () => {
      if (!editingId) throw new Error('No session');
      return disconnectSession(editingId);
    },
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(s);
    },
  });

  const connected = activeSession?.connected ?? false;

  return (
    <div className="p-2 overflow-y-auto border-t border-[#2a2d3a]">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs uppercase tracking-wider text-gray-500">Session</div>
        <div
          className={`px-2 py-0.5 rounded text-[10px] ${
            connected ? 'bg-green-700 text-white' : 'bg-gray-700 text-gray-300'
          }`}
        >
          {connected ? 'CONNECTED' : 'DISCONNECTED'}
        </div>
      </div>
      <div className="mb-2">
        <label className="text-[10px] text-gray-500">Active session</label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={activeSession?.id ?? ''}
          onChange={(e) => {
            const s = (sessions ?? []).find((x) => x.id === e.target.value) ?? null;
            setActiveSession(s);
          }}
        >
          <option value="">-- new session --</option>
          {(sessions ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <form
        onSubmit={handleSubmit((values) => saveMutation.mutate(values))}
        className="text-xs space-y-2"
      >
        <Field label="Name">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('name', { required: true })}
          />
        </Field>
        <Field label="Mode">
          <select
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('mode')}
          >
            {MODES.map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
        </Field>
        <Field label="FIX Version">
          <select
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            disabled={connected}
            {...register('fixVersion', {
              onChange: (e) => {
                if (e.target.value === 'FIXT_11') {
                  setValue('defaultApplVerID', 'FIX.5.0SP2');
                }
              },
            })}
          >
            {FIX_VERSIONS.map((v) => (
              <option key={v.value} value={v.value}>
                {v.label}
              </option>
            ))}
          </select>
          {connected && (
            <div className="text-[10px] text-amber-400 mt-1">
              Disconnect before changing FIX version
            </div>
          )}
        </Field>
        {fixVersion === 'FIXT_11' && (
          <Field label="DefaultApplVerID">
            <input
              type="text"
              className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
              {...register('defaultApplVerID')}
            />
          </Field>
        )}
        <Field label="SenderCompID">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('senderCompID', { required: true })}
          />
        </Field>
        <Field label="TargetCompID">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('targetCompID', { required: true })}
          />
        </Field>
        <Field label="Host">
          <input
            type="text"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('host')}
          />
        </Field>
        <Field label="Port">
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('port', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Heartbeat Interval (sec)">
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('heartbeatInterval', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Reconnect Interval (sec)">
          <input
            type="number"
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
            {...register('reconnectInterval', { valueAsNumber: true })}
          />
        </Field>
        <label className="flex items-center gap-2">
          <input type="checkbox" {...register('resetOnLogon')} /> Reset on Logon
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" {...register('resetOnLogout')} /> Reset on Logout
        </label>
        <div className="flex gap-1">
          <button
            type="submit"
            className="flex-1 px-2 py-1 rounded bg-gray-700 hover:bg-gray-600"
          >
            Save
          </button>
          {connected ? (
            <button
              type="button"
              className="flex-1 px-2 py-1 rounded bg-red-600 hover:bg-red-500"
              onClick={() => disconnectMutation.mutate()}
            >
              Disconnect
            </button>
          ) : (
            <button
              type="button"
              className="flex-1 px-2 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40"
              disabled={!editingId}
              onClick={() => connectMutation.mutate()}
            >
              Connect
            </button>
          )}
        </div>
      </form>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-[10px] text-gray-500">{label}</label>
      {children}
    </div>
  );
}
```

2. Replace `fix-flow-ui/src/panels/right/RightPanel.tsx`:
```typescript
import { PropertiesPanel } from './PropertiesPanel';
import { SessionPanel } from './SessionPanel';

export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] flex flex-col">
      <div className="flex-1 min-h-0 overflow-hidden">
        <PropertiesPanel />
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <SessionPanel />
      </div>
    </div>
  );
}
```

3. Run:
```bash
cd fix-flow-ui && npm run dev
```
Session panel shows all fields, FIX version dropdown works, DefaultApplVerID appears only for FIX 5.0.

4. Commit:
```bash
git add fix-flow-ui/src/panels/right
git commit -m "feat(ui): add SessionPanel with FIX version, CompIDs and connect controls"
```

---

### Task 45: Save scenario + YAML sync

**Files:**
- Modify: `fix-flow-ui/src/lib/scenarioSerializer.ts`
- Already wired: `fix-flow-ui/src/components/TopBar.tsx` (Save handler from Task 37)
- Already wired: `fix-flow-ui/src/panels/left/ScenarioList.tsx` (parse on select from Task 39)

**Steps:**

1. Replace `fix-flow-ui/src/lib/scenarioSerializer.ts` with a full implementation backed by `js-yaml`:
```typescript
import yaml from 'js-yaml';
import {
  NodeType,
  RetryPolicy,
  ScenarioEdge,
  ScenarioNode,
  TimeoutConfig,
} from '../types';

export interface ScenarioMeta {
  id: string;
  name: string;
  description: string;
  version: string;
  sessionRef: string;
}

interface YamlNode {
  id: string;
  name: string;
  type: NodeType;
  config?: Record<string, unknown>;
  timeout?: TimeoutConfig;
  retryPolicy?: RetryPolicy;
  onSuccess?: string;
  onFailure?: string;
  onTimeout?: string;
  position?: { x: number; y: number };
}

interface YamlEdge {
  from: string;
  to: string;
  label: string;
}

interface YamlDoc extends ScenarioMeta {
  nodes: YamlNode[];
  edges: YamlEdge[];
}

export function serializeToYaml(
  nodes: ScenarioNode[],
  edges: ScenarioEdge[],
  meta: ScenarioMeta,
): string {
  const doc: YamlDoc = {
    ...meta,
    nodes: nodes.map((n) => ({
      id: n.id,
      name: n.name,
      type: n.type,
      config: n.config ?? {},
      timeout: n.timeout,
      retryPolicy: n.retryPolicy,
      onSuccess: n.onSuccess,
      onFailure: n.onFailure,
      onTimeout: n.onTimeout,
      position: n.position,
    })),
    edges: edges.map((e) => ({ from: e.from, to: e.to, label: e.label })),
  };
  return yaml.dump(doc, { noRefs: true, sortKeys: false, lineWidth: 120 });
}

export function parseFromYaml(yamlStr: string): {
  nodes: ScenarioNode[];
  edges: ScenarioEdge[];
  meta: ScenarioMeta;
} {
  if (!yamlStr.trim()) {
    return {
      nodes: [],
      edges: [],
      meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
    };
  }
  const doc = yaml.load(yamlStr) as YamlDoc | null;
  if (!doc) {
    return {
      nodes: [],
      edges: [],
      meta: { id: '', name: '', description: '', version: '', sessionRef: '' },
    };
  }
  const meta: ScenarioMeta = {
    id: doc.id ?? '',
    name: doc.name ?? '',
    description: doc.description ?? '',
    version: doc.version ?? '1.0',
    sessionRef: doc.sessionRef ?? '',
  };
  const nodes: ScenarioNode[] = (doc.nodes ?? []).map((n) => ({
    id: n.id,
    name: n.name,
    type: n.type,
    config: n.config ?? {},
    timeout: n.timeout,
    retryPolicy: n.retryPolicy,
    onSuccess: n.onSuccess,
    onFailure: n.onFailure,
    onTimeout: n.onTimeout,
    position: n.position,
  }));
  const edges: ScenarioEdge[] = (doc.edges ?? []).map((e) => ({
    from: e.from,
    to: e.to,
    label: e.label,
  }));
  return { nodes, edges, meta };
}
```

2. Run:
```bash
cd fix-flow-ui && npm run dev
```
Create a flow, save, reload the page, then re-open the scenario from ScenarioList — nodes and edges restore from YAML.

3. Run:
```bash
cd fix-flow-ui && npm run build
```
Expect no errors.

4. Commit:
```bash
git add fix-flow-ui/src/lib
git commit -m "feat(ui): implement YAML scenario serializer and parser with round-trip support"
```

---

## Phase 13: Reporting (Task 46)

### Task 46: Execution report download

**Files:**
- Modify: `fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/dto/ReportDto.java`
- Create: `fix-flow-api/src/main/java/com/fixflow/api/service/ReportService.java`
- Create: `fix-flow-ui/src/panels/bottom/ExecutionReport.tsx`
- Modify: `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx`
- Test: `fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java`

**Steps:**

1. Create `fix-flow-api/src/main/java/com/fixflow/api/dto/ReportDto.java`:
```java
package com.fixflow.api.dto;

import java.util.List;
import java.util.Map;

public record ReportDto(
    String executionId,
    String scenarioName,
    String scenarioVersion,
    String sessionName,
    String status,
    String startTime,
    String endTime,
    long durationMs,
    List<NodeResultDto> nodeResults,
    List<String> rawFIXMessages,
    List<ValidationErrorDto> validationErrors,
    Map<String, Object> statistics
) {
    public record NodeResultDto(
        String nodeId,
        String nodeName,
        String status,
        long durationMs
    ) {}

    public record ValidationErrorDto(
        int tag,
        String rule,
        String expected,
        String actual,
        String message
    ) {}
}
```

2. Create `fix-flow-api/src/main/java/com/fixflow/api/service/ReportService.java`:
```java
package com.fixflow.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.api.dto.ReportDto.NodeResultDto;
import com.fixflow.api.dto.ReportDto.ValidationErrorDto;
import com.fixflow.persistence.entity.ExecutionEntity;
import com.fixflow.persistence.entity.ExecutionEventEntity;
import com.fixflow.persistence.entity.FIXMessageEntity;
import com.fixflow.persistence.entity.ScenarioEntity;
import com.fixflow.persistence.entity.FIXSessionEntity;
import com.fixflow.persistence.repository.ExecutionEventRepository;
import com.fixflow.persistence.repository.ExecutionRepository;
import com.fixflow.persistence.repository.FIXMessageRepository;
import com.fixflow.persistence.repository.ScenarioRepository;
import com.fixflow.persistence.repository.FIXSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class ReportService {

    private final ExecutionRepository executions;
    private final ScenarioRepository scenarios;
    private final FIXSessionRepository sessions;
    private final ExecutionEventRepository events;
    private final FIXMessageRepository messages;
    private final ObjectMapper objectMapper;

    public ReportService(
            ExecutionRepository executions,
            ScenarioRepository scenarios,
            FIXSessionRepository sessions,
            ExecutionEventRepository events,
            FIXMessageRepository messages,
            ObjectMapper objectMapper) {
        this.executions = executions;
        this.scenarios = scenarios;
        this.sessions = sessions;
        this.events = events;
        this.messages = messages;
        this.objectMapper = objectMapper;
    }

    public ReportDto buildReport(UUID executionId) {
        ExecutionEntity exec = executions.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        ScenarioEntity scen = scenarios.findById(exec.getScenarioId()).orElse(null);
        FIXSessionEntity sess = sessions.findById(exec.getSessionId()).orElse(null);
        List<ExecutionEventEntity> eventList = events.findByExecutionIdOrderByTimestampAsc(executionId);
        List<FIXMessageEntity> messageList = messages.findByExecutionIdOrderByReceivedAtAsc(executionId);

        long durationMs = 0L;
        if (exec.getStartTime() != null && exec.getEndTime() != null) {
            durationMs = Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis();
        }

        Map<String, Long> nodeStartTimes = new HashMap<>();
        List<NodeResultDto> nodeResults = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        List<ValidationErrorDto> validationErrors = new ArrayList<>();

        for (ExecutionEventEntity e : eventList) {
            String type = e.getType();
            String nodeId = e.getNodeId();
            long ts = e.getTimestamp().toEpochMilli();
            if ("NODE_STARTED".equals(type) && nodeId != null) {
                nodeStartTimes.put(nodeId, ts);
            } else if (("NODE_COMPLETED".equals(type) || "NODE_FAILED".equals(type)) && nodeId != null) {
                Long start = nodeStartTimes.get(nodeId);
                long dur = start != null ? ts - start : 0L;
                String status = "NODE_COMPLETED".equals(type) ? "PASSED" : "FAILED";
                if ("PASSED".equals(status)) passed++; else failed++;
                nodeResults.add(new NodeResultDto(nodeId, nodeId, status, dur));
            } else if ("VALIDATION_FAILED".equals(type) && e.getDetail() != null) {
                validationErrors.add(parseValidationError(e.getDetail()));
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("nodesTotal", nodeResults.size());
        stats.put("nodesPassed", passed);
        stats.put("nodesFailed", failed);
        stats.put("messagesTotal", messageList.size());
        stats.put("eventsTotal", eventList.size());

        List<String> rawMessages = messageList.stream().map(FIXMessageEntity::getRawFix).toList();

        return new ReportDto(
                executionId.toString(),
                scen != null ? scen.getName() : "",
                scen != null ? scen.getVersion() : "",
                sess != null ? sess.getName() : "",
                exec.getStatus().name(),
                String.valueOf(exec.getStartTime()),
                exec.getEndTime() != null ? exec.getEndTime().toString() : "",
                durationMs,
                nodeResults,
                rawMessages,
                validationErrors,
                stats
        );
    }

    private ValidationErrorDto parseValidationError(String json) {
        try {
            Map<?, ?> m = objectMapper.readValue(json, Map.class);
            int tag = m.get("tag") instanceof Number n ? n.intValue() : 0;
            return new ValidationErrorDto(
                    tag,
                    String.valueOf(m.getOrDefault("rule", "")),
                    String.valueOf(m.getOrDefault("expected", "")),
                    String.valueOf(m.getOrDefault("actual", "")),
                    String.valueOf(m.getOrDefault("message", ""))
            );
        } catch (Exception ex) {
            return new ValidationErrorDto(0, "UNKNOWN", "", json, ex.getMessage());
        }
    }
}
```

3. Update `fix-flow-api/src/main/java/com/fixflow/api/rest/ExecutionController.java` — add report endpoints:
```java
package com.fixflow.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.api.dto.ReportDto;
import com.fixflow.api.service.ReportService;
// ... existing imports
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;
    // ... existing fields and constructor injection adds reportService + objectMapper

    public ExecutionController(/* existing args */ ReportService reportService, ObjectMapper objectMapper) {
        // ... existing assignments
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{id}/report")
    public ReportDto getReport(@PathVariable UUID id) {
        return reportService.buildReport(id);
    }

    @GetMapping("/{id}/report/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID id) throws Exception {
        ReportDto report = reportService.buildReport(id);
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"execution-" + id + "-report.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }
}
```

4. Create `fix-flow-ui/src/panels/bottom/ExecutionReport.tsx`:
```typescript
import { useExecutionStore } from '../../store/executionStore';
import { getExecutionReport } from '../../api/executions';

export function ExecutionReport() {
  const executionId = useExecutionStore((s) => s.activeExecutionId);

  const download = async () => {
    if (!executionId) return;
    const report = await getExecutionReport(executionId);
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `execution-${executionId}-report.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <button
      className="px-2 py-1 rounded bg-blue-600 hover:bg-blue-500 text-xs disabled:opacity-40"
      onClick={download}
      disabled={!executionId}
    >
      Download Report
    </button>
  );
}
```

5. Modify `fix-flow-ui/src/panels/bottom/ExecutionStats.tsx` — add the download button at the top:
```typescript
// At the top of the rendered JSX inside the outer div, add a row containing <ExecutionReport />.
import { ExecutionReport } from './ExecutionReport';

// Inside the returned grid div, wrap with a flex container:
return (
  <div className="h-full overflow-y-auto px-3 py-2 space-y-2">
    <div className="flex justify-end">
      <ExecutionReport />
    </div>
    <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs">
      <Stat label="Status" value={executionStatus} />
      <Stat label="Nodes passed" value={String(stats.nodesPassed)} />
      <Stat label="Nodes failed" value={String(stats.nodesFailed)} />
      <Stat label="Avg node time" value={`${stats.avgNodeMs} ms`} />
      <Stat label="Total duration" value={`${stats.duration} ms`} />
    </div>
  </div>
);
```

6. Create `fix-flow-api/src/test/java/com/fixflow/api/rest/ExecutionControllerTest.java`:
```java
package com.fixflow.api.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionControllerTest {

    @Autowired private MockMvc mvc;

    @Test
    void downloadReportReturnsAttachment() throws Exception {
        UUID id = TestExecutionFactory.createCompletedExecution();
        mvc.perform(get("/api/v1/executions/{id}/report/download", id))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                    "attachment; filename=\"execution-" + id + "-report.json\""))
            .andExpect(content().contentType("application/json"));
    }

    @Test
    void getReportReturnsJsonWithStatistics() throws Exception {
        UUID id = TestExecutionFactory.createCompletedExecution();
        mvc.perform(get("/api/v1/executions/{id}/report", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(id.toString()))
            .andExpect(jsonPath("$.statistics.nodesTotal").exists())
            .andExpect(jsonPath("$.statistics.messagesTotal").exists());
    }
}
```
Note: `TestExecutionFactory.createCompletedExecution()` is a helper that seeds a scenario, session, execution, events and messages directly via repositories. Implement it under `src/test/java/com/fixflow/api/rest/TestExecutionFactory.java` using the same repositories that Task 36 (backend) set up.

7. Run:
```bash
mvn test -pl fix-flow-api -Dtest=ExecutionControllerTest
```
Expect both tests to pass.

8. Commit:
```bash
git add fix-flow-api fix-flow-ui/src
git commit -m "feat(report): add execution report endpoint, download, and stats panel button"
```

---

## Phase 14: Full Test Suite (Tasks 47-49)

### Task 47: Unit test coverage for engine

**Files:**
- Create: `fix-flow-engine/src/main/java/com/fixflow/engine/scenario/ScenarioValidator.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/variable/DateOffsetPluginTest.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/validation/StrictModeValidationTest.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/scenario/ScenarioValidatorTest.java`

**Steps:**

1. Create `fix-flow-engine/src/test/java/com/fixflow/engine/variable/DateOffsetPluginTest.java`:
```java
package com.fixflow.engine.variable;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateOffsetPluginTest {

    private final DateOffsetPlugin plugin = new DateOffsetPlugin();

    @Test
    void plusFiveMinutes() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "+5m");
        assertEquals(base.plus(5, ChronoUnit.MINUTES), result);
    }

    @Test
    void minusThirtySeconds() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "-30s");
        assertEquals(base.minus(30, ChronoUnit.SECONDS), result);
    }

    @Test
    void plusTwoHours() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "+2h");
        assertEquals(base.plus(2, ChronoUnit.HOURS), result);
    }

    @Test
    void plusOneDay() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        Instant result = plugin.applyOffset(base, "+1d");
        assertEquals(base.plus(1, ChronoUnit.DAYS), result);
    }

    @Test
    void parsesIsoStringWithinOneSecond() {
        Instant base = Instant.parse("2025-01-15T10:00:00Z");
        String iso = plugin.format(plugin.applyOffset(base, "+5m"));
        Instant parsed = Instant.parse(iso);
        assertTrue(Math.abs(parsed.toEpochMilli() - base.plus(5, ChronoUnit.MINUTES).toEpochMilli()) < 1000);
    }
}
```

2. Create `fix-flow-engine/src/test/java/com/fixflow/engine/validation/StrictModeValidationTest.java`:
```java
package com.fixflow.engine.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictModeValidationTest {

    private final ValidationEngine engine = new ValidationEngine();

    private static final Map<Integer, String> MESSAGE = Map.of(
            35, "D",
            49, "CLIENT",
            56, "SERVER",
            11, "ORDER-1"
    );

    @Test
    void strictModeFailsOnUnvalidatedTags() {
        ValidationConfig cfg = new ValidationConfig(
                true,
                List.of(
                        new ValidationRule(35, RuleKind.EQUALS, "D", null, null, null, null),
                        new ValidationRule(11, RuleKind.EQUALS, "ORDER-1", null, null, null, null)
                ),
                List.of()
        );
        List<ValidationResult> results = engine.validate(MESSAGE, cfg, Map.of());
        long failures = results.stream().filter(r -> !r.passed()).count();
        assertEquals(2, failures, "Tags 49 and 56 should fail in strict mode");
    }

    @Test
    void nonStrictModePassesUnvalidatedTags() {
        ValidationConfig cfg = new ValidationConfig(
                false,
                List.of(
                        new ValidationRule(35, RuleKind.EQUALS, "D", null, null, null, null),
                        new ValidationRule(11, RuleKind.EQUALS, "ORDER-1", null, null, null, null)
                ),
                List.of()
        );
        List<ValidationResult> results = engine.validate(MESSAGE, cfg, Map.of());
        assertTrue(results.stream().allMatch(ValidationResult::passed));
    }
}
```

3. Create `fix-flow-engine/src/main/java/com/fixflow/engine/scenario/ScenarioValidator.java`:
```java
package com.fixflow.engine.scenario;

import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScenarioValidator {

    public List<String> validate(Scenario scenario) {
        List<String> errors = new ArrayList<>();
        if (scenario == null) {
            errors.add("Scenario is null");
            return errors;
        }
        Set<String> ids = new HashSet<>();
        for (ScenarioNode n : scenario.nodes()) {
            if (!ids.add(n.id())) {
                errors.add("Duplicate node id: " + n.id());
            }
        }
        for (ScenarioNode n : scenario.nodes()) {
            checkRef(errors, ids, n.id(), "onSuccess", n.onSuccess());
            checkRef(errors, ids, n.id(), "onFailure", n.onFailure());
            checkRef(errors, ids, n.id(), "onTimeout", n.onTimeout());
            if (n.timeout() != null && "JUMP".equals(n.timeout().onTimeout())) {
                checkRef(errors, ids, n.id(), "timeout.jumpTo", n.timeout().jumpTo());
            }
        }
        scenario.edges().forEach(e -> {
            if (!ids.contains(e.from())) errors.add("Edge from unknown node: " + e.from());
            if (!ids.contains(e.to())) errors.add("Edge to unknown node: " + e.to());
        });
        boolean hasStart = scenario.nodes().stream().anyMatch(n -> "START".equals(n.type()));
        if (!hasStart) errors.add("Scenario has no START node");
        return errors;
    }

    private void checkRef(List<String> errors, Set<String> ids, String nodeId, String field, String ref) {
        if (ref != null && !ref.isEmpty() && !ids.contains(ref)) {
            errors.add("Node " + nodeId + "." + field + " references unknown node: " + ref);
        }
    }
}
```

4. Create `fix-flow-engine/src/test/java/com/fixflow/engine/scenario/ScenarioValidatorTest.java`:
```java
package com.fixflow.engine.scenario;

import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioValidatorTest {

    private final ScenarioValidator validator = new ScenarioValidator();

    @Test
    void detectsUnknownOnSuccessReference() {
        Scenario scen = new Scenario(
                "s1", "S1", "", "1.0", "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "missing", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of()
        );
        List<String> errors = validator.validate(scen);
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing")));
    }

    @Test
    void validScenarioYieldsNoErrors() {
        Scenario scen = new Scenario(
                "s1", "S1", "", "1.0", "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "end", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of(new ScenarioEdge("start", "end", "success"))
        );
        assertEquals(List.of(), validator.validate(scen));
    }

    @Test
    void detectsMissingStart() {
        Scenario scen = new Scenario(
                "s1", "S1", "", "1.0", "default",
                List.of(new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)),
                List.of()
        );
        assertTrue(validator.validate(scen).stream().anyMatch(e -> e.contains("no START")));
    }
}
```

5. Run:
```bash
mvn test -pl fix-flow-engine
```
Expect all tests PASS.

6. Commit:
```bash
git add fix-flow-engine
git commit -m "test(engine): add DateOffsetPlugin, StrictModeValidation, and ScenarioValidator tests"
```

---

### Task 48: FakeFixAdapter multi-scenario system test

**Files:**
- Modify: `fix-flow-engine/pom.xml` (add awaitility)
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/SystemTest.java`

**Steps:**

1. Add awaitility dependency to `fix-flow-engine/pom.xml`:
```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.1</version>
    <scope>test</scope>
</dependency>
```

2. Create `fix-flow-engine/src/test/java/com/fixflow/engine/SystemTest.java`:
```java
package com.fixflow.engine;

import com.fixflow.engine.adapter.FakeFixAdapter;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.ExecutionStatus;
import com.fixflow.engine.handler.EndHandler;
import com.fixflow.engine.handler.ExpectFIXHandler;
import com.fixflow.engine.handler.NodeDispatcher;
import com.fixflow.engine.handler.SendFIXHandler;
import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import com.fixflow.engine.publisher.NoOpEventPublisher;
import com.fixflow.engine.routing.MessageBuffer;
import com.fixflow.engine.routing.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.session.FIXSessionManager;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemTest {

    @Test
    void threeParallelScenariosOnOneSession() throws Exception {
        FakeFixAdapter fake = new FakeFixAdapter();
        MessageBuffer buffer = new MessageBuffer(1000, 60_000L);
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router::route);

        ScenarioRegistry registry = new ScenarioRegistry();
        NoOpEventPublisher publisher = new NoOpEventPublisher();
        VariableResolver resolver = new VariableResolver();
        FIXSessionManager sessionManager = new FIXSessionManager(fake);
        NodeDispatcher dispatcher = new NodeDispatcher(List.of(
                new SendFIXHandler(sessionManager, resolver),
                new ExpectFIXHandler(correlation),
                new EndHandler()
        ));
        ExecutionManager manager = new ExecutionManager(dispatcher, registry, publisher);

        UUID sessionId = UUID.randomUUID();
        Scenario s1 = buildScenario("S1", "REQ-001");
        Scenario s2 = buildScenario("S2", "REQ-002");
        Scenario s3 = buildScenario("S3", "REQ-003");
        registry.register(s1);
        registry.register(s2);
        registry.register(s3);

        UUID e1 = manager.start(s1.id(), sessionId);
        UUID e2 = manager.start(s2.id(), sessionId);
        UUID e3 = manager.start(s3.id(), sessionId);

        Thread.sleep(50);
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-003"));
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-001"));
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-002"));

        await().atMost(5, SECONDS).until(() ->
                manager.getStatus(e1) == ExecutionStatus.PASSED
             && manager.getStatus(e2) == ExecutionStatus.PASSED
             && manager.getStatus(e3) == ExecutionStatus.PASSED
        );

        assertEquals(ExecutionStatus.PASSED, manager.getStatus(e1));
        assertEquals(ExecutionStatus.PASSED, manager.getStatus(e2));
        assertEquals(ExecutionStatus.PASSED, manager.getStatus(e3));
    }

    private Scenario buildScenario(String id, String clOrdId) {
        ScenarioNode start = new ScenarioNode("start", "Start", "START", Map.of(),
                null, null, "send", null, null, null);
        ScenarioNode send = new ScenarioNode("send", "Send NOS", "SEND_FIX",
                Map.of("msgType", "D", "fields", List.of(Map.of("tag", 11, "value", clOrdId))),
                null, null, "expect", null, null, null);
        ScenarioNode expect = new ScenarioNode("expect", "Expect ER", "EXPECT_FIX",
                Map.of("msgType", "8",
                       "correlation", Map.of("sourceTag", 11, "fromNode", "send", "targetTag", 11)),
                null, null, "end", null, null, null);
        ScenarioNode end = new ScenarioNode("end", "End", "END_PASS",
                Map.of(), null, null, null, null, null, null);
        List<ScenarioEdge> edges = List.of(
                new ScenarioEdge("start", "send", "success"),
                new ScenarioEdge("send", "expect", "success"),
                new ScenarioEdge("expect", "end", "success")
        );
        return new Scenario(id, id, "", "1.0", "default",
                List.of(start, send, expect, end), edges);
    }
}
```

3. Run:
```bash
mvn test -pl fix-flow-engine -Dtest=SystemTest
```
Expect PASS.

4. Commit:
```bash
git add fix-flow-engine
git commit -m "test(engine): add multi-scenario parallel execution system test on shared session"
```

---

### Task 49: Hot reload + session failure test

**Files:**
- Modify: `fix-flow-engine/src/main/java/com/fixflow/engine/adapter/FakeFixAdapter.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/HotReloadTest.java`
- Test: `fix-flow-engine/src/test/java/com/fixflow/engine/SessionFailureTest.java`

**Steps:**

1. Add `simulateSessionDown` to `fix-flow-engine/src/main/java/com/fixflow/engine/adapter/FakeFixAdapter.java`:
```java
public void simulateSessionDown(UUID sessionId) {
    if (sessionStatusListener != null) {
        sessionStatusListener.onSessionStatus(sessionId, SessionStatus.DOWN, Instant.now());
    }
}
```
Also expose a `setSessionStatusListener(SessionStatusListener listener)` setter, and add the `SessionStatusListener` functional interface in the engine package if not already present:
```java
@FunctionalInterface
public interface SessionStatusListener {
    void onSessionStatus(UUID sessionId, SessionStatus status, Instant timestamp);
}

public enum SessionStatus { UP, DOWN, LOGON, LOGOUT }
```

2. Create `fix-flow-engine/src/test/java/com/fixflow/engine/HotReloadTest.java`:
```java
package com.fixflow.engine;

import com.fixflow.engine.adapter.FakeFixAdapter;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.ExecutionStatus;
import com.fixflow.engine.handler.EndHandler;
import com.fixflow.engine.handler.ExpectFIXHandler;
import com.fixflow.engine.handler.NodeDispatcher;
import com.fixflow.engine.handler.SendFIXHandler;
import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import com.fixflow.engine.publisher.NoOpEventPublisher;
import com.fixflow.engine.routing.MessageBuffer;
import com.fixflow.engine.routing.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.session.FIXSessionManager;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HotReloadTest {

    @Test
    void inFlightExecutionUsesOldVersionAfterReload() throws Exception {
        FakeFixAdapter fake = new FakeFixAdapter();
        MessageBuffer buffer = new MessageBuffer(1000, 60_000L);
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router::route);

        ScenarioRegistry registry = new ScenarioRegistry();
        ExecutionManager manager = new ExecutionManager(
                new NodeDispatcher(List.of(
                        new SendFIXHandler(new FIXSessionManager(fake), new VariableResolver()),
                        new ExpectFIXHandler(correlation),
                        new EndHandler()
                )),
                registry,
                new NoOpEventPublisher()
        );

        UUID sessionId = UUID.randomUUID();
        Scenario v1 = scenario("scen", "1.0", "REQ-A");
        registry.register(v1);
        UUID inFlight = manager.start(v1.id(), sessionId);

        Thread.sleep(100);

        Scenario v2 = scenario("scen", "2.0", "REQ-B");
        registry.reload(v2);

        // Inject the v1 expected response
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-A"));
        await().atMost(5, SECONDS).until(() -> manager.getStatus(inFlight) == ExecutionStatus.PASSED);

        UUID newExec = manager.start(v2.id(), sessionId);
        Thread.sleep(50);
        fake.injectInbound(sessionId, Map.of(35, "8", 11, "REQ-B"));
        await().atMost(5, SECONDS).until(() -> manager.getStatus(newExec) == ExecutionStatus.PASSED);

        assertEquals("1.0", manager.getScenarioVersion(inFlight));
        assertEquals("2.0", manager.getScenarioVersion(newExec));
    }

    private Scenario scenario(String id, String version, String clOrdId) {
        return new Scenario(id, id, "", version, "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "send", null, null, null),
                        new ScenarioNode("send", "Send", "SEND_FIX",
                                Map.of("msgType", "D",
                                        "fields", List.of(Map.of("tag", 11, "value", clOrdId))),
                                null, null, "expect", null, null, null),
                        new ScenarioNode("expect", "Expect", "EXPECT_FIX",
                                Map.of("msgType", "8",
                                        "correlation", Map.of("sourceTag", 11, "fromNode", "send", "targetTag", 11)),
                                null, null, "end", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of(
                        new ScenarioEdge("start", "send", "success"),
                        new ScenarioEdge("send", "expect", "success"),
                        new ScenarioEdge("expect", "end", "success")
                )
        );
    }
}
```

3. Create `fix-flow-engine/src/test/java/com/fixflow/engine/SessionFailureTest.java`:
```java
package com.fixflow.engine;

import com.fixflow.engine.adapter.FakeFixAdapter;
import com.fixflow.engine.correlation.CorrelationEngine;
import com.fixflow.engine.execution.ExecutionManager;
import com.fixflow.engine.execution.ExecutionStatus;
import com.fixflow.engine.handler.EndHandler;
import com.fixflow.engine.handler.ExpectFIXHandler;
import com.fixflow.engine.handler.NodeDispatcher;
import com.fixflow.engine.handler.SendFIXHandler;
import com.fixflow.engine.model.Scenario;
import com.fixflow.engine.model.ScenarioEdge;
import com.fixflow.engine.model.ScenarioNode;
import com.fixflow.engine.publisher.NoOpEventPublisher;
import com.fixflow.engine.routing.MessageBuffer;
import com.fixflow.engine.routing.MessageRouter;
import com.fixflow.engine.scenario.ScenarioRegistry;
import com.fixflow.engine.session.FIXSessionManager;
import com.fixflow.engine.variable.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionFailureTest {

    @Test
    void sessionDownFailsAffectedExecutionOnly() throws Exception {
        FakeFixAdapter fake = new FakeFixAdapter();
        MessageBuffer buffer = new MessageBuffer(1000, 60_000L);
        CorrelationEngine correlation = new CorrelationEngine();
        MessageRouter router = new MessageRouter(correlation, buffer);
        fake.setInboundListener(router::route);
        FIXSessionManager sessionManager = new FIXSessionManager(fake);
        fake.setSessionStatusListener(sessionManager::onSessionStatusChange);

        ScenarioRegistry registry = new ScenarioRegistry();
        ExecutionManager manager = new ExecutionManager(
                new NodeDispatcher(List.of(
                        new SendFIXHandler(sessionManager, new VariableResolver()),
                        new ExpectFIXHandler(correlation),
                        new EndHandler()
                )),
                registry,
                new NoOpEventPublisher()
        );
        sessionManager.setExecutionManager(manager);

        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        Scenario s = scenario("s1", "1.0", "REQ-X");
        registry.register(s);

        UUID exA = manager.start(s.id(), sessionA);
        UUID exB = manager.start(s.id(), sessionB);

        Thread.sleep(100);
        fake.simulateSessionDown(sessionA);

        await().atMost(5, SECONDS).until(() -> manager.getStatus(exA) == ExecutionStatus.FAILED);

        // exB completes normally
        fake.injectInbound(sessionB, Map.of(35, "8", 11, "REQ-X"));
        await().atMost(5, SECONDS).until(() -> manager.getStatus(exB) == ExecutionStatus.PASSED);

        assertEquals(ExecutionStatus.FAILED, manager.getStatus(exA));
        assertEquals(ExecutionStatus.PASSED, manager.getStatus(exB));
    }

    private Scenario scenario(String id, String version, String clOrdId) {
        return new Scenario(id, id, "", version, "default",
                List.of(
                        new ScenarioNode("start", "Start", "START", Map.of(), null, null, "send", null, null, null),
                        new ScenarioNode("send", "Send", "SEND_FIX",
                                Map.of("msgType", "D",
                                        "fields", List.of(Map.of("tag", 11, "value", clOrdId))),
                                null, null, "expect", null, null, null),
                        new ScenarioNode("expect", "Expect", "EXPECT_FIX",
                                Map.of("msgType", "8",
                                        "correlation", Map.of("sourceTag", 11, "fromNode", "send", "targetTag", 11)),
                                null, null, "end", null, null, null),
                        new ScenarioNode("end", "End", "END_PASS", Map.of(), null, null, null, null, null, null)
                ),
                List.of(
                        new ScenarioEdge("start", "send", "success"),
                        new ScenarioEdge("send", "expect", "success"),
                        new ScenarioEdge("expect", "end", "success")
                )
        );
    }
}
```

4. Run:
```bash
mvn test -pl fix-flow-engine
```
Expect all tests PASS.

5. Commit:
```bash
git add fix-flow-engine
git commit -m "test(engine): add hot reload and session failure integration tests"
```

---

## Phase 15: Documentation (Tasks 50-52)

### Task 50: README + local setup guide

**Files:**
- Create: `README.md`
- Create: `docs/setup.md`

**Steps:**

1. Create `README.md`:
````markdown
# FIX Flow Simulator

Visual FIX protocol scenario designer, runtime, and monitor.

## Quick Start

Prerequisites: Java 21+, Maven 3.9+, Node.js 20+.

```bash
# Build everything
mvn clean package -DskipTests

# Run
java -jar fix-flow-api/target/fix-flow-api-1.0.0.jar

# Open browser
open http://localhost:8080
```

## Features

- Visual flow editor (drag-and-drop FIX scenario design)
- Multi-scenario execution on shared FIX sessions
- FIX 4.2, FIX 4.4, FIX 5.0 SP2 (FIXT.1.1) support
- Per-session configurable: SenderCompID, TargetCompID, host, port, heartbeat interval
- Real-time execution monitoring (WebSocket)
- Validation engine with date/time rules
- Hot reload scenarios without restarting FIX session
- H2 embedded database (no install required)

## Architecture

- `fix-flow-engine` — execution engine, validation, correlation
- `fix-flow-persistence` — H2 + JPA repositories
- `fix-flow-api` — Spring Boot REST + WebSocket + static UI bundle
- `fix-flow-ui` — React + ReactFlow + Tailwind UI

## Documentation

- [Setup guide](docs/setup.md)
- [DSL reference](docs/dsl-reference.md)
- [API reference](docs/api-reference.md)
````

2. Create `docs/setup.md`:
````markdown
# Setup Guide

## Prerequisites

- Java 21 or later (`java -version`)
- Maven 3.9 or later (`mvn -version`)
- Node.js 20 or later (`node --version`) — only needed for UI development

## Production build

```bash
mvn clean package -DskipTests
java -jar fix-flow-api/target/fix-flow-api-1.0.0.jar
```

The fat JAR bundles the React UI build under `/static`. Open
`http://localhost:8080` once the application logs `Started FixFlowApplication`.

## Development mode

Run the backend with hot reload:

```bash
mvn -pl fix-flow-api spring-boot:run
```

Run the UI with Vite dev server (proxies `/api` and `/ws` to port 8080):

```bash
cd fix-flow-ui
npm install
npm run dev
```

Open `http://localhost:5173`.

## H2 console

The embedded database is exposed at `http://localhost:8080/h2-console`
with JDBC URL `jdbc:h2:file:./data/fixflow`.

## Troubleshooting

- **Port 8080 in use**: pass `--server.port=8090` on the `java -jar` command.
- **WebSocket disconnects**: verify there is no reverse proxy stripping `/ws`.
- **UI does not build**: delete `fix-flow-ui/node_modules` and rerun `npm install`.
- **FIX session won't connect**: confirm the counterparty CompIDs and that
  `host`/`port` are reachable.
````

3. Commit:
```bash
git add README.md docs/setup.md
git commit -m "docs: add README and local setup guide"
```

---

### Task 51: DSL reference + API reference

**Files:**
- Create: `docs/dsl-reference.md`
- Create: `docs/api-reference.md`

**Steps:**

1. Create `docs/dsl-reference.md`:
````markdown
# Scenario DSL Reference

Scenarios are YAML documents stored under the `yamlDsl` field of a scenario.

## Top-level shape

```yaml
id: rfq-flow
name: RFQ Flow
description: Quote request/response
version: '1.0'
sessionRef: default
nodes: []
edges: []
```

## Node types

| type | purpose |
|---|---|
| `START` | Entry point. No incoming edges. |
| `SEND_FIX` | Send a FIX message via the session. |
| `EXPECT_FIX` | Wait for a matching inbound message. |
| `VALIDATE` | Apply validation rules to a received message. |
| `DECISION` | Branch based on previous result. |
| `BRANCH` | Alias for `DECISION`. |
| `RETRY` / `LOOP` | Retry a sub-graph N times with delay. |
| `WAIT` / `DELAY` / `TIMEOUT` | Pause for a duration. |
| `END_PASS` / `END_FAIL` | Terminal nodes. |

## Common node fields

```yaml
- id: send-nos
  name: Send New Order Single
  type: SEND_FIX
  config: { ... }                # node-specific
  timeout:
    value: 30
    unit: SECONDS                # MILLISECONDS | SECONDS | MINUTES | HOURS
    onTimeout: FAIL              # FAIL | RETRY | CONTINUE | JUMP
    jumpTo: some-node-id         # required when onTimeout == JUMP
  retryPolicy:
    maxAttempts: 3
    delayMs: 1000
  onSuccess: next-node-id
  onFailure: error-node-id
  onTimeout: timeout-node-id
```

## SEND_FIX config

```yaml
config:
  msgType: D
  fields:
    - { tag: 11, value: "{{uuid}}" }
    - { tag: 55, value: AAPL }
    - { tag: 38, value: "100" }
    - { tag: 40, value: "2" }
    - { tag: 44, value: "{{node:prev:tag31}}" }
```

## EXPECT_FIX config

```yaml
config:
  msgType: 8
  correlation:
    sourceTag: 11      # tag in the inbound message
    fromNode: send-nos # node id whose outbound value should match
    targetTag: 11      # tag in the outbound message
```

## VALIDATE config

```yaml
config:
  strictMode: true
  rules:
    - { tag: 35, rule: EQUALS, value: "8" }
    - { tag: 39, rule: ENUM, values: ["0", "1", "2"] }
    - { tag: 11, rule: REGEX, pattern: "^ORD-[0-9]+$" }
    - { tag: 38, rule: NUMERIC_MIN, numericValue: 1 }
    - { tag: 60, rule: DATE_RULE, dateRuleId: dr-recent }
  dateRules:
    - ruleId: dr-recent
      type: CURRENT_TIMESTAMP
      toleranceValue: 5
      toleranceUnit: SECONDS
    - ruleId: dr-expiry
      type: FIELD_OFFSET
      sourceNode: send-nos
      sourceTag: 60
      offsetValue: 5
      offsetUnit: MINUTES
      toleranceValue: 1
      toleranceUnit: SECONDS
```

### Rule kinds

| rule | extra fields |
|---|---|
| `EQUALS` / `NOT_EQUALS` | `value` |
| `ENUM` | `values` (list) |
| `REGEX` | `pattern` |
| `NUMERIC_MIN` / `NUMERIC_MAX` | `numericValue` |
| `FIELD_PRESENT` / `FIELD_ABSENT` | none |
| `DATE_RULE` | `dateRuleId` |

## Variable syntax

| placeholder | meaning |
|---|---|
| `{{now}}` | current UTC ISO timestamp |
| `{{uuid}}` | random UUID |
| `{{seq:name}}` | monotonic sequence keyed by `name` |
| `{{env:VAR}}` | environment variable |
| `{{node:id:tagN}}` | value of tag N from a previous node |
| `{{node:id:tagN:offset:+5m}}` | value with date offset applied |

Offset format: `[+-](\d+)[smhd]` (seconds, minutes, hours, days).

## Edges

```yaml
edges:
  - { from: send-nos, to: expect-er, label: success }
  - { from: send-nos, to: end-fail, label: failure }
  - { from: send-nos, to: retry,     label: timeout }
```

## Worked example — RFQ flow

```yaml
id: rfq-flow
name: RFQ Flow
description: Quote request/response
version: '1.0'
sessionRef: default
nodes:
  - id: start
    name: Start
    type: START
    config: {}
    onSuccess: send-qr
  - id: send-qr
    name: Send QuoteRequest
    type: SEND_FIX
    config:
      msgType: R
      fields:
        - { tag: 131, value: "{{uuid}}" }
        - { tag: 55,  value: AAPL }
        - { tag: 38,  value: "100" }
    timeout: { value: 5, unit: SECONDS, onTimeout: FAIL }
    onSuccess: expect-quote
  - id: expect-quote
    name: Expect Quote
    type: EXPECT_FIX
    config:
      msgType: S
      correlation:
        sourceTag: 131
        fromNode: send-qr
        targetTag: 131
    timeout: { value: 10, unit: SECONDS, onTimeout: FAIL }
    onSuccess: validate
  - id: validate
    name: Validate Quote
    type: VALIDATE
    config:
      strictMode: false
      rules:
        - { tag: 132, rule: NUMERIC_MIN, numericValue: 0 }
        - { tag: 60,  rule: DATE_RULE,   dateRuleId: dr-fresh }
      dateRules:
        - ruleId: dr-fresh
          type: CURRENT_TIMESTAMP
          toleranceValue: 5
          toleranceUnit: SECONDS
    onSuccess: end-pass
    onFailure: end-fail
  - id: end-pass
    name: End OK
    type: END_PASS
    config: {}
  - id: end-fail
    name: End Failed
    type: END_FAIL
    config: {}
edges:
  - { from: start,        to: send-qr,      label: success }
  - { from: send-qr,      to: expect-quote, label: success }
  - { from: expect-quote, to: validate,     label: success }
  - { from: validate,     to: end-pass,     label: success }
  - { from: validate,     to: end-fail,     label: failure }
```
````

2. Create `docs/api-reference.md`:
````markdown
# REST + WebSocket API

Base URL: `/api/v1`. All requests/responses are JSON unless noted.

## Error format

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Scenario has no START node",
  "details": { "errors": ["Scenario has no START node"] }
}
```

## Scenarios

| Method | Path | Notes |
|---|---|---|
| `GET` | `/scenarios` | list all |
| `GET` | `/scenarios/{id}` | get one |
| `POST` | `/scenarios` | create — body `ScenarioCreateRequest` |
| `PUT` | `/scenarios/{id}` | update — body `ScenarioUpdateRequest` |
| `DELETE` | `/scenarios/{id}` | delete |
| `POST` | `/scenarios/{id}/validate` | returns `{valid, errors[]}` |
| `POST` | `/scenarios/import` | multipart `file` |
| `GET` | `/scenarios/{id}/export` | downloads YAML |
| `POST` | `/scenarios/{id}/execute` | body `{ sessionId }` |
| `POST` | `/scenarios/{id}/reload` | swap in-place |

### Example: create

Request:
```json
POST /api/v1/scenarios
{
  "name": "RFQ",
  "description": "Quote flow",
  "sessionRef": "default",
  "yamlDsl": "id: rfq\nname: RFQ\n..."
}
```
Response:
```json
{
  "id": "8f4...",
  "name": "RFQ",
  "version": "1.0",
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z",
  "yamlDsl": "..."
}
```

## Sessions

| Method | Path |
|---|---|
| `GET` | `/sessions` |
| `GET` | `/sessions/{id}` |
| `POST` | `/sessions` |
| `PUT` | `/sessions/{id}` |
| `DELETE` | `/sessions/{id}` |
| `POST` | `/sessions/{id}/connect` |
| `POST` | `/sessions/{id}/disconnect` |
| `GET` | `/sessions/{id}/status` |

### Create body

```json
{
  "name": "default",
  "mode": "INITIATOR",
  "fixVersion": "FIXT_11",
  "defaultApplVerID": "FIX.5.0SP2",
  "senderCompID": "CLIENT",
  "targetCompID": "SERVER",
  "host": "localhost",
  "port": 9876,
  "heartbeatInterval": 30,
  "reconnectInterval": 5,
  "resetOnLogon": true,
  "resetOnLogout": false
}
```

## Executions

| Method | Path |
|---|---|
| `GET` | `/executions/{id}` |
| `GET` | `/executions/{id}/events` |
| `GET` | `/executions/{id}/messages` |
| `GET` | `/executions/{id}/report` |
| `GET` | `/executions/{id}/report/download` |
| `POST` | `/executions/{id}/stop` |

## WebSocket

Endpoint: `http://localhost:8080/ws` (SockJS + STOMP).

Topics:

| Topic | Payload |
|---|---|
| `/topic/executions/{executionId}/events` | `ExecutionEvent` |
| `/topic/executions/{executionId}/messages` | `FIXMessage` |
| `/topic/sessions/{sessionId}/status` | `{sessionId, status, timestamp}` |

### ExecutionEvent payload

```json
{
  "id": "evt-1",
  "executionId": "exec-1",
  "type": "NODE_STARTED",
  "nodeId": "send-nos",
  "timestamp": "2026-01-01T10:00:00Z",
  "detail": null,
  "rawFix": null
}
```

Event types: `SCENARIO_STARTED`, `SCENARIO_PASSED`, `SCENARIO_FAILED`,
`SCENARIO_STOPPED`, `NODE_STARTED`, `NODE_COMPLETED`, `NODE_FAILED`,
`VALIDATION_FAILED`, `MESSAGE_SENT`, `MESSAGE_RECEIVED`, `TIMEOUT`.
````

3. Commit:
```bash
git add docs/dsl-reference.md docs/api-reference.md
git commit -m "docs: add DSL reference and REST/WebSocket API reference"
```

---

### Task 52: Final Maven build — fat JAR + frontend bundled

**Files:**
- Create: `fix-flow-ui/pom.xml`
- Modify: `fix-flow-api/pom.xml`
- Modify: `pom.xml` (root, add `fix-flow-ui` module)

**Steps:**

1. Create `fix-flow-ui/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-parent</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>fix-flow-ui</artifactId>
    <packaging>pom</packaging>

    <properties>
        <node.version>v20.12.2</node.version>
        <npm.version>10.5.0</npm.version>
        <frontend-maven-plugin.version>1.15.0</frontend-maven-plugin.version>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>com.github.eirslett</groupId>
                <artifactId>frontend-maven-plugin</artifactId>
                <version>${frontend-maven-plugin.version}</version>
                <configuration>
                    <installDirectory>target</installDirectory>
                    <workingDirectory>${project.basedir}</workingDirectory>
                </configuration>
                <executions>
                    <execution>
                        <id>install-node-and-npm</id>
                        <goals><goal>install-node-and-npm</goal></goals>
                        <phase>generate-resources</phase>
                        <configuration>
                            <nodeVersion>${node.version}</nodeVersion>
                            <npmVersion>${npm.version}</npmVersion>
                        </configuration>
                    </execution>
                    <execution>
                        <id>npm-install</id>
                        <goals><goal>npm</goal></goals>
                        <phase>generate-resources</phase>
                        <configuration><arguments>install --no-audit --no-fund</arguments></configuration>
                    </execution>
                    <execution>
                        <id>npm-build</id>
                        <goals><goal>npm</goal></goals>
                        <phase>generate-resources</phase>
                        <configuration><arguments>run build</arguments></configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

2. Add `fix-flow-ui` module to root `pom.xml`:
```xml
<modules>
    <module>fix-flow-engine</module>
    <module>fix-flow-persistence</module>
    <module>fix-flow-api</module>
    <module>fix-flow-ui</module>
</modules>
```

3. Modify `fix-flow-api/pom.xml` — add a dependency declaration on `fix-flow-ui` so Maven builds it first, plus a resources plugin that copies the Vite output into `target/classes/static`, and the Spring Boot fat-JAR plugin:
```xml
<dependencies>
    <!-- existing dependencies ... -->
    <dependency>
        <groupId>com.fixflow</groupId>
        <artifactId>fix-flow-ui</artifactId>
        <version>${project.version}</version>
        <type>pom</type>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-resources-plugin</artifactId>
            <version>3.3.1</version>
            <executions>
                <execution>
                    <id>copy-ui-bundle</id>
                    <phase>process-resources</phase>
                    <goals><goal>copy-resources</goal></goals>
                    <configuration>
                        <outputDirectory>${project.build.outputDirectory}/static</outputDirectory>
                        <resources>
                            <resource>
                                <directory>${project.basedir}/../fix-flow-ui/target/dist</directory>
                                <filtering>false</filtering>
                            </resource>
                        </resources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>
                <execution>
                    <goals><goal>repackage</goal></goals>
                </execution>
            </executions>
            <configuration>
                <mainClass>com.fixflow.api.FixFlowApplication</mainClass>
            </configuration>
        </plugin>
    </plugins>
</build>
```

4. Run:
```bash
mvn clean package -DskipTests
```
Expect `fix-flow-api/target/fix-flow-api-1.0.0.jar` to exist and exceed 10 MB.

5. Verify:
```bash
ls -lh fix-flow-api/target/fix-flow-api-1.0.0.jar
java -jar fix-flow-api/target/fix-flow-api-1.0.0.jar
```
Open `http://localhost:8080` — UI loads from the embedded static bundle.

6. Final commit:
```bash
git add -A
git commit -m "feat: complete FIX Flow Simulator — full system build + frontend bundled"
```

---
*End of Part 3 — see fix-flow-part1.md and fix-flow-part2.md for backend implementation*
