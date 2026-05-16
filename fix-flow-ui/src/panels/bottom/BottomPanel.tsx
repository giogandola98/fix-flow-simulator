import { useState } from 'react';
import { EventLog } from './EventLog';
import { FIXMessageLog } from './FIXMessageLog';
import { ValidationErrors } from './ValidationErrors';
import { ExecutionStats } from './ExecutionStats';

type Tab = 'events' | 'messages' | 'validation' | 'stats';

const TABS: Array<{ id: Tab; label: string }> = [
  { id: 'events', label: 'Events' },
  { id: 'messages', label: 'FIX Messages' },
  { id: 'validation', label: 'Validation Errors' },
  { id: 'stats', label: 'Statistics' },
];

export default function BottomPanel() {
  const [tab, setTab] = useState<Tab>('events');
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="bg-[#1a1d27] border-t border-[#2a2d3a] flex flex-col" style={{ height: collapsed ? 32 : 240 }}>
      <div className="h-8 flex items-center border-b border-[#2a2d3a]">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => { setTab(t.id); setCollapsed(false); }}
            className={`px-3 h-full text-xs ${tab === t.id ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-gray-200'}`}
          >
            {t.label}
          </button>
        ))}
        <button className="ml-auto px-3 text-xs text-gray-400 hover:text-gray-200" onClick={() => setCollapsed((c) => !c)}>
          {collapsed ? 'Expand' : 'Collapse'}
        </button>
      </div>
      {!collapsed && (
        <div className="flex-1 min-h-0">
          {tab === 'events' && <EventLog />}
          {tab === 'messages' && <FIXMessageLog />}
          {tab === 'validation' && <ValidationErrors />}
          {tab === 'stats' && <ExecutionStats />}
        </div>
      )}
    </div>
  );
}
