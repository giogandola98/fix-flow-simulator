import { useTranslation } from 'react-i18next';
import { ENGINE_TAGS } from '../../../lib/parseFIXMessage';
import { fixTagName, FIX_TAGS } from '../../../lib/fixTags';

export interface FieldRow { tag: number; value: string }

export interface FieldTableProps {
  fields: FieldRow[];
  onChange: (next: FieldRow[]) => void;
  label?: string;
  idPrefix?: string;
}

export function FieldTable({ fields, onChange, label, idPrefix = 'ft' }: FieldTableProps) {
  const { t } = useTranslation();

  const updateField = (i: number, patch: Partial<FieldRow>) =>
    onChange(fields.map((f, idx) => (idx === i ? { ...f, ...patch } : f)));
  const addField = () => onChange([...fields, { tag: 0, value: '' }]);
  const removeField = (i: number) => onChange(fields.filter((_, idx) => idx !== i));

  return (
    <div>
      <div className="flex items-center justify-between">
        <label className="text-[10px] text-gray-500">
          {label ?? t('nodeConfig.sendFix.fields')}
          <span
            title="FIX tag-value pairs. Value supports placeholders: {{now}}, {{uuid}}, {{seq:name}}, {{env:VAR}}, {{node:id:tagN}}, {{node:id:gNNN.i:tagM}}."
            className="ml-1 text-gray-600 cursor-help"
          >?</span>
        </label>
        <button
          type="button"
          data-testid={`${idPrefix}-add-field`}
          className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded"
          onClick={addField}
        >
          {t('nodeConfig.sendFix.addField')}
        </button>
      </div>
      <datalist id="fix-tag-list">
        {Object.entries(FIX_TAGS).map(([tag, name]) => (
          <option key={tag} value={tag}>{`${tag} — ${name}`}</option>
        ))}
      </datalist>
      <table className="w-full mt-1">
        <thead className="text-[10px] text-gray-500">
          <tr>
            <th className="text-left w-16">{t('nodeConfig.tag')}</th>
            <th className="text-left">{t('nodeConfig.field')}</th>
            <th className="text-left">{t('nodeConfig.value')}</th>
            <th />
          </tr>
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
                    data-testid={`${idPrefix}-tag-${i}`}
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
                  <input
                    type="text"
                    data-testid={`${idPrefix}-value-${i}`}
                    className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    value={f.value}
                    onChange={(e) => updateField(i, { value: e.target.value })}
                  />
                </td>
                <td className="pl-1 align-top">
                  <button
                    type="button"
                    data-testid={`${idPrefix}-remove-${i}`}
                    className="text-red-400 hover:text-red-300 text-xs"
                    onClick={() => removeField(i)}
                  >x</button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
