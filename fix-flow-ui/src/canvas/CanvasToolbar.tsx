import { useTranslation } from 'react-i18next';
import { useReactFlow } from '@xyflow/react';

export function CanvasToolbar() {
  const { t } = useTranslation();
  const { zoomIn, zoomOut, fitView } = useReactFlow();
  return (
    <div className="absolute top-2 right-2 z-10 flex gap-1 bg-[#1a1d27] border border-[#2a2d3a] rounded p-1">
      <button className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]" onClick={() => zoomIn()}>+</button>
      <button className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]" onClick={() => zoomOut()}>-</button>
      <button className="px-2 py-1 text-xs rounded hover:bg-[#2a2d3a]" onClick={() => fitView()}>{t('canvas.fit')}</button>
    </div>
  );
}
