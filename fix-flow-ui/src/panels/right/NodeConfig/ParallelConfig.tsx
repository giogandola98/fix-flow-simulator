import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface VarRow { from: string; to: string; }
interface ParallelCfg {
  targetScenarioId?: string;
  rows?: 'all' | number[];
  inputVars?: VarRow[];
  maxConcurrency?: number;
}
interface Props { node: ScenarioNode; }

export const DEFAULT_MAX_CONCURRENCY = 10;

/** "0, 2,5" -> [0, 2, 5]; anything that is not a non-negative integer is dropped. */
export function parseRowList(text: string): number[] {
  return text.split(',')
    .map((s) => s.trim())
    .filter((s) => /^\d+$/.test(s))
    .map((s) => Number(s));
}

export function ParallelConfig({ node }: Props) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allScenarios = useScenarioStore((s) => s.scenarios);
  const activeId = useScenarioStore((s) => s.activeScenario?.id);
  const liveNode = useScenarioStore((s) => s.nodes.find((n) => n.id === node.id)) ?? node;
  const cfg = (liveNode.config as ParallelCfg) ?? {};
  const inputVars: VarRow[] = cfg.inputVars ?? [];
  const listMode = Array.isArray(cfg.rows);
  const [rowsText, setRowsText] = useState(listMode ? (cfg.rows as number[]).join(', ') : '');

  const patchConfig = (patch: Partial<ParallelCfg>) => {
    const next: Record<string, unknown> = { ...cfg, ...patch };
    for (const k of Object.keys(patch)) if (next[k] === undefined) delete next[k];
    updateNode(node.id, { config: next });
  };

  const otherScenarios = allScenarios.filter((s) => s.id !== activeId);

  const updateInputVar = (i: number, patch: Partial<VarRow>) =>
    patchConfig({ inputVars: inputVars.map((r, idx) => (idx === i ? { ...r, ...patch } : r)) });
  const addInputVar = () => patchConfig({ inputVars: [...inputVars, { from: '', to: '' }] });
  const removeInputVar = (i: number) => patchConfig({ inputVars: inputVars.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={liveNode.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      <p className="text-[10px] text-gray-500">{t('nodeConfig.parallel.desc')}</p>

      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.parallel.targetScenario')}</label>
        <select data-testid="parallel-target"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.targetScenarioId ?? ''}
          onChange={(e) => patchConfig({ targetScenarioId: e.target.value || undefined })}
        >
          <option value="">{t('nodeConfig.parallel.noScenario')}</option>
          {otherScenarios.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.parallel.rows')}</label>
        <select data-testid="parallel-rows-mode"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={listMode ? 'list' : 'all'}
          onChange={(e) => {
            if (e.target.value === 'list') {
              patchConfig({ rows: parseRowList(rowsText) });
            } else {
              patchConfig({ rows: 'all' });
            }
          }}
        >
          <option value="all">{t('nodeConfig.parallel.allRows')}</option>
          <option value="list">{t('nodeConfig.parallel.selectedRows')}</option>
        </select>
        {listMode && (
          <input data-testid="parallel-rows-list" type="text"
            className="mt-1 w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono"
            placeholder="0, 2, 5"
            title={t('nodeConfig.parallel.rowsHint')}
            value={rowsText}
            onChange={(e) => {
              setRowsText(e.target.value);
              patchConfig({ rows: parseRowList(e.target.value) });
            }} />
        )}
      </div>

      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.parallel.maxConcurrency')}</label>
        <input data-testid="parallel-max-concurrency" type="number" min={1}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          placeholder={String(DEFAULT_MAX_CONCURRENCY)}
          value={cfg.maxConcurrency ?? ''}
          onChange={(e) => {
            const n = parseInt(e.target.value, 10);
            patchConfig({ maxConcurrency: Number.isFinite(n) && n >= 1 ? n : undefined });
          }} />
      </div>

      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-[10px] text-gray-500" title={t('nodeConfig.parallel.inputVarsHint')}>
            {t('nodeConfig.callScenario.inputVars')}
          </label>
          <button data-testid="parallel-add-input-var"
            className="text-[10px] px-2 py-0.5 bg-purple-700 hover:bg-purple-600 rounded"
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
                    placeholder="var:dealer"
                    value={r.from} onChange={(e) => updateInputVar(i, { from: e.target.value })} />
                </td>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    placeholder="dealer"
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

      <TimeoutConfig value={liveNode.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
