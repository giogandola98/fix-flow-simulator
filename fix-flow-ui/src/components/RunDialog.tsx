import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getDataSet, getDataSets } from '../api/datasets';
import type { ExecuteInputs } from '../api/scenarios';

interface Props {
  /** The scenario's datasetRef (a data set name or id); preselected when it matches. */
  defaultDatasetRef?: string;
  onRun: (inputs: ExecuteInputs) => void;
  onClose: () => void;
}

interface VarRow { key: string; value: string }

const PREVIEW_COLUMNS = 3;

/** Asks for the inputs of one run: data set and row (or all rows), and extra variables. */
export default function RunDialog({ defaultDatasetRef, onRun, onClose }: Props) {
  const { t } = useTranslation();
  const [datasetId, setDatasetId] = useState('');
  const [rowIndex, setRowIndex] = useState(0);
  const [allRows, setAllRows] = useState(false);
  const [vars, setVars] = useState<VarRow[]>([]);

  const list = useQuery({ queryKey: ['datasets'], queryFn: getDataSets });
  const full = useQuery({
    queryKey: ['datasets', datasetId],
    queryFn: () => getDataSet(datasetId),
    enabled: !!datasetId,
  });

  // Preselect the scenario's own data set once the list is known.
  useEffect(() => {
    if (!defaultDatasetRef || !list.data) return;
    const match = list.data.find((d) => d.id === defaultDatasetRef || d.name === defaultDatasetRef);
    if (match) setDatasetId((cur) => cur || match.id);
  }, [defaultDatasetRef, list.data]);

  const rows = full.data?.rows ?? [];
  const columns = full.data?.columns ?? [];

  const rowLabel = (i: number) =>
    `${i}: ${rows[i].slice(0, PREVIEW_COLUMNS).map((v, c) => `${columns[c]}=${v}`).join(', ')}`;

  const submit = () => {
    const inputs: ExecuteInputs = {};
    if (datasetId) {
      inputs.datasetId = datasetId;
      if (allRows) inputs.allRows = true;
      else inputs.rowIndex = rowIndex;
    }
    const filled = vars.filter((v) => v.key.trim() !== '');
    if (filled.length > 0) {
      inputs.vars = Object.fromEntries(filled.map((v) => [v.key.trim(), v.value]));
    }
    onRun(inputs);
  };

  const field = 'bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-sm';

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center" role="dialog"
      aria-label={t('runDialog.title')}>
      <div className="bg-[#1a1d27] border border-[#2a2d3a] rounded p-4 w-[32rem] max-w-full flex flex-col gap-3 text-sm">
        <div className="font-semibold">{t('runDialog.title')}</div>

        <label className="flex flex-col gap-1">
          {t('runDialog.dataset')}
          <select className={field} value={datasetId}
            onChange={(e) => { setDatasetId(e.target.value); setRowIndex(0); }}>
            <option value="">{t('runDialog.noDataset')}</option>
            {(list.data ?? []).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </label>

        {datasetId && (
          <>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={allRows} onChange={(e) => setAllRows(e.target.checked)} />
              {t('runDialog.allRows')}
            </label>
            {!allRows && rows.length > 0 && (
              <label className="flex flex-col gap-1">
                {t('runDialog.row')}
                <select className={field} value={rowIndex} onChange={(e) => setRowIndex(Number(e.target.value))}>
                  {rows.map((_, i) => <option key={i} value={i}>{rowLabel(i)}</option>)}
                </select>
              </label>
            )}
            {allRows && rows.length > 0 && (
              <div className="text-xs text-gray-400">{t('runDialog.allRowsHint', { count: rows.length })}</div>
            )}
          </>
        )}

        <div className="flex flex-col gap-1">
          {vars.map((v, i) => (
            <div key={i} className="flex gap-2">
              <input aria-label={t('runDialog.varName')} className={`${field} flex-1`} value={v.key}
                onChange={(e) => setVars(vars.map((r, j) => (j === i ? { ...r, key: e.target.value } : r)))} />
              <input aria-label={t('runDialog.varValue')} className={`${field} flex-1`} value={v.value}
                onChange={(e) => setVars(vars.map((r, j) => (j === i ? { ...r, value: e.target.value } : r)))} />
              <button type="button" aria-label={t('runDialog.removeVar')} className="px-2 text-gray-400 hover:text-red-400"
                onClick={() => setVars(vars.filter((_, j) => j !== i))}>✕</button>
            </div>
          ))}
          <button type="button" className="self-start px-2 py-0.5 rounded bg-gray-700 hover:bg-gray-600 text-xs"
            onClick={() => setVars([...vars, { key: '', value: '' }])}>
            {t('runDialog.addVar')}
          </button>
        </div>

        <div className="flex justify-end gap-2">
          <button type="button" className="px-3 py-1 rounded bg-gray-700 hover:bg-gray-600" onClick={onClose}>
            {t('runDialog.cancel')}
          </button>
          <button type="button" className="px-3 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40"
            disabled={!!datasetId && (full.isLoading || (!allRows && rows.length === 0))} onClick={submit}>
            {t('runDialog.start')}
          </button>
        </div>
      </div>
    </div>
  );
}
