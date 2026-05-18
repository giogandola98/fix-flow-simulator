import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useExecutionStore } from '../../store/executionStore';
import { ExecutionReport } from './ExecutionReport';

export function ExecutionStats() {
  const { t } = useTranslation();
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
      if (e.type === 'NODE_ENTERED' && e.nodeId) nodeStartTimes[e.nodeId] = new Date(e.timestamp).getTime();
      if (e.type === 'NODE_EXITED' && e.nodeId) {
        nodesPassed += 1;
        if (nodeStartTimes[e.nodeId]) nodeDurations.push(new Date(e.timestamp).getTime() - nodeStartTimes[e.nodeId]);
      }
      if (e.type === 'ERROR' && e.nodeId) nodesFailed += 1;
    }
    const avgNodeMs = nodeDurations.length > 0 ? Math.round(nodeDurations.reduce((a, b) => a + b, 0) / nodeDurations.length) : 0;
    const duration = startedAt && endedAt
      ? new Date(endedAt).getTime() - new Date(startedAt).getTime()
      : startedAt ? Date.now() - new Date(startedAt).getTime() : 0;
    return { nodesPassed, nodesFailed, avgNodeMs, duration };
  }, [events, startedAt, endedAt]);

  return (
    <div className="h-full overflow-y-auto px-3 py-2 space-y-2 text-xs">
      <div className="flex justify-end">
        <ExecutionReport />
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Stat label={t('stats.status')} value={executionStatus ?? '—'} />
        <Stat label={t('stats.nodesPassed')} value={String(stats.nodesPassed)} />
        <Stat label={t('stats.nodesFailed')} value={String(stats.nodesFailed)} />
        <Stat label={t('stats.avgNodeTime')} value={`${stats.avgNodeMs} ms`} />
        <Stat label={t('stats.totalDuration')} value={`${stats.duration} ms`} />
      </div>
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
