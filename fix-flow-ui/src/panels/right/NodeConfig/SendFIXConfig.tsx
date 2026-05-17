import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface FieldRow { tag: number; value: string; }
interface SendCfg { msgType?: string; fields?: FieldRow[]; }
interface Props { node: ScenarioNode; }

export function SendFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as SendCfg) ?? {};
  const fields = cfg.fields ?? [];

  const patchConfig = (patch: Partial<SendCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateField = (i: number, patch: Partial<FieldRow>) => {
    const next = fields.map((f, idx) => (idx === i ? { ...f, ...patch } : f));
    patchConfig({ fields: next });
  };
  const addField = () => patchConfig({ fields: [...fields, { tag: 0, value: '' }] });
  const removeField = (i: number) => patchConfig({ fields: fields.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">MsgType (tag 35)</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''} onChange={(e) => patchConfig({ msgType: e.target.value })} />
      </div>
      <div>
        <div className="flex items-center justify-between">
          <label className="text-[10px] text-gray-500">Fields</label>
          <button className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded" onClick={addField}>+ Field</button>
        </div>
        <table className="w-full mt-1">
          <thead className="text-[10px] text-gray-500">
            <tr><th className="text-left">Tag</th><th className="text-left">Value</th><th /></tr>
          </thead>
          <tbody>
            {fields.map((f, i) => (
              <tr key={i}>
                <td className="pr-1">
                  <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.tag} onChange={(e) => updateField(i, { tag: Number(e.target.value) })} />
                </td>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.value} onChange={(e) => updateField(i, { value: e.target.value })} />
                </td>
                <td>
                  <button className="text-red-400 hover:text-red-300 text-xs" onClick={() => removeField(i)}>x</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
