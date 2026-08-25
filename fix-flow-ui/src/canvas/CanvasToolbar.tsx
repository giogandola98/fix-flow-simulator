import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useReactFlow } from '@xyflow/react';
import { useScenarioStore } from '../store/scenarioStore';
import { exportFlowchartToPdf } from '../lib/exportFlowchartPdf';

export function CanvasToolbar() {
  const { t } = useTranslation();
  const { zoomIn, zoomOut, fitView, getNodes } = useReactFlow();
  const scenarioName = useScenarioStore((s) => s.activeScenario?.name);
  const nodeCount = useScenarioStore((s) => s.nodes.length);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleExportPdf = async () => {
    // The viewport element carries the nodes and edges; the controls and this toolbar live
    // outside it, so they stay out of the picture.
    const viewport = document.querySelector<HTMLElement>('.react-flow__viewport');
    if (!viewport) return;
    setBusy(true);
    setError(null);
    try {
      await exportFlowchartToPdf({ viewport, nodes: getNodes(), scenarioName });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="absolute top-2 right-2 z-10 flex items-center gap-1 bg-[#1a1d27] border border-[#2a2d3a] rounded p-1">
      {error && <span className="px-2 text-[10px] text-red-400" role="alert">{error}</span>}
      <button className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]" onClick={() => zoomIn()}>+</button>
      <button className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]" onClick={() => zoomOut()}>-</button>
      <button className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]" onClick={() => fitView()}>{t('canvas.fit')}</button>
      <button
        className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a] disabled:opacity-40 disabled:hover:bg-transparent"
        title={t('canvas.exportPdfTitle')}
        disabled={busy || nodeCount === 0}
        onClick={handleExportPdf}
      >
        {busy ? t('canvas.exportPdfBusy') : t('canvas.exportPdf')}
      </button>
    </div>
  );
}
