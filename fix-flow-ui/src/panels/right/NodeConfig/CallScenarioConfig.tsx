import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

interface VarRow { from: string; to: string; }
interface CallCfg {
  targetScenarioId?: string;
  inputVars?: VarRow[];
  outputVars?: VarRow[];
}
interface Props { node: ScenarioNode; }

export function CallScenarioConfig({ node }: Props) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allScenarios = useScenarioStore((s) => s.scenarios);
  const activeId = useScenarioStore((s) => s.activeScenario?.id);
  const cfg = (node.config as CallCfg) ?? {};
  const inputVars: VarRow[] = cfg.inputVars ?? [];
  const outputVars: VarRow[] = cfg.outputVars ?? [];

  const patchConfig = (patch: Partial<CallCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const otherScenarios = allScenarios.filter((s) => s.id !== activeId);

  const updateInputVar  = (i: number, patch: Partial<VarRow>) =>
    patchConfig({ inputVars: inputVars.map((r, idx) => idx === i ? { ...r, ...patch } : r) });
  const addInputVar     = () => patchConfig({ inputVars: [...inputVars, { from: '', to: '' }] });
  const removeInputVar  = (i: number) => patchConfig({ inputVars: inputVars.filter((_, idx) => idx !== i) });

  const updateOutputVar  = (i: number, patch: Partial<VarRow>) =>
    patchConfig({ outputVars: outputVars.map((r, idx) => idx === i ? { ...r, ...patch } : r) });
  const addOutputVar     = () => patchConfig({ outputVars: [...outputVars, { from: '', to: '' }] });
  const removeOutputVar  = (i: number) => patchConfig({ outputVars: outputVars.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      <div>
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.callScenario.targetScenario')}
          <span title="The scenario to execute as a sub-flow. It inherits the parent FIX session." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetScenarioId ?? ''}
          onChange={(e) => patchConfig({ targetScenarioId: e.target.value || undefined })}
        >
          <option value="">{t('nodeConfig.callScenario.noScenarios')}</option>
          {otherScenarios.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>

      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-[10px] text-gray-500">
            {t('nodeConfig.callScenario.inputVars')}
            <span title="Copy variables from the parent scenario into the child. 'From' is a parent expression (e.g. var:orderId). 'To' is the variable name in the child." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <button className="text-[10px] px-2 py-0.5 bg-purple-700 hover:bg-purple-600 rounded"
            onClick={addInputVar}>{t('nodeConfig.callScenario.addVar')}</button>
        </div>
        <table className="w-full">
          <thead className="text-[10px] text-gray-500">
            <tr>
              <th className="text-left">{t('nodeConfig.callScenario.from')}</th>
              <th className="text-left">{t('nodeConfig.callScenario.to')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {inputVars.map((r, i) => (
              <tr key={i}>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    placeholder="var:orderId"
                    value={r.from} onChange={(e) => updateInputVar(i, { from: e.target.value })} />
                </td>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    placeholder="orderId"
                    value={r.to} onChange={(e) => updateInputVar(i, { to: e.target.value })} />
                </td>
                <td>
                  <button className="text-red-400 hover:text-red-300 text-xs" onClick={() => removeInputVar(i)}>x</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-[10px] text-gray-500">
            {t('nodeConfig.callScenario.outputVars')}
            <span title="Copy variables from the child back into the parent. 'From' is the variable name in the child. 'To' is the variable name in the parent." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <button className="text-[10px] px-2 py-0.5 bg-purple-700 hover:bg-purple-600 rounded"
            onClick={addOutputVar}>{t('nodeConfig.callScenario.addVar')}</button>
        </div>
        <table className="w-full">
          <thead className="text-[10px] text-gray-500">
            <tr>
              <th className="text-left">{t('nodeConfig.callScenario.from')}</th>
              <th className="text-left">{t('nodeConfig.callScenario.to')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {outputVars.map((r, i) => (
              <tr key={i}>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    placeholder="rfqResult"
                    value={r.from} onChange={(e) => updateOutputVar(i, { from: e.target.value })} />
                </td>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    placeholder="parentResult"
                    value={r.to} onChange={(e) => updateOutputVar(i, { to: e.target.value })} />
                </td>
                <td>
                  <button className="text-red-400 hover:text-red-300 text-xs" onClick={() => removeOutputVar(i)}>x</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
