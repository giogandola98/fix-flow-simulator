import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  PackImportResult, PackInspection, deletePack, getPacks, importPack, inspectPack, packExportUrl,
} from '../../api/packs';
import { usePackStore } from '../../store/packStore';

const input = 'bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 text-xs text-gray-100 w-full';
const btn = 'px-2 py-1 text-xs rounded border border-[#2a2d3a] bg-[#242736] hover:bg-[#2f3347] text-gray-200 disabled:opacity-40';

interface Pending { file: File; manifest: PackInspection; values: Record<string, string>; }

/** Modal page: installed provider packs, import (with the pack's template values), export, delete. */
export default function PackPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const isOpen = usePackStore((s) => s.isOpen);
  const close = usePackStore((s) => s.close);
  const [pending, setPending] = useState<Pending | null>(null);
  const [result, setResult] = useState<PackImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const list = useQuery({ queryKey: ['packs'], queryFn: getPacks, enabled: isOpen });

  const fail = (e: unknown) =>
    setError(t('packs.failed', { message: (e as { message?: string })?.message ?? String(e) }));
  const refreshAll = () => {
    for (const key of ['packs', 'scenarios', 'datasets', 'sessions', 'dictionaries']) {
      queryClient.invalidateQueries({ queryKey: [key] });
    }
  };

  const inspectMut = useMutation({
    mutationFn: (file: File) => inspectPack(file).then((manifest) => ({ file, manifest })),
    onSuccess: ({ file, manifest }) => {
      setError(null);
      setResult(null);
      const values: Record<string, string> = {};
      for (const v of manifest.values) {
        values[v.name] = manifest.installedValues?.[v.name] ?? v.default ?? '';
      }
      setPending({ file, manifest, values });
    },
    onError: fail,
  });
  const importMut = useMutation({
    mutationFn: (p: Pending) => importPack(p.file, p.values),
    onSuccess: (r) => {
      setError(null);
      setPending(null);
      setResult(r);
      refreshAll();
    },
    onError: fail,
  });
  const deleteMut = useMutation({
    mutationFn: (id: string) => deletePack(id),
    onSuccess: () => {
      setError(null);
      refreshAll();
    },
    onError: fail,
  });

  if (!isOpen) return null;

  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) inspectMut.mutate(file);
    e.target.value = '';
  };
  const setValue = (name: string, v: string) =>
    setPending((p) => (p ? { ...p, values: { ...p.values, [name]: v } } : p));
  const missing = pending
    ? pending.manifest.values.filter((v) => v.required && !pending.values[v.name]?.trim()).map((v) => v.name)
    : [];

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center" role="dialog" aria-label={t('packs.title')}>
      <div className="bg-[#1a1d27] border border-[#2a2d3a] rounded w-[70vw] max-h-[80vh] flex flex-col">
        <div className="flex items-center gap-2 px-3 py-2 border-b border-[#2a2d3a]">
          <span className="text-sm font-semibold text-gray-100 flex-1">{t('packs.title')}</span>
          <button className={btn} onClick={() => fileRef.current?.click()}>{t('packs.import')}</button>
          <input ref={fileRef} type="file" accept=".zip,application/zip" className="hidden" data-testid="pack-file" onChange={onFile} />
          <button className={btn} onClick={close}>{t('packs.close')}</button>
        </div>
        {error && <div className="px-3 py-1 text-xs text-red-400 whitespace-pre-wrap">{error}</div>}
        <div className="flex-1 min-h-0 overflow-y-auto p-3 flex flex-col gap-3 text-xs">
          {pending && (
            <form
              className="border border-[#2a2d3a] rounded p-2 flex flex-col gap-2"
              onSubmit={(e) => { e.preventDefault(); importMut.mutate(pending); }}
            >
              <div className="text-gray-100 font-semibold">
                {pending.manifest.name} {pending.manifest.version}
                {pending.manifest.provider ? ` · ${pending.manifest.provider}` : ''}
              </div>
              {pending.manifest.description && <div className="text-gray-400">{pending.manifest.description}</div>}
              {pending.manifest.installedVersion && (
                <div className="text-amber-400">{t('packs.replaces', { version: pending.manifest.installedVersion })}</div>
              )}
              {pending.manifest.values.length === 0 && <div className="text-gray-500">{t('packs.noValues')}</div>}
              {pending.manifest.values.map((v) => (
                <div key={v.name} className="grid grid-cols-[10rem_1fr] gap-2 items-start">
                  <label htmlFor={`pack-value-${v.name}`} className="text-gray-300 font-mono pt-0.5">
                    {v.name}{v.required ? ' *' : ''}
                  </label>
                  <div>
                    <input id={`pack-value-${v.name}`} className={input} value={pending.values[v.name] ?? ''}
                      placeholder={v.default ?? ''} onChange={(e) => setValue(v.name, e.target.value)} />
                    {v.description && <div className="text-gray-500 mt-0.5">{v.description}</div>}
                  </div>
                </div>
              ))}
              {missing.length > 0 && <div className="text-gray-500">{t('packs.required', { names: missing.join(', ') })}</div>}
              <div className="flex gap-2">
                <button type="submit" className={btn} disabled={missing.length > 0 || importMut.isPending}>
                  {pending.manifest.installedVersion ? t('packs.update') : t('packs.install')}
                </button>
                <button type="button" className={btn} onClick={() => setPending(null)}>{t('packs.cancel')}</button>
              </div>
            </form>
          )}
          {result && (
            <div className="border border-[#2a2d3a] rounded p-2 text-gray-200">
              <div>
                {t('packs.imported', { id: result.packId, version: result.version })}{' '}
                {t('packs.counts', {
                  scenarios: result.scenarios, datasets: result.datasets,
                  sessions: result.sessions, dictionaries: result.dictionaries,
                })}
              </div>
              {result.warnings.map((w, i) => <div key={i} className="text-amber-400">{w}</div>)}
            </div>
          )}
          <table className="w-full text-left">
            <thead className="text-gray-500">
              <tr>
                <th className="font-normal py-1">{t('packs.name')}</th>
                <th className="font-normal">{t('packs.version')}</th>
                <th className="font-normal">{t('packs.provider')}</th>
                <th className="font-normal">{t('packs.contents')}</th>
                <th className="font-normal">{t('packs.installedAt')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(list.data ?? []).length === 0 && (
                <tr><td colSpan={6} className="py-2 text-gray-500">{t('packs.none')}</td></tr>
              )}
              {(list.data ?? []).map((p) => (
                <tr key={p.id} className="border-t border-[#2a2d3a] text-gray-200">
                  <td className="py-1">
                    <div>{p.name}</div>
                    <div className="text-gray-500 font-mono">{p.id}</div>
                  </td>
                  <td>{p.version}</td>
                  <td>{p.provider ?? ''}</td>
                  <td>{t('packs.counts', { ...p.counts })}</td>
                  <td>{new Date(p.installedAt).toLocaleString()}</td>
                  <td className="text-right whitespace-nowrap">
                    <a className={btn} href={packExportUrl(p.id)} download>{t('packs.export')}</a>{' '}
                    <button className={btn} onClick={() => {
                      if (window.confirm(t('packs.deleteConfirm', { name: p.name }))) deleteMut.mutate(p.id);
                    }}>{t('packs.delete')}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
