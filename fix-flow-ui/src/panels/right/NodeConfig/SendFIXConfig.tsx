import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';
import { parseFIXMessage, ENGINE_TAGS } from '../../../lib/parseFIXMessage';
import { fixTagName, FIX_TAGS } from '../../../lib/fixTags';
import { VarRefPanel } from './VarRefPanel';

interface FieldRow { tag: number; value: string; }
interface SendCfg { msgType?: string; fields?: FieldRow[]; }
interface Props { node: ScenarioNode; }

export function SendFIXConfig({ node }: Props) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as SendCfg) ?? {};
  const fields = cfg.fields ?? [];
  const [showPaste, setShowPaste] = useState(false);
  const [pasteRaw, setPasteRaw] = useState('');
  const [parseError, setParseError] = useState('');

  const patchConfig = (patch: Partial<SendCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateField = (i: number, patch: Partial<FieldRow>) => {
    const next = fields.map((f, idx) => (idx === i ? { ...f, ...patch } : f));
    patchConfig({ fields: next });
  };
  const addField = () => patchConfig({ fields: [...fields, { tag: 0, value: '' }] });
  const removeField = (i: number) => patchConfig({ fields: fields.filter((_, idx) => idx !== i) });

  const handleParse = () => {
    if (!pasteRaw.trim()) { setParseError('Paste a FIX message first'); return; }
    const result = parseFIXMessage(pasteRaw);
    const updates: Partial<SendCfg> = { fields: result.fields };
    if (result.msgType) updates.msgType = result.msgType;
    patchConfig(updates);
    setPasteRaw('');
    setParseError(result.skipped > 0 ? `Parsed OK — ${result.skipped} segment(s) skipped (engine-managed or malformed)` : '');
    setShowPaste(false);
  };

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.sendFix.msgType')}
          <span title="FIX tag 35 value. e.g. D = New Order Single, 8 = Execution Report, V = Market Data Request." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.msgType ?? ''} onChange={(e) => patchConfig({ msgType: e.target.value })} />
      </div>

      <div className="border border-[#2a2d3a] rounded">
        <button
          type="button"
          className="w-full flex items-center justify-between px-2 py-1 text-[10px] text-gray-400 hover:text-gray-300"
          onClick={() => setShowPaste(v => !v)}
        >
          <span>{t('nodeConfig.sendFix.pasteBtn')}</span>
          <span>{showPaste ? '▲' : '▼'}</span>
        </button>
        {showPaste && (
          <div className="px-2 pb-2 space-y-1">
            <div className="text-[10px] text-gray-500 italic">
              {t('nodeConfig.sendFix.pasteDesc')}
            </div>
            <textarea
              className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono text-[10px] resize-y"
              rows={3}
              placeholder="8=FIX.4.4|35=D|49=CLIENT|56=SERVER|11=ORD-001|55=AAPL|54=1|38=100|40=2|"
              value={pasteRaw}
              onChange={e => { setPasteRaw(e.target.value); setParseError(''); }}
            />
            {parseError && (
              <div className={`text-[10px] ${parseError.startsWith('Parsed OK') ? 'text-yellow-400' : 'text-red-400'}`}>
                {parseError}
              </div>
            )}
            <div className="flex gap-1">
              <button
                type="button"
                className="flex-1 px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded text-[10px]"
                onClick={handleParse}
              >
                {t('nodeConfig.sendFix.parseBtn')}
              </button>
              <button
                type="button"
                className="px-2 py-0.5 bg-gray-700 hover:bg-gray-600 rounded text-[10px]"
                onClick={() => { setPasteRaw(''); setParseError(''); setShowPaste(false); }}
              >
                {t('nodeConfig.sendFix.cancelBtn')}
              </button>
            </div>
          </div>
        )}
      </div>

      <div>
        <div className="flex items-center justify-between">
          <label className="text-[10px] text-gray-500">
            {t('nodeConfig.sendFix.fields')}
            <span title="FIX tag-value pairs. Tag is the integer field number. Value supports placeholders: {{now}}, {{uuid}}, {{seq:name}}, {{env:VAR}}, {{node:id:tagN}}. See Variable Reference below." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <button className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded" onClick={addField}>{t('nodeConfig.sendFix.addField')}</button>
        </div>
        <datalist id="fix-tag-list">
          {Object.entries(FIX_TAGS).map(([tag, name]) => (
            <option key={tag} value={tag}>{`${tag} — ${name}`}</option>
          ))}
        </datalist>
        <table className="w-full mt-1">
          <thead className="text-[10px] text-gray-500">
            <tr><th className="text-left w-16">{t('nodeConfig.tag')}</th><th className="text-left">{t('nodeConfig.field')}</th><th className="text-left">{t('nodeConfig.value')}</th><th /></tr>
          </thead>
          <tbody>
            {fields.map((f, i) => {
              const isRestricted = ENGINE_TAGS.has(f.tag);
              const tagName = fixTagName(f.tag);
              return (
                <tr key={i}>
                  <td className="pr-1 align-top">
                    <input
                      type="number"
                      list="fix-tag-list"
                      className={`w-full bg-[#0f1117] border rounded px-1 py-0.5 ${isRestricted ? 'border-yellow-500' : 'border-[#2a2d3a]'}`}
                      value={f.tag}
                      onChange={(e) => updateField(i, { tag: Number(e.target.value) })}
                      title={isRestricted ? `Tag ${f.tag} is session-managed by QuickFIX/J and will be ignored` : undefined}
                    />
                    {isRestricted && (
                      <div className="text-yellow-400 text-[9px] leading-tight mt-0.5">engine-managed</div>
                    )}
                  </td>
                  <td className="pr-1 align-top">
                    <div className={`px-1 py-0.5 text-[10px] leading-tight ${tagName ? 'text-gray-400' : 'text-gray-600 italic'}`} title={tagName ?? undefined}>
                      {tagName ?? '—'}
                    </div>
                  </td>
                  <td className="pr-1 align-top">
                    <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                      value={f.value} onChange={(e) => updateField(i, { value: e.target.value })} />
                  </td>
                  <td className="pl-1 align-top">
                    <button className="text-red-400 hover:text-red-300 text-xs" onClick={() => removeField(i)}>x</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <VarRefPanel />

      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
