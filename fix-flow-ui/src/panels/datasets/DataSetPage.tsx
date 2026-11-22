import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  DataSet, createDataSet, dataSetExportUrl, deleteDataSet, getDataSet, getDataSets, importDataSet, updateDataSet,
} from '../../api/datasets';
import { useDataSetStore } from '../../store/dataSetStore';

interface Draft { name: string; columns: string[]; rows: string[][]; }

const cell = 'bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 text-xs text-gray-100 w-full min-w-[80px]';
const btn = 'px-2 py-1 text-xs rounded border border-[#2a2d3a] bg-[#242736] hover:bg-[#2f3347] text-gray-200 disabled:opacity-40';

const toDraft = (d: DataSet): Draft => ({
  name: d.name,
  columns: [...d.columns],
  rows: (d.rows ?? []).map((r) => [...r]),
});

/** Modal page: list of data sets, table editor, CSV import/export. Opened from the left panel. */
export default function DataSetPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const isOpen = useDataSetStore((s) => s.isOpen);
  const close = useDataSetStore((s) => s.close);
  const selectedId = useDataSetStore((s) => s.selectedId);
  const select = useDataSetStore((s) => s.select);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const list = useQuery({ queryKey: ['datasets'], queryFn: getDataSets, enabled: isOpen });
  const detail = useQuery({
    queryKey: ['datasets', selectedId],
    queryFn: () => getDataSet(selectedId as string),
    enabled: isOpen && !!selectedId,
  });

  useEffect(() => {
    if (detail.data) setDraft(toDraft(detail.data));
  }, [detail.data]);

  const fail = (e: unknown) =>
    setError(t('datasets.failed', { message: (e as { message?: string })?.message ?? String(e) }));
  const refresh = (saved: DataSet) => {
    setError(null);
    queryClient.invalidateQueries({ queryKey: ['datasets'] });
    select(saved.id);
    setDraft(toDraft(saved));
  };

  const importMut = useMutation({
    mutationFn: (file: File) => importDataSet(file, file.name.replace(/\.csv$/i, '')),
    onSuccess: refresh, onError: fail,
  });
  const createMut = useMutation({
    mutationFn: () => createDataSet({
      name: `${t('datasets.newName')}-${Date.now().toString(36)}`, columns: [t('datasets.newColumn')], rows: [],
    }),
    onSuccess: refresh, onError: fail,
  });
  const saveMut = useMutation({
    mutationFn: (d: Draft) => updateDataSet(selectedId as string, d),
    onSuccess: refresh, onError: fail,
  });
  const deleteMut = useMutation({
    mutationFn: (id: string) => deleteDataSet(id),
    onSuccess: () => {
      setError(null);
      select(null);
      setDraft(null);
      queryClient.invalidateQueries({ queryKey: ['datasets'] });
    },
    onError: fail,
  });

  if (!isOpen) return null;

  const update = (fn: (d: Draft) => Draft) => setDraft((d) => (d ? fn(d) : d));
  const setCell = (r: number, c: number, v: string) =>
    update((d) => ({ ...d, rows: d.rows.map((row, i) => (i === r ? row.map((x, j) => (j === c ? v : x)) : row)) }));
  const setColumn = (c: number, v: string) =>
    update((d) => ({ ...d, columns: d.columns.map((x, j) => (j === c ? v : x)) }));
  const addRow = () => update((d) => ({ ...d, rows: [...d.rows, d.columns.map(() => '')] }));
  const removeRow = (r: number) => update((d) => ({ ...d, rows: d.rows.filter((_, i) => i !== r) }));
  const addColumn = () =>
    update((d) => {
      let n = d.columns.length + 1;
      while (d.columns.includes(`${t('datasets.newColumn')}${n}`)) n++;
      return { ...d, columns: [...d.columns, `${t('datasets.newColumn')}${n}`], rows: d.rows.map((r) => [...r, '']) };
    });
  const removeColumn = (c: number) =>
    update((d) => ({
      ...d,
      columns: d.columns.filter((_, j) => j !== c),
      rows: d.rows.map((r) => r.filter((_, j) => j !== c)),
    }));

  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) importMut.mutate(file);
    e.target.value = '';
  };

  const selected = list.data?.find((d) => d.id === selectedId);

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center" role="dialog" aria-label={t('datasets.title')}>
      <div className="bg-[#1a1d27] border border-[#2a2d3a] rounded w-[90vw] h-[80vh] flex flex-col">
        <div className="flex items-center gap-2 px-3 py-2 border-b border-[#2a2d3a]">
          <span className="text-sm font-semibold text-gray-100 flex-1">{t('datasets.title')}</span>
          <button className={btn} onClick={() => createMut.mutate()}>{t('datasets.new')}</button>
          <button className={btn} onClick={() => fileRef.current?.click()}>{t('datasets.import')}</button>
          <input ref={fileRef} type="file" accept=".csv,text/csv" className="hidden" data-testid="dataset-file" onChange={onFile} />
          <button className={btn} onClick={close}>{t('datasets.close')}</button>
        </div>
        {error && <div className="px-3 py-1 text-xs text-red-400">{error}</div>}
        <div className="flex flex-1 min-h-0">
          <ul className="w-56 border-r border-[#2a2d3a] overflow-y-auto text-xs">
            {(list.data ?? []).length === 0 && <li className="p-3 text-gray-500">{t('datasets.none')}</li>}
            {(list.data ?? []).map((d) => (
              <li key={d.id}>
                <button
                  className={`w-full text-left px-3 py-1.5 hover:bg-[#242736] ${d.id === selectedId ? 'bg-[#242736] text-white' : 'text-gray-300'}`}
                  onClick={() => select(d.id)}
                >
                  <div>{d.name}</div>
                  <div className="text-gray-500">
                    {t('datasets.rows', { count: d.rowCount ?? 0 })}
                    {d.packId ? ` · ${t('datasets.pack', { pack: d.packId })}` : ''}
                  </div>
                </button>
              </li>
            ))}
          </ul>
          <div className="flex-1 min-w-0 flex flex-col p-3 gap-2">
            {!draft || !selectedId ? (
              <div className="text-xs text-gray-500">{t('datasets.select')}</div>
            ) : (
              <>
                <div className="flex items-center gap-2">
                  <label className="text-xs text-gray-400" htmlFor="dataset-name">{t('datasets.name')}</label>
                  <input id="dataset-name" className={`${cell} max-w-xs`} value={draft.name}
                    onChange={(e) => update((d) => ({ ...d, name: e.target.value }))} />
                  <span className="flex-1" />
                  <button className={btn} onClick={addRow}>{t('datasets.addRow')}</button>
                  <button className={btn} onClick={addColumn}>{t('datasets.addColumn')}</button>
                  <button className={btn} onClick={() => saveMut.mutate(draft)}>{t('datasets.save')}</button>
                  <a className={btn} href={dataSetExportUrl(selectedId)} download>{t('datasets.export')}</a>
                  <button className={btn} onClick={() => {
                    if (window.confirm(t('datasets.deleteConfirm', { name: selected?.name ?? draft.name }))) {
                      deleteMut.mutate(selectedId);
                    }
                  }}>{t('datasets.delete')}</button>
                </div>
                <div className="flex-1 overflow-auto">
                  <table className="border-separate border-spacing-1">
                    <thead>
                      <tr>
                        <th />
                        {draft.columns.map((c, ci) => (
                          <th key={ci} className="font-normal">
                            <div className="flex gap-1">
                              <input className={cell} aria-label={t('datasets.renameColumn')} value={c}
                                onChange={(e) => setColumn(ci, e.target.value)} />
                              <button className="text-gray-500 hover:text-red-400" title={t('datasets.removeColumn')}
                                aria-label={t('datasets.removeColumn')} onClick={() => removeColumn(ci)}>×</button>
                            </div>
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {draft.rows.map((row, ri) => (
                        <tr key={ri}>
                          <td>
                            <button className="text-gray-500 hover:text-red-400" title={t('datasets.removeRow')}
                              aria-label={t('datasets.removeRow')} onClick={() => removeRow(ri)}>×</button>
                          </td>
                          {row.map((v, ci) => (
                            <td key={ci}>
                              <input className={cell} aria-label={`r${ri + 1}c${ci + 1}`} value={v}
                                onChange={(e) => setCell(ri, ci, e.target.value)} />
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
