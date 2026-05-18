import { useState } from 'react';
import { NodeType } from '../../types';
import { colors } from '../../theme/colors';

interface PaletteItem {
  type: NodeType;
  label: string;
  description?: string;
}

const GROUPS: Array<{ title: string; items: PaletteItem[] }> = [
  {
    title: 'Messages',
    items: [
      { type: 'SEND_FIX', label: 'Send FIX' },
      { type: 'EXPECT_FIX', label: 'Expect FIX' },
      { type: 'VALIDATE', label: 'Validate' },
    ],
  },
  {
    title: 'Flow Control',
    items: [
      { type: 'DECISION', label: 'Decision' },
      { type: 'ROUTE_FIX', label: 'Route FIX' },
      { type: 'RETRY', label: 'Retry', description: 'Re-execute a subgraph up to N times; stops on first success.' },
      { type: 'LOOP', label: 'Loop', description: 'Execute a subgraph exactly N times; all iterations must succeed.' },
      { type: 'WAIT', label: 'Wait', description: 'Block until timeout; configurable on-timeout action (proceed / fail / jump).' },
      { type: 'DELAY', label: 'Delay', description: 'Fixed sleep (ms). Always continues on success — no branching.' },
    ],
  },
  {
    title: 'Integration',
    items: [
      { type: 'HTTP_REQUEST', label: 'HTTP Request' },
    ],
  },
  {
    title: 'Terminals',
    items: [
      { type: 'START', label: 'Start' },
      { type: 'END_PASS', label: 'End Pass' },
      { type: 'END_FAIL', label: 'End Fail' },
    ],
  },
];

export function NodePalette() {
  const [search, setSearch] = useState('');

  const onDragStart = (evt: React.DragEvent, type: NodeType) => {
    evt.dataTransfer.setData('application/fix-flow-node-type', type);
    evt.dataTransfer.effectAllowed = 'move';
  };

  const q = search.trim().toLowerCase();
  const filteredGroups = GROUPS.map((g) => ({
    ...g,
    items: q ? g.items.filter((it) => it.label.toLowerCase().includes(q) || it.type.toLowerCase().includes(q) || (it.description?.toLowerCase().includes(q) ?? false)) : g.items,
  })).filter((g) => g.items.length > 0);

  return (
    <div className="h-full flex flex-col">
      <div className="p-2 pb-0">
        <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">Palette</div>
        <input
          type="text"
          placeholder="Search blocks…"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs mb-2 placeholder-gray-600 focus:outline-none focus:border-blue-500"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto px-2 pb-2">
        {filteredGroups.map((g) => (
          <div key={g.title} className="mb-3">
            <div className="text-[10px] uppercase text-gray-400 mb-1">{g.title}</div>
            <div className="flex flex-col gap-1">
              {g.items.map((it) => (
                <div
                  key={it.type}
                  draggable
                  onDragStart={(e) => onDragStart(e, it.type)}
                  title={it.description}
                  className="px-2 py-1 rounded cursor-grab bg-[#0f1117] border text-xs hover:bg-[#22252f]"
                  style={{ borderColor: colors.node[it.type as keyof typeof colors.node] }}
                >
                  {it.label}
                </div>
              ))}
            </div>
          </div>
        ))}
        {filteredGroups.length === 0 && (
          <div className="text-xs text-gray-600 italic">No blocks match "{search}"</div>
        )}
      </div>
    </div>
  );
}
