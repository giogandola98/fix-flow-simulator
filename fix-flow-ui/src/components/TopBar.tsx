import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useScenarioStore } from '../store/scenarioStore';
import { useSessionStore } from '../store/sessionStore';
import { useExecutionStore } from '../store/executionStore';
import { executeScenario, updateScenario } from '../api/scenarios';
import { stopExecution } from '../api/executions';
import { serializeToYaml } from '../lib/scenarioSerializer';

export default function TopBar() {
  const activeScenario = useScenarioStore((s) => s.activeScenario);
  const isDirty = useScenarioStore((s) => s.isDirty);
  const markClean = useScenarioStore((s) => s.markClean);
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const activeSession = useSessionStore((s) => s.activeSession);
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  const executionStatus = useExecutionStore((s) => s.executionStatus);
  const setActiveExecution = useExecutionStore((s) => s.setActiveExecution);
  const updateStatus = useExecutionStore((s) => s.updateStatus);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const runMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario || !activeSession) throw new Error('No scenario or session selected');
      return executeScenario(activeScenario.id, activeSession.id);
    },
    onSuccess: (exec) => {
      setErrorMsg(null);
      useExecutionStore.getState().reset();
      setActiveExecution(exec.executionId);
      updateStatus('RUNNING');
    },
    onError: (err: unknown) => {
      setErrorMsg(err instanceof Error ? err.message : String(err));
    },
  });

  const stopMutation = useMutation({
    mutationFn: async () => {
      if (!activeExecutionId) throw new Error('No active execution');
      return stopExecution(activeExecutionId);
    },
    onSuccess: () => updateStatus('STOPPED'),
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario) throw new Error('No active scenario');
      const yamlDsl = serializeToYaml(nodes, edges, {
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
        yamlDsl,
      });
    },
    onSuccess: () => markClean(),
  });

  const isRunning = executionStatus === 'RUNNING';

  return (
    <div className="h-12 bg-[#1a1d27] border-b border-[#2a2d3a] flex items-center px-4 gap-4">
      <div className="font-semibold text-blue-400">FIX Flow Simulator</div>
      <div className="text-sm text-gray-400">{activeScenario?.name ?? 'No scenario loaded'}</div>
      {errorMsg && (
        <div className="text-xs text-red-400 bg-red-900/40 border border-red-700 rounded px-2 py-0.5 max-w-xs truncate"
          title={errorMsg} onClick={() => setErrorMsg(null)}>
          ✕ {errorMsg}
        </div>
      )}
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
        className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 disabled:opacity-40 text-sm"
        onClick={() => saveMutation.mutate()}
        disabled={!activeScenario || !isDirty}
      >
        Save{isDirty ? ' •' : ''}
      </button>
      <div className="w-px h-6 bg-[#2a2d3a]" />
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Import</button>
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Export</button>
    </div>
  );
}
