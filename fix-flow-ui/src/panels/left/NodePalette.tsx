import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NodeType } from '../../types';
import { colors } from '../../theme/colors';

interface PaletteItem {
  type: NodeType;
  descKey?: string;
}

const GROUPS: Array<{ titleKey: string; items: PaletteItem[] }> = [
  {
    titleKey: 'palette.groups.messages',
    items: [
      { type: 'SEND_FIX' },
      { type: 'EXPECT_FIX' },
      { type: 'VALIDATE' },
    ],
  },
  {
    titleKey: 'palette.groups.flowControl',
    items: [
      { type: 'DECISION' },
      { type: 'ROUTE_FIX' },
      { type: 'RETRY', descKey: 'palette.descriptions.RETRY' },
      { type: 'LOOP', descKey: 'palette.descriptions.LOOP' },
      { type: 'WAIT', descKey: 'palette.descriptions.WAIT' },
      { type: 'DELAY', descKey: 'palette.descriptions.DELAY' },
    ],
  },
  {
    titleKey: 'palette.groups.integration',
    items: [
      { type: 'HTTP_REQUEST' },
    ],
  },
  {
    titleKey: 'palette.groups.composition',
    items: [
      { type: 'CALL_SCENARIO', descKey: 'palette.descriptions.CALL_SCENARIO' },
    ],
  },
  {
    titleKey: 'palette.groups.terminals',
    items: [
      { type: 'START' },
      { type: 'END_PASS' },
      { type: 'END_FAIL' },
    ],
  },
];

export function NodePalette() {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');

  const onDragStart = (evt: React.DragEvent, type: NodeType) => {
    evt.dataTransfer.setData('application/fix-flow-node-type', type);
    evt.dataTransfer.effectAllowed = 'move';
  };

  const q = search.trim().toLowerCase();
  const filteredGroups = GROUPS.map((g) => ({
    ...g,
    items: q
      ? g.items.filter((it) => {
          const label = t(`palette.nodes.${it.type}`).toLowerCase();
          const desc = it.descKey ? t(it.descKey).toLowerCase() : '';
          return label.includes(q) || it.type.toLowerCase().includes(q) || desc.includes(q);
        })
      : g.items,
  })).filter((g) => g.items.length > 0);

  return (
    <div className="h-full flex flex-col">
      <div className="p-2 pb-0">
        <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">{t('palette.title')}</div>
        <input
          type="text"
          placeholder={t('palette.searchPlaceholder')}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs mb-2 placeholder-gray-600 focus:outline-none focus:border-blue-500"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto px-2 pb-2">
        {filteredGroups.map((g) => (
          <div key={g.titleKey} className="mb-3">
            <div className="text-[10px] uppercase text-gray-400 mb-1">{t(g.titleKey)}</div>
            <div className="flex flex-col gap-1">
              {g.items.map((it) => (
                <div
                  key={it.type}
                  draggable
                  onDragStart={(e) => onDragStart(e, it.type)}
                  title={it.descKey ? t(it.descKey) : undefined}
                  className="px-2 py-1 rounded cursor-grab bg-[#0f1117] border text-xs hover:bg-[#22252f]"
                  style={{ borderColor: colors.node[it.type as keyof typeof colors.node] }}
                >
                  {t(`palette.nodes.${it.type}`)}
                </div>
              ))}
            </div>
          </div>
        ))}
        {filteredGroups.length === 0 && (
          <div className="text-xs text-gray-600 italic">{t('palette.noMatch', { search })}</div>
        )}
      </div>
    </div>
  );
}
