import { useEffect, useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getScenarios, getScenario, createScenario, updateScenario, deleteScenario, duplicateScenario } from '../../api/scenarios';
import { useScenarioStore } from '../../store/scenarioStore';
import { parseFromYaml } from '../../lib/scenarioSerializer';
import { Scenario } from '../../types';

export function ScenarioList() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const setScenarios = useScenarioStore((s) => s.setScenarios);
  const setActiveScenario = useScenarioStore((s) => s.setActiveScenario);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const markDirty = useScenarioStore((s) => s.markDirty);
  const activeScenario = useScenarioStore((s) => s.activeScenario);

  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [search, setSearch] = useState('');
  const renameRef = useRef<HTMLInputElement>(null);

  const { data } = useQuery({
    queryKey: ['scenarios'],
    queryFn: getScenarios,
  });

  useEffect(() => {
    if (data) setScenarios(data);
  }, [data, setScenarios]);

  useEffect(() => {
    if (renamingId && renameRef.current) renameRef.current.focus();
  }, [renamingId]);

  const createMutation = useMutation({
    mutationFn: () =>
      createScenario({ name: t('scenarios.newScenarioName'), description: '', sessionRef: 'default', yamlDsl: '' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  });

  const renameMutation = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      updateScenario(id, { name, description: '', sessionRef: activeScenario?.sessionRef ?? 'default', yamlDsl: '' }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      if (activeScenario?.id === updated.id) setActiveScenario(updated);
      setRenamingId(null);
    },
  });

  const duplicateMutation = useMutation({
    mutationFn: (id: string) => duplicateScenario(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteScenario(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      if (activeScenario?.id === deleteMutation.variables) {
        setActiveScenario(null);
        setNodes([]);
        setEdges([]);
      }
    },
  });

  const selectMutation = useMutation({
    mutationFn: (s: Scenario) => getScenario(s.id),
    onSuccess: (full) => {
      setActiveScenario(full);
      const { nodes, edges } = full.yamlDsl ? parseFromYaml(full.yamlDsl) : { nodes: [], edges: [] };
      if (nodes.length === 0) {
        setNodes([
          { id: crypto.randomUUID(), name: 'Start', type: 'START', config: {}, position: { x: 300, y: 80 } },
          { id: crypto.randomUUID(), name: 'End', type: 'END_PASS', config: {}, position: { x: 300, y: 280 } },
        ]);
        setEdges([]);
        markDirty();
      } else {
        setNodes(nodes);
        setEdges(edges);
      }
    },
  });

  const startRename = (s: Scenario, e: React.MouseEvent) => {
    e.stopPropagation();
    setRenamingId(s.id);
    setRenameValue(s.name);
  };

  const commitRename = (id: string) => {
    const trimmed = renameValue.trim();
    if (trimmed && trimmed !== (data ?? []).find((s) => s.id === id)?.name) {
      renameMutation.mutate({ id, name: trimmed });
    } else {
      setRenamingId(null);
    }
  };

  const q = search.trim().toLowerCase();
  const filtered = (data ?? []).filter((s) => !q || s.name.toLowerCase().includes(q));

  return (
    // h-full matters: the parent is a plain block, so without it this column is as tall as its
    // content, the list below has nothing to shrink against, and a long list is simply clipped by
    // the panel's overflow-hidden with no scrollbar (issue #100). NodePalette already does this.
    <div className="h-full flex flex-col min-h-0 border-t border-[#2a2d3a]">
      <div className="p-2 pb-0">
        <div className="flex items-center justify-between mb-1">
          <div className="text-xs uppercase tracking-wider text-gray-500">{t('scenarios.title')}</div>
          <button
            className="text-xs px-2 py-0.5 rounded bg-blue-600 hover:bg-blue-500"
            onClick={() => createMutation.mutate()}
          >
            {t('scenarios.newScenario')}
          </button>
        </div>
        <input
          type="text"
          placeholder={t('scenarios.searchPlaceholder')}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs mb-2 placeholder-gray-600 focus:outline-none focus:border-blue-500"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      <div
        data-testid="scenario-list"
        className="flex-1 min-h-0 overflow-y-auto px-2 pb-2 flex flex-col gap-1"
      >
        {filtered.map((s) => (
          <div
            key={s.id}
            className={`rounded text-xs group relative ${
              activeScenario?.id === s.id
                ? 'bg-blue-700 text-white'
                : 'bg-[#0f1117] hover:bg-[#22252f] text-gray-200'
            }`}
          >
            {renamingId === s.id ? (
              <input
                ref={renameRef}
                className="w-full px-2 py-1 bg-[#0f1117] text-gray-100 outline-none rounded"
                value={renameValue}
                onChange={(e) => setRenameValue(e.target.value)}
                onBlur={() => commitRename(s.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') commitRename(s.id);
                  if (e.key === 'Escape') setRenamingId(null);
                }}
              />
            ) : (
              <button
                className="text-left w-full px-2 py-1"
                onClick={() => selectMutation.mutate(s)}
              >
                <div className="font-medium truncate">{s.name}</div>
                <div className="text-[10px] opacity-70">v{s.version} · {s.nodeCount} nodes</div>
              </button>
            )}
            {renamingId !== s.id && (
              <div className="absolute right-1 top-1 hidden group-hover:flex gap-0.5">
                <button
                  className="px-1 rounded hover:bg-white/10 text-[10px]"
                  title={t('scenarios.renameTitle')}
                  onClick={(e) => startRename(s, e)}
                >✎</button>
                <button
                  className="px-1 rounded hover:bg-white/10 text-[10px]"
                  title={t('scenarios.duplicateTitle')}
                  aria-label={t('scenarios.duplicateTitle')}
                  onClick={(e) => { e.stopPropagation(); duplicateMutation.mutate(s.id); }}
                >⧉</button>
                <button
                  className="px-1 rounded hover:bg-red-600/50 text-[10px] text-red-400"
                  title={t('scenarios.deleteTitle')}
                  onClick={(e) => { e.stopPropagation(); deleteMutation.mutate(s.id); }}
                >✕</button>
              </div>
            )}
          </div>
        ))}
        {filtered.length === 0 && (
          <div className="text-[10px] text-gray-600 italic px-1">
            {q ? t('scenarios.noMatch', { search }) : t('scenarios.noScenarios')}
          </div>
        )}
      </div>
    </div>
  );
}
