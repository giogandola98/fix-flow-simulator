import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useScenarioStore } from '../store/scenarioStore';
import { useSessionStore } from '../store/sessionStore';
import { useExecutionStore } from '../store/executionStore';
import { executeScenario, updateScenario, importScenario } from '../api/scenarios';
import { stopExecution } from '../api/executions';
import { serializeToYaml, parseFromYaml } from '../lib/scenarioSerializer';

const LANGUAGES = [
  { code: 'en', label: 'EN' },
  { code: 'it', label: 'IT' },
  { code: 'fr', label: 'FR' },
];

export default function TopBar() {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const activeScenario = useScenarioStore((s) => s.activeScenario);
  const setActiveScenario = useScenarioStore((s) => s.setActiveScenario);
  const isDirty = useScenarioStore((s) => s.isDirty);
  const markClean = useScenarioStore((s) => s.markClean);
  const nodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const activeSession = useSessionStore((s) => s.activeSession);
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  const executionStatus = useExecutionStore((s) => s.executionStatus);
  const updateStatus = useExecutionStore((s) => s.updateStatus);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const importRef = useRef<HTMLInputElement>(null);

  const handleExport = () => {
    if (!activeScenario) return;
    const yamlDsl = serializeToYaml(nodes, edges, {
      id: activeScenario.id,
      name: activeScenario.name,
      description: activeScenario.description,
      version: activeScenario.version,
      sessionRef: activeScenario.sessionRef,
    });
    const blob = new Blob([yamlDsl], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${activeScenario.name}.yaml`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const importMutation = useMutation({
    mutationFn: (file: File) => importScenario(file),
    onSuccess: (saved) => {
      setActiveScenario(saved);
      const { nodes: n, edges: ed } = saved.yamlDsl
        ? parseFromYaml(saved.yamlDsl)
        : { nodes: [], edges: [] };
      setNodes(n);
      setEdges(ed);
      markClean();
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      setErrorMsg(null);
    },
    onError: (err: unknown) => {
      const msg = (err as { message?: string })?.message ?? String(err);
      setErrorMsg(`Import failed: ${msg}`);
    },
  });

  const handleImportFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    importMutation.mutate(file);
    e.target.value = '';
  };

  const runMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario || !activeSession) throw new Error('No scenario or session selected');
      return executeScenario(activeScenario.id, activeSession.id);
    },
    onSuccess: (exec) => {
      setErrorMsg(null);
      // Atomic reset + new ID in one update — prevents null intermediate state
      // that would cause useExecutionSubscription to briefly unsubscribe
      useExecutionStore.setState({
        activeExecutionId: exec.executionId,
        executionStatus: 'RUNNING',
        events: [],
        messages: [],
        seenEventIds: new Set<string>(),
        seenMsgIds: new Set<string>(),
        nodeStatuses: {},
        startedAt: null,
        endedAt: null,
      });
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
  const currentLang = i18n.language?.slice(0, 2) ?? 'en';

  return (
    <div className="h-12 bg-[#1a1d27] border-b border-[#2a2d3a] flex items-center px-4 gap-4">
      <div className="font-semibold text-blue-400">{t('topbar.appName')}</div>
      <div className="text-sm text-gray-400">{activeScenario?.name ?? t('topbar.noScenario')}</div>
      {errorMsg && (
        <div className="text-xs text-red-400 bg-red-900/40 border border-red-700 rounded px-2 py-0.5 max-w-xs truncate"
          title={errorMsg} onClick={() => setErrorMsg(null)}>
          ✕ {errorMsg}
        </div>
      )}
      <div className="flex-1" />
      {/* Language switcher */}
      <div className="flex items-center gap-0.5">
        {LANGUAGES.map((l) => (
          <button
            key={l.code}
            onClick={() => i18n.changeLanguage(l.code)}
            className={`px-1.5 py-0.5 rounded text-[10px] font-medium transition-colors ${
              currentLang === l.code
                ? 'bg-blue-600 text-white'
                : 'text-gray-400 hover:text-gray-200 hover:bg-[#2a2d3a]'
            }`}
          >
            {l.label}
          </button>
        ))}
      </div>
      <div className="w-px h-6 bg-[#2a2d3a]" />
      <button
        className="px-3 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40 text-sm"
        onClick={() => {
          if (!activeScenario || !activeSession?.connected) return;
          if (isDirty) {
            saveMutation.mutate(undefined, { onSuccess: () => runMutation.mutate() });
          } else {
            runMutation.mutate();
          }
        }}
        disabled={!activeScenario || !activeSession || !activeSession.connected || isRunning}
      >
        {t('topbar.run')}
      </button>
      <button
        className="px-3 py-1 rounded bg-red-600 hover:bg-red-500 disabled:opacity-40 text-sm"
        onClick={() => stopMutation.mutate()}
        disabled={!isRunning}
      >
        {t('topbar.stop')}
      </button>
      <button
        className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 disabled:opacity-40 text-sm"
        onClick={() => saveMutation.mutate()}
        disabled={!activeScenario || !isDirty}
      >
        {t('topbar.save')}{isDirty ? ' •' : ''}
      </button>
      <div className="w-px h-6 bg-[#2a2d3a]" />
      <input ref={importRef} type="file" accept=".yaml,.yml" className="hidden" onChange={handleImportFile} />
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 disabled:opacity-40 text-sm"
        disabled={importMutation.isPending}
        onClick={() => importRef.current?.click()}>
        {importMutation.isPending ? t('topbar.importing') : t('topbar.import')}
      </button>
      <button className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600 disabled:opacity-40 text-sm"
        disabled={!activeScenario} onClick={handleExport}>{t('topbar.export')}</button>
    </div>
  );
}
