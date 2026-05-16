import { useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getScenarios, createScenario } from '../../api/scenarios';
import { useScenarioStore } from '../../store/scenarioStore';
import { Scenario } from '../../types';

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
        yamlDsl: '',
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scenarios'] }),
  });

  const onSelect = (s: Scenario) => {
    setActiveScenario(s);
    setNodes([]);
    setEdges([]);
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
