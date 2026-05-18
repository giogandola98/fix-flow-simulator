import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { EventLog } from './EventLog';
import { FIXMessageLog } from './FIXMessageLog';
import { ValidationErrors } from './ValidationErrors';
import { ExecutionStats } from './ExecutionStats';

type Tab = 'events' | 'messages' | 'validation' | 'stats';

export default function BottomPanel() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('events');
  const [collapsed, setCollapsed] = useState(false);

  const TABS: Array<{ id: Tab; labelKey: string }> = [
    { id: 'events', labelKey: 'bottomPanel.tabs.events' },
    { id: 'messages', labelKey: 'bottomPanel.tabs.messages' },
    { id: 'validation', labelKey: 'bottomPanel.tabs.validation' },
    { id: 'stats', labelKey: 'bottomPanel.tabs.stats' },
  ];

  return (
    <div className="bg-[#1a1d27] border-t border-[#2a2d3a] flex flex-col" style={{ height: collapsed ? 32 : 240 }}>
      <div className="h-8 flex items-center border-b border-[#2a2d3a]">
        {TABS.map((tabDef) => (
          <button
            key={tabDef.id}
            onClick={() => { setTab(tabDef.id); setCollapsed(false); }}
            className={`px-3 h-full text-xs ${tab === tabDef.id ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-gray-200'}`}
          >
            {t(tabDef.labelKey)}
          </button>
        ))}
        <button className="ml-auto px-3 text-xs text-gray-400 hover:text-gray-200" onClick={() => setCollapsed((c) => !c)}>
          {collapsed ? t('bottomPanel.expand') : t('bottomPanel.collapse')}
        </button>
      </div>
      {!collapsed && (
        <div className="flex-1 min-h-0">
          <div className={`h-full ${tab === 'events' ? '' : 'hidden'}`}><EventLog /></div>
          <div className={`h-full ${tab === 'messages' ? '' : 'hidden'}`}><FIXMessageLog /></div>
          <div className={`h-full ${tab === 'validation' ? '' : 'hidden'}`}><ValidationErrors /></div>
          <div className={`h-full ${tab === 'stats' ? '' : 'hidden'}`}><ExecutionStats /></div>
        </div>
      )}
    </div>
  );
}
