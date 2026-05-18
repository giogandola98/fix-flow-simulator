import { useEffect, useRef, useState } from 'react';
import { useExecutionStore } from '../../store/executionStore';

export function FIXMessageLog() {
  const messages = useExecutionStore((s) => s.messages);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [messages]);

  return (
    <div className="h-full flex flex-col">
      <div className="px-2 py-1 border-b border-[#2a2d3a] flex items-center">
        <div className="ml-auto text-[10px] text-gray-500">{messages.length} messages</div>
      </div>
      <div ref={ref} className="flex-1 overflow-y-auto px-2 py-1 font-mono text-[11px]">
        {messages.length === 0 && <div className="text-gray-500 italic">No messages</div>}
        {messages.map((m) => {
          const isExp = expanded[m.id];
          const display = isExp ? m.rawFix : m.rawFix.slice(0, 80);
          const tag35 = String(m.fields[35 as unknown as keyof typeof m.fields] ?? m.fields['35' as unknown as keyof typeof m.fields] ?? '?');
          return (
            <div key={m.id} className="py-0.5 border-b border-[#2a2d3a] cursor-pointer"
              onClick={() => setExpanded((p) => ({ ...p, [m.id]: !p[m.id] }))}>
              <div className="flex gap-2">
                <div className="text-gray-500">{new Date(m.receivedAt).toLocaleTimeString()}</div>
                <div className={`px-1.5 rounded text-[10px] ${m.direction === 'INBOUND' ? 'bg-green-700' : 'bg-blue-700'}`}>
                  {m.direction === 'INBOUND' ? 'IN' : 'OUT'}
                </div>
                <div className="text-amber-300">35={tag35}</div>
                <div className="text-gray-300 truncate">{display}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
