import { useEffect, useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getScenarios, getScenario, createScenario, updateScenario, deleteScenario } from '../../api/scenarios';
import { useScenarioStore } from '../../store/scenarioStore';
import { parseFromYaml } from '../../lib/scenarioSerializer';
import { Scenario } from '../../types';

export function ScenarioList() {
  const queryClient = useQueryClient();
  const setScenarios = useScenarioStore((s) => s.setScenarios);
  const setActiveScenario = useScenarioStore((s) => s.setActiveScenario);
  const setNodes = useScenarioStore((s) => s.setNodes);
  const setEdges = useScenarioStore((s) => s.setEdges);
  const activeScenario = useScenarioStore((s) => s.activeScenario);

  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
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
      createScenario({ name: 'New Scenario', description: '', sessionRef: 'default', yamlDsl: '' }),
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
      if (full.yamlDsl) {
        const { nodes, edges } = parseFromYaml(full.yamlDsl);
        setNodes(nodes);
        setEdges(edges);
      } else {
        setNodes([]);
        setEdges([]);
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
                  title="Rename"
                  onClick={(e) => startRename(s, e)}
                >✎</button>
                <button
                  className="px-1 rounded hover:bg-red-600/50 text-[10px] text-red-400"
                  title="Delete"
                  onClick={(e) => { e.stopPropagation(); deleteMutation.mutate(s.id); }}
                >✕</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
