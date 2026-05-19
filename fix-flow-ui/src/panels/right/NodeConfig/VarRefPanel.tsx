import { useState } from 'react';
import { useTranslation } from 'react-i18next';

interface VarEntry {
  syntax: string;
  description: string;
  example: string;
}

const VARS: VarEntry[] = [
  { syntax: '{{now}}',                     description: 'Current UTC ISO timestamp',              example: '{{now}}' },
  { syntax: '{{now:offset:+1h}}',          description: 'Current UTC time with offset (s/m/h/d)',example: '{{now:offset:+1h}}' },
  { syntax: '{{nowdate}}',                 description: 'Current UTC date as YYYYMMDD',           example: '{{nowdate}}' },
  { syntax: '{{nowdate:offset:+1h}}',     description: 'UTC date-time with offset → YYYYMMDD-HH:MM:SS (s/m/h/d)', example: '{{nowdate:offset:+1h}}' },
  { syntax: '{{uuid}}',                    description: 'Random UUID v4',                          example: '{{uuid}}' },
  { syntax: '{{seq:name}}',               description: 'Monotonic counter keyed by name',         example: '{{seq:orderId}}' },
  { syntax: '{{env:VAR}}',                description: 'Environment variable value',              example: '{{env:SENDER_ID}}' },
  { syntax: '{{node:id:tagN}}',           description: 'Tag N value from a previous node',        example: '{{node:send-order:tag11}}' },
  { syntax: '{{node:id:tagN:offset:+5m}}',description: 'Tag value with time offset (s/m/h/d)',   example: '{{node:send-order:tag60:offset:+1h}}' },
];

export function VarRefPanel() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);

  const copy = async (example: string) => {
    try {
      await navigator.clipboard.writeText(example);
      setCopied(example);
      setTimeout(() => setCopied(null), 1500);
    } catch {
      setCopied(null);
    }
  };

  return (
    <div className="border border-[#2a2d3a] rounded">
      <button
        type="button"
        className="w-full flex items-center justify-between px-2 py-1 text-[10px] text-gray-400 hover:text-gray-300"
        onClick={() => setOpen(v => !v)}
      >
        <span>{t('nodeConfig.varRef.title')}</span>
        <span>{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="px-2 pb-2 space-y-1">
          <div className="text-[10px] text-gray-500 italic mb-1">
            {t('nodeConfig.varRef.copyHint')}
          </div>
          {VARS.map((v) => (
            <div key={v.syntax} className="flex items-start gap-2">
              <button
                type="button"
                className="shrink-0 font-mono text-[10px] px-1 py-0.5 bg-[#0f1117] border border-[#2a2d3a] rounded text-blue-400 hover:border-blue-500 hover:text-blue-300 whitespace-nowrap"
                title={`Copy: ${v.example}`}
                onClick={() => copy(v.example)}
              >
                {copied === v.example ? t('nodeConfig.varRef.copied') : v.syntax}
              </button>
              <div className="min-w-0">
                <div className="text-[10px] text-gray-400 leading-tight">{v.description}</div>
                {v.example !== v.syntax && (
                  <div className="text-[9px] text-gray-600 font-mono break-all">{v.example}</div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
