import { useMutation } from '@tanstack/react-query';
import { useScenarioStore } from '../store/scenarioStore';
import { useSessionStore } from '../store/sessionStore';
import { useExecutionStore } from '../store/executionStore';
import { executeScenario } from '../api/scenarios';
import { stopExecution } from '../api/executions';

export default function TopBar() {
  const activeScenario = useScenarioStore((s) => s.activeScenario);
  const isDirty = useScenarioStore((s) => s.isDirty);
  const markClean = useScenarioStore((s) => s.markClean);
  const activeSession = useSessionStore((s) => s.activeSession);
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  const executionStatus = useExecutionStore((s) => s.executionStatus);
  const setActiveExecution = useExecutionStore((s) => s.setActiveExecution);
  const updateStatus = useExecutionStore((s) => s.updateStatus);

  const runMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario || !activeSession) throw new Error('No scenario or session selected');
      return executeScenario(activeScenario.id, activeSession.id);
    },
    onSuccess: (exec) => {
      useExecutionStore.getState().reset();
      setActiveExecution(exec.executionId);
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
        className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 disabled:opacity-40 text-sm"
        onClick={() => markClean()}
        disabled={!activeScenario || !isDirty}
        title="Save (backend sync in Task 45)"
      >
        Save{isDirty ? ' •' : ''}
      </button>
      <div className="w-px h-6 bg-[#2a2d3a]" />
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Import</button>
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm">Export</button>
    </div>
  );
}
