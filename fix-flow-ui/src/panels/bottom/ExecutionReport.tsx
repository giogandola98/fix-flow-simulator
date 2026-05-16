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
