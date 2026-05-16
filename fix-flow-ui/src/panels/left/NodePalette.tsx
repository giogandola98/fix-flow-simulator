import { NodeType } from '../../types';
import { colors } from '../../theme/colors';

interface PaletteItem {
  type: NodeType;
  label: string;
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
      { type: 'RETRY', label: 'Retry' },
      { type: 'LOOP', label: 'Loop' },
      { type: 'WAIT', label: 'Wait' },
      { type: 'DELAY', label: 'Delay' },
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
  const onDragStart = (evt: React.DragEvent, type: NodeType) => {
    evt.dataTransfer.setData('application/fix-flow-node-type', type);
    evt.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="p-2 overflow-y-auto">
      <div className="text-xs uppercase tracking-wider text-gray-500 mb-2">Palette</div>
      {GROUPS.map((g) => (
        <div key={g.title} className="mb-3">
          <div className="text-[10px] uppercase text-gray-400 mb-1">{g.title}</div>
          <div className="flex flex-col gap-1">
            {g.items.map((it) => (
              <div
                key={it.type}
                draggable
                onDragStart={(e) => onDragStart(e, it.type)}
                className="px-2 py-1 rounded cursor-grab bg-[#0f1117] border text-xs hover:bg-[#22252f]"
                style={{ borderColor: colors.node[it.type as keyof typeof colors.node] }}
              >
                {it.label}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
