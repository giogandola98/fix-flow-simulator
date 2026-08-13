import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useExecutionStore } from '../../store/executionStore';

const TYPE_COLORS: Record<string, string> = {
  NODE_ENTERED: 'bg-blue-700',
  NODE_EXITED: 'bg-green-700',
  ERROR: 'bg-red-700',
  EXECUTION_STARTED: 'bg-blue-700',
  EXECUTION_FINISHED: 'bg-green-700',
  MESSAGE_SENT: 'bg-cyan-700',
  MESSAGE_RECEIVED: 'bg-purple-700',
  TIMEOUT: 'bg-amber-700',
  SESSION_UP: 'bg-green-700',
  SESSION_DOWN: 'bg-red-700',
};

export function EventLog() {
  const { t } = useTranslation();
  const events = useExecutionStore((s) => s.events);
  const ref = useRef<HTMLDivElement>(null);
  // Only auto-scroll when the user was already near the bottom, so scrolling up is not yanked.
  const nearBottom = useRef(true);

  const onScroll = () => {
    const el = ref.current;
    if (el) nearBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
  };

  useEffect(() => {
    if (ref.current && nearBottom.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [events]);

  return (
    <div ref={ref} onScroll={onScroll} className="h-full overflow-y-auto px-2 py-1 font-mono text-[11px]">
      {events.length === 0 && <div className="text-gray-500 italic">{t('events.noEvents')}</div>}
      {events.map((e) => (
        <div key={e.id} className="flex gap-2 py-0.5 border-b border-[#2a2d3a]">
          <div className="text-gray-500">{new Date(e.timestamp).toLocaleTimeString()}</div>
          <div className={`px-1.5 rounded text-[10px] uppercase ${TYPE_COLORS[e.type] ?? 'bg-gray-700'}`}>
            {e.type}
          </div>
          <div className="text-blue-300">{e.nodeId ?? '-'}</div>
          <div className="text-gray-300 truncate">{e.detail ?? ''}</div>
        </div>
      ))}
    </div>
  );
}
