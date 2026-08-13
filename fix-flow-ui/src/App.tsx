import { useCallback, useEffect, useRef, useState } from 'react';
import TopBar from './components/TopBar';
import FlowCanvas from './canvas/FlowCanvas';
import LeftPanel from './panels/left/LeftPanel';
import RightPanel from './panels/right/RightPanel';
import BottomPanel from './panels/bottom/BottomPanel';
import { useExecutionStore } from './store/executionStore';
import { useExecutionSubscription } from './hooks/useExecutionSubscription';

const RIGHT_WIDTH_KEY = 'fix-right-panel-width';
const MIN_RIGHT = 280;
const MAX_RIGHT = 720;
const DEFAULT_RIGHT = 320;

const clampWidth = (w: number) => Math.min(MAX_RIGHT, Math.max(MIN_RIGHT, w));

export default function App() {
  const activeExecutionId = useExecutionStore((s) => s.activeExecutionId);
  useExecutionSubscription(activeExecutionId);

  const [rightWidth, setRightWidth] = useState<number>(() => {
    const saved = Number(sessionStorage.getItem(RIGHT_WIDTH_KEY));
    return saved ? clampWidth(saved) : DEFAULT_RIGHT;
  });
  const dragging = useRef(false);

  const onPointerDown = useCallback((e: React.PointerEvent) => {
    dragging.current = true;
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    e.preventDefault();
  }, []);

  const onPointerMove = useCallback((e: React.PointerEvent) => {
    if (!dragging.current) return;
    // Handle sits at the left edge of the right panel; width grows as pointer moves left.
    const next = clampWidth(window.innerWidth - e.clientX);
    setRightWidth(next);
  }, []);

  const endDrag = useCallback(() => {
    dragging.current = false;
  }, []);

  useEffect(() => {
    sessionStorage.setItem(RIGHT_WIDTH_KEY, String(rightWidth));
  }, [rightWidth]);

  return (
    <div className="h-screen flex flex-col bg-[#0f1117] text-gray-100">
      <TopBar />
      <div
        className="flex-1 overflow-hidden"
        style={{
          display: 'grid',
          gridTemplateColumns: `240px 1fr 4px ${rightWidth}px`,
          gridTemplateRows: '1fr',
        }}
      >
        <LeftPanel />
        <FlowCanvas />
        <div
          role="separator"
          aria-orientation="vertical"
          title="Drag to resize panel"
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
          className="cursor-col-resize bg-[#2a2d3a] hover:bg-blue-500 transition-colors"
        />
        <RightPanel />
      </div>
      <BottomPanel />
    </div>
  );
}
