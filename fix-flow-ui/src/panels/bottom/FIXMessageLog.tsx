import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useExecutionStore } from '../../store/executionStore';

type Separator = 'pipe' | 'soh';

function toRaw(raw: string, sep: Separator): string {
  const normalized = raw.replace(/\x01/g, '|');
  if (sep === 'soh') return normalized.replace(/\|/g, '\x01');
  return normalized;
}

export function FIXMessageLog() {
  const { t } = useTranslation();
  const messages = useExecutionStore((s) => s.messages);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [copied, setCopied] = useState<string | null>(null);
  const [sep, setSep] = useState<Separator>('pipe');
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [messages]);

  const copyText = async (text: string, key: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(key);
      setTimeout(() => setCopied(null), 1500);
    } catch { /* ignore */ }
  };

  const exportAll = () => {
    const lines = messages.map((m) => {
      const tag35 = String(m.fields?.[35] ?? m.fields?.['35'] ?? '?');
      return `${m.receivedAt}\t${m.direction}\t35=${tag35}\t${toRaw(m.rawFix, sep)}`;
    });
    const blob = new Blob([lines.join('\n')], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fix-messages.txt';
    a.click();
    URL.revokeObjectURL(url);
  };

  const copyAll = async () => {
    const lines = messages.map((m) => {
      const tag35 = String(m.fields?.[35] ?? m.fields?.['35'] ?? '?');
      return `${m.receivedAt}\t${m.direction}\t35=${tag35}\t${toRaw(m.rawFix, sep)}`;
    });
    await copyText(lines.join('\n'), '__all__');
  };

  return (
    <div className="h-full flex flex-col">
      <div className="px-2 py-1 border-b border-[#2a2d3a] flex items-center gap-2">
        <div className="text-[10px] text-gray-500">{t('messages.count', { count: messages.length })}</div>
        <div className="flex items-center gap-1 ml-2">
          <span className="text-[9px] text-gray-600">SEP:</span>
          <button
            onClick={() => setSep('pipe')}
            className={`text-[9px] px-1 rounded ${sep === 'pipe' ? 'bg-blue-700 text-white' : 'text-gray-500 hover:text-gray-300'}`}
          >|</button>
          <button
            onClick={() => setSep('soh')}
            className={`text-[9px] px-1 rounded ${sep === 'soh' ? 'bg-blue-700 text-white' : 'text-gray-500 hover:text-gray-300'}`}
          >SOH</button>
        </div>
        <div className="ml-auto flex gap-1">
          {messages.length > 0 && (
            <>
              <button
                onClick={copyAll}
                className="text-[9px] px-1.5 py-0.5 rounded bg-[#2a2d3a] hover:bg-[#3a3d4a] text-gray-400"
              >{copied === '__all__' ? '✓' : t('messages.copyAll', { defaultValue: 'Copy All' })}</button>
              <button
                onClick={exportAll}
                className="text-[9px] px-1.5 py-0.5 rounded bg-[#2a2d3a] hover:bg-[#3a3d4a] text-gray-400"
              >{t('messages.export', { defaultValue: 'Export' })}</button>
            </>
          )}
        </div>
      </div>
      <div ref={ref} className="flex-1 overflow-y-auto px-2 py-1 font-mono text-[11px]">
        {messages.length === 0 && <div className="text-gray-500 italic">{t('messages.noMessages')}</div>}
        {messages.map((m) => {
          const isExp = expanded[m.id];
          const raw = toRaw(m.rawFix, sep);
          const display = isExp ? raw : raw.slice(0, 80);
          const tag35 = String(m.fields?.[35 as unknown as keyof typeof m.fields] ?? m.fields?.['35' as unknown as keyof typeof m.fields] ?? '?');
          return (
            <div key={m.id} className="py-0.5 border-b border-[#2a2d3a]">
              <div className="flex gap-2 items-center">
                <div
                  className="flex-1 flex gap-2 items-center cursor-pointer"
                  onClick={() => setExpanded((p) => ({ ...p, [m.id]: !p[m.id] }))}
                >
                  <div className="text-gray-500 shrink-0">{new Date(m.receivedAt).toLocaleTimeString()}</div>
                  <div className={`px-1.5 rounded text-[10px] shrink-0 ${m.direction === 'INBOUND' ? 'bg-green-700' : 'bg-blue-700'}`}>
                    {m.direction === 'INBOUND' ? 'IN' : 'OUT'}
                  </div>
                  <div className="text-amber-300 shrink-0">35={tag35}</div>
                  <div className="text-gray-300 truncate">{display}</div>
                </div>
                <button
                  className="shrink-0 text-[9px] px-1 py-0.5 rounded bg-[#2a2d3a] hover:bg-[#3a3d4a] text-gray-400"
                  onClick={() => copyText(raw, m.id)}
                  title="Copy raw FIX message"
                >{copied === m.id ? '✓' : 'copy'}</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
