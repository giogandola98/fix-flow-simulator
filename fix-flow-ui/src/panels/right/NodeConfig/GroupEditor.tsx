import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { FieldTable, FieldRow } from './FieldTable';
import { GROUP_COUNTER_TAGS } from '../../../lib/fixTags';

export interface GroupEntry { fields: FieldRow[]; groups?: GroupSpec[] }
export interface GroupSpec { counterTag: number; entries: GroupEntry[] }

export interface GroupEditorProps {
  groups: GroupSpec[];
  onChange: (next: GroupSpec[]) => void;
  depth?: number;
  idPrefix?: string;
}

const MAX_DEPTH = 3;

export function GroupEditor({ groups, onChange, depth = 0, idPrefix = 'grp' }: GroupEditorProps) {
  const { t } = useTranslation();
  const [adding, setAdding] = useState(false);
  const [newCounter, setNewCounter] = useState('');
  const [collapsed, setCollapsed] = useState<Record<number, boolean>>({});
  // Per-entry "create the first sub-group" flow, keyed by `${gi}-${ei}`.
  // Kept here (not delegated to the nested GroupEditor's own add-group state)
  // so the confirm control appears immediately after "+ Add sub-group" —
  // no extra click into the not-yet-existent nested editor is required.
  const [subAdding, setSubAdding] = useState<Record<string, boolean>>({});
  const [subCounter, setSubCounter] = useState<Record<string, string>>({});

  const patchGroup = (gi: number, patch: Partial<GroupSpec>) =>
    onChange(groups.map((g, i) => (i === gi ? { ...g, ...patch } : g)));

  const patchEntries = (gi: number, next: GroupEntry[]) => patchGroup(gi, { entries: next });

  const confirmAdd = () => {
    const tag = Number(newCounter);
    if (!Number.isInteger(tag) || tag <= 0) return;
    onChange([...groups, { counterTag: tag, entries: [{ fields: [] }] }]);
    setNewCounter('');
    setAdding(false);
  };

  const move = (gi: number, ei: number, delta: number) => {
    const entries = [...groups[gi].entries];
    const target = ei + delta;
    if (target < 0 || target >= entries.length) return;
    [entries[ei], entries[target]] = [entries[target], entries[ei]];
    patchEntries(gi, entries);
  };

  const entryKey = (gi: number, ei: number) => `${gi}-${ei}`;

  const confirmSubAdd = (gi: number, ei: number) => {
    const k = entryKey(gi, ei);
    const tag = Number(subCounter[k] ?? '');
    if (!Number.isInteger(tag) || tag <= 0) return;
    patchEntries(gi, groups[gi].entries.map((e, i) => (i === ei
      ? { ...e, groups: [{ counterTag: tag, entries: [{ fields: [] }] }] }
      : e)));
    setSubAdding((s) => ({ ...s, [k]: false }));
    setSubCounter((s) => ({ ...s, [k]: '' }));
  };

  return (
    <div className={depth > 0 ? 'pl-2 border-l border-[#2a2d3a]' : ''}>
      <div className="flex items-center justify-between mt-2">
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.sendFix.groups', 'Repeating Groups')}
          <span
            title="FIX repeating groups. The counter tag (e.g. 555 NoLegs) is derived from the number of entries and written by the engine — never type it as a field."
            className="ml-1 text-gray-600 cursor-help"
          >?</span>
        </label>
        <button
          type="button"
          data-testid={`${idPrefix}-add-group`}
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={() => setAdding((v) => !v)}
        >
          {t('nodeConfig.sendFix.addGroup', '+ Add group')}
        </button>
      </div>

      {depth >= MAX_DEPTH && (
        <div className="text-[10px] text-gray-600 italic mt-1">
          {t('nodeConfig.sendFix.nestingLimit', 'Nesting limit reached (max depth 3)')}
        </div>
      )}

      {adding && (
        <div className="flex gap-1 mt-1">
          <input
            type="number"
            list={`${idPrefix}-counter-list`}
            data-testid={`${idPrefix}-new-counter`}
            placeholder="555"
            className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 text-xs"
            value={newCounter}
            onChange={(e) => setNewCounter(e.target.value)}
          />
          <datalist id={`${idPrefix}-counter-list`}>
            {Object.entries(GROUP_COUNTER_TAGS).map(([tag, name]) => (
              <option key={tag} value={tag}>{`${tag} — ${name}`}</option>
            ))}
          </datalist>
          <button
            type="button"
            data-testid={`${idPrefix}-confirm-group`}
            className="px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded text-[10px]"
            onClick={confirmAdd}
          >OK</button>
        </div>
      )}

      {groups.map((g, gi) => (
        <div key={gi} className="border border-[#2a2d3a] rounded mt-1">
          <div className="flex items-center gap-1 px-2 py-1 bg-[#161922]">
            <button
              type="button"
              className="text-[10px] text-gray-400"
              onClick={() => setCollapsed((c) => ({ ...c, [gi]: !c[gi] }))}
            >{collapsed[gi] ? '▼' : '▲'}</button>
            <span className="text-[10px] text-gray-300">
              {g.counterTag} — {GROUP_COUNTER_TAGS[g.counterTag] ?? 'group'} ({g.entries.length} entries)
            </span>
            <input
              readOnly
              data-testid={`${idPrefix}-counter-${gi}`}
              className="w-10 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 text-[10px] text-gray-500"
              value={g.entries.length}
              title="Derived from entry count — maintained by the engine"
            />
            <div className="flex-1" />
            <button
              type="button"
              data-testid={`${idPrefix}-add-entry-${gi}`}
              className="text-[10px] px-1.5 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
              onClick={() => patchEntries(gi, [...g.entries, { fields: [] }])}
            >{t('nodeConfig.sendFix.addEntry', '+ Entry')}</button>
            <button
              type="button"
              data-testid={`${idPrefix}-del-group-${gi}`}
              className="text-red-400 hover:text-red-300 text-xs px-1"
              onClick={() => onChange(groups.filter((_, i) => i !== gi))}
            >x</button>
          </div>

          {!collapsed[gi] && g.entries.map((entry, ei) => (
            <div key={ei} className="border-t border-[#2a2d3a] px-2 py-1">
              <div className="flex items-center gap-1">
                <span className="text-[10px] text-gray-500">#{ei + 1}</span>
                <div className="flex-1" />
                <button type="button" data-testid={`${idPrefix}-up-entry-${gi}-${ei}`}
                        className="text-[10px] text-gray-400 px-1" onClick={() => move(gi, ei, -1)}>↑</button>
                <button type="button" data-testid={`${idPrefix}-down-entry-${gi}-${ei}`}
                        className="text-[10px] text-gray-400 px-1" onClick={() => move(gi, ei, 1)}>↓</button>
                <button type="button" data-testid={`${idPrefix}-dup-entry-${gi}-${ei}`}
                        className="text-[10px] text-gray-400 px-1"
                        onClick={() => patchEntries(gi, [
                          ...g.entries.slice(0, ei + 1),
                          JSON.parse(JSON.stringify(entry)) as GroupEntry,
                          ...g.entries.slice(ei + 1),
                        ])}>⧉</button>
                <button type="button" data-testid={`${idPrefix}-del-entry-${gi}-${ei}`}
                        className="text-red-400 hover:text-red-300 text-xs px-1"
                        onClick={() => patchEntries(gi, g.entries.filter((_, i) => i !== ei))}>x</button>
              </div>

              <FieldTable
                fields={entry.fields}
                idPrefix={`${idPrefix}-${gi}-${ei}`}
                label={t('nodeConfig.sendFix.entryFields', 'Fields')}
                onChange={(next) => patchEntries(gi,
                  g.entries.map((e, i) => (i === ei ? { ...e, fields: next } : e)))}
              />

              {depth < MAX_DEPTH && (
                <>
                  {(entry.groups?.length ?? 0) === 0 && !subAdding[entryKey(gi, ei)] && (
                    <button
                      type="button"
                      data-testid={`${idPrefix}-add-subgroup-${gi}-${ei}`}
                      className="text-[10px] text-blue-400 hover:text-blue-300 mt-1"
                      onClick={() => setSubAdding((s) => ({ ...s, [entryKey(gi, ei)]: true }))}
                    >{t('nodeConfig.sendFix.addSubGroup', '+ Add sub-group')}</button>
                  )}
                  {(entry.groups?.length ?? 0) === 0 && subAdding[entryKey(gi, ei)] && (
                    <div className="flex gap-1 mt-1">
                      <input
                        type="number"
                        list={`${idPrefix}-${gi}-${ei}-sub-counter-list`}
                        data-testid={`${idPrefix}-${gi}-${ei}-sub-new-counter`}
                        placeholder="555"
                        className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 text-xs"
                        value={subCounter[entryKey(gi, ei)] ?? ''}
                        onChange={(e) => setSubCounter((s) => ({ ...s, [entryKey(gi, ei)]: e.target.value }))}
                      />
                      <datalist id={`${idPrefix}-${gi}-${ei}-sub-counter-list`}>
                        {Object.entries(GROUP_COUNTER_TAGS).map(([tag, name]) => (
                          <option key={tag} value={tag}>{`${tag} — ${name}`}</option>
                        ))}
                      </datalist>
                      <button
                        type="button"
                        data-testid={`${idPrefix}-${gi}-${ei}-sub-confirm-group`}
                        className="px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded text-[10px]"
                        onClick={() => confirmSubAdd(gi, ei)}
                      >OK</button>
                    </div>
                  )}
                  {entry.groups && entry.groups.length > 0 && (
                    <GroupEditor
                      groups={entry.groups}
                      depth={depth + 1}
                      idPrefix={`${idPrefix}-${gi}-${ei}-sub`}
                      onChange={(next) => patchEntries(gi,
                        g.entries.map((e, i) => (i === ei ? { ...e, groups: next } : e)))}
                    />
                  )}
                </>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
