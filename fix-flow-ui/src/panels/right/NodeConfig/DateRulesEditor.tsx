import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeUnit } from '../../../types';

export type DateRuleType = 'CURRENT_TIMESTAMP' | 'FIELD_OFFSET';

export interface DateRule {
  ruleId: string;
  type: DateRuleType;
  sourceNode?: string;
  sourceTag?: number;
  offsetValue?: number;
  offsetUnit?: TimeUnit;
  toleranceValue: number;
  toleranceUnit: TimeUnit;
}

const UNITS: TimeUnit[] = ['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS'];

interface Props {
  value: DateRule[];
  onChange: (next: DateRule[]) => void;
}

export function DateRulesEditor({ value, onChange }: Props) {
  const allNodes = useScenarioStore((s) => s.nodes);

  const add = () => onChange([...value, { ruleId: `dr-${Date.now()}`, type: 'CURRENT_TIMESTAMP', toleranceValue: 1, toleranceUnit: 'SECONDS' }]);
  const remove = (i: number) => onChange(value.filter((_, idx) => idx !== i));
  const update = (i: number, patch: Partial<DateRule>) => onChange(value.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  return (
    <div className="border border-[#2a2d3a] rounded p-2 mt-2">
      <div className="flex items-center justify-between mb-1">
        <div className="text-[10px] uppercase text-gray-500">Date Rules</div>
        <button className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded" onClick={add}>+ Date Rule</button>
      </div>
      <div className="space-y-2">
        {value.map((r, i) => (
          <div key={i} className="border border-[#2a2d3a] rounded p-2 space-y-1">
            <div className="flex gap-1">
              <input type="text" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.ruleId} onChange={(e) => update(i, { ruleId: e.target.value })} placeholder="ruleId" />
              <button className="text-red-400 hover:text-red-300" onClick={() => remove(i)}>x</button>
            </div>
            <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
              value={r.type} onChange={(e) => update(i, { type: e.target.value as DateRuleType })}>
              <option value="CURRENT_TIMESTAMP">CURRENT_TIMESTAMP</option>
              <option value="FIELD_OFFSET">FIELD_OFFSET</option>
            </select>
            {r.type === 'FIELD_OFFSET' && (
              <>
                <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                  value={r.sourceNode ?? ''} onChange={(e) => update(i, { sourceNode: e.target.value })}>
                  <option value="">-- source node --</option>
                  {allNodes.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
                </select>
                <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                  value={r.sourceTag ?? 0} onChange={(e) => update(i, { sourceTag: Number(e.target.value) })} placeholder="source tag" />
                <div className="flex gap-1">
                  <input type="number" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                    value={r.offsetValue ?? 0} onChange={(e) => update(i, { offsetValue: Number(e.target.value) })} placeholder="offset" />
                  <select className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                    value={r.offsetUnit ?? 'SECONDS'} onChange={(e) => update(i, { offsetUnit: e.target.value as TimeUnit })}>
                    {UNITS.map((u) => <option key={u}>{u}</option>)}
                  </select>
                </div>
              </>
            )}
            <div className="flex gap-1">
              <input type="number" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.toleranceValue} onChange={(e) => update(i, { toleranceValue: Number(e.target.value) })} placeholder="tolerance" />
              <select className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.toleranceUnit} onChange={(e) => update(i, { toleranceUnit: e.target.value as TimeUnit })}>
                {UNITS.map((u) => <option key={u}>{u}</option>)}
              </select>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
