import { useTranslation } from 'react-i18next';
import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { DateRulesEditor, DateRule } from './DateRulesEditor';
import { fixTagName } from '../../../lib/fixTags';

type RuleKind = 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'NOT_CONTAINS' | 'ENUM' | 'REGEX' | 'NUMERIC_MIN' | 'NUMERIC_MAX' | 'LENGTH' | 'LENGTH_MIN' | 'LENGTH_MAX' | 'FIELD_PRESENT' | 'FIELD_ABSENT' | 'DATE_RULE';
interface ValidationRule { tag: number; rule: RuleKind; value?: string; values?: string[]; pattern?: string; numericValue?: number; ref?: string; dateRuleId?: string; groupTag?: number; index?: string; }
interface ValidateCfg { sourceNodeId?: string; strictMode?: boolean; rules?: ValidationRule[]; dateRules?: DateRule[]; }
const RULES: RuleKind[] = ['EQUALS', 'NOT_EQUALS', 'CONTAINS', 'NOT_CONTAINS', 'ENUM', 'REGEX', 'NUMERIC_MIN', 'NUMERIC_MAX', 'LENGTH', 'LENGTH_MIN', 'LENGTH_MAX', 'FIELD_PRESENT', 'FIELD_ABSENT', 'DATE_RULE'];
/** Rules whose parameter is a free-text value (and which also accept a cross-node ref). */
const VALUE_RULES: RuleKind[] = ['EQUALS', 'NOT_EQUALS', 'CONTAINS', 'NOT_CONTAINS'];
/** Rules whose parameter is a number. */
const NUMERIC_RULES: RuleKind[] = ['NUMERIC_MIN', 'NUMERIC_MAX', 'LENGTH', 'LENGTH_MIN', 'LENGTH_MAX'];
const VALUE_PLACEHOLDER: Partial<Record<RuleKind, string>> = {
  CONTAINS: 'substring, e.g. /',
  NOT_CONTAINS: 'substring, e.g. XXX',
};

export function ValidateConfig({ node }: { node: ScenarioNode }) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  // Select this node live from the store (falling back to the prop for a
  // node the store doesn't know about yet) so the panel re-renders and
  // reflects its own writes immediately. Without this, controlled inputs
  // stay bound to the render-time `node` prop and rapid sequential edits
  // (e.g. typing several characters, or editing two fields back to back)
  // each build on a stale snapshot and clobber one another in the store.
  const storeNode = useScenarioStore((s) => s.nodes.find((n) => n.id === node.id));
  const allNodes = useScenarioStore((s) => s.nodes);
  const liveNode = storeNode ?? node;
  const cfg = (liveNode.config as ValidateCfg) ?? {};
  const rules = cfg.rules ?? [];
  const dateRules = cfg.dateRules ?? [];

  const patchConfig = (patch: Partial<ValidateCfg>) => updateNode(node.id, { config: { ...cfg, ...patch } });
  const updateRule = (i: number, patch: Partial<ValidationRule>) => {
    const next = rules.map((r, idx) => {
      if (idx !== i) return r;
      const merged: Record<string, unknown> = { ...r, ...patch };
      Object.keys(merged).forEach((k) => { if (merged[k] === undefined) delete merged[k]; });
      return merged as unknown as ValidationRule;
    });
    patchConfig({ rules: next });
  };
  const addRule = () => patchConfig({ rules: [...rules, { tag: 0, rule: 'EQUALS', value: '' }] });
  const removeRule = (i: number) => patchConfig({ rules: rules.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-gray-500 italic bg-[#1a1d27] rounded px-2 py-1">
        {t('nodeConfig.validate.desc')}
      </div>
      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">
          {t('nodeConfig.validate.sourceNode')}
          <span title="Which received message to validate. Leave on the default to validate the last message received in the run — normally the one the Expect FIX block just matched." className="ml-1 text-gray-600 cursor-help">?</span>
        </label>
        <select
          data-testid="validate-source-node"
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={cfg.sourceNodeId ?? ''}
          onChange={(e) => {
            const v = e.target.value;
            const next = { ...cfg } as Record<string, unknown>;
            if (v) next.sourceNodeId = v;
            else delete next.sourceNodeId;
            updateNode(node.id, { config: next });
          }}
        >
          <option value="">{t('nodeConfig.validate.sourceNodeAuto')}</option>
          {allNodes
            .filter((n) => n.type === 'EXPECT_FIX' || n.type === 'ROUTE_FIX')
            .map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
        </select>
      </div>
      <label className="flex items-center gap-2">
        <input type="checkbox" checked={cfg.strictMode ?? false} onChange={(e) => patchConfig({ strictMode: e.target.checked })} />
        {t('nodeConfig.validate.strictMode')}
        <span title="When enabled, any field in the received message not covered by a rule causes validation failure." className="text-[10px] text-gray-600 cursor-help">?</span>
      </label>
      <div className="flex items-center justify-between">
        <div className="text-[10px] uppercase text-gray-500">
          {t('nodeConfig.validate.rules')}
          <span title="Each rule checks one FIX tag. EQUALS/NOT_EQUALS: exact string match. ENUM: value in list. REGEX: pattern match. NUMERIC_MIN/MAX: numeric bounds. FIELD_PRESENT/ABSENT: existence check. DATE_RULE: timestamp validation." className="ml-1 normal-case text-gray-600 cursor-help">?</span>
        </div>
        <button className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded" onClick={addRule}>{t('nodeConfig.validate.addRule')}</button>
      </div>
      <div className="space-y-1">
        {rules.map((r, i) => (
          <div key={i} className="border border-[#2a2d3a] rounded p-2">
            <div className="flex gap-1 items-start">
              <div className="w-16 flex-shrink-0">
                <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                  value={r.tag} onChange={(e) => updateRule(i, { tag: Number(e.target.value) })} placeholder="tag" />
                {fixTagName(r.tag) && <div className="text-[9px] text-gray-500 leading-tight mt-0.5">{fixTagName(r.tag)}</div>}
              </div>
              <input
                type="number"
                data-testid={`validate-grouptag-${i}`}
                placeholder="grp"
                title="Repeating group counter tag (e.g. 555 NoLegs). Leave blank to validate a top-level field."
                className="w-14 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                value={r.groupTag ?? ''}
                onChange={(e) => {
                  const v = e.target.value.trim();
                  updateRule(i, v === ''
                    ? { groupTag: undefined }
                    : { groupTag: Number(v) });
                }}
              />
              <input
                type="text"
                data-testid={`validate-index-${i}`}
                placeholder="idx"
                title="Group entry index, 0-based. Use * to apply the rule to every entry."
                className="w-10 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                value={r.index ?? ''}
                onChange={(e) => {
                  const v = e.target.value.trim();
                  updateRule(i, v === '' ? { index: undefined } : { index: v });
                }}
              />
              <select className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                value={r.rule} onChange={(e) => updateRule(i, { rule: e.target.value as RuleKind })}>
                {RULES.map((rk) => <option key={rk}>{rk}</option>)}
              </select>
              <button className="text-red-400 hover:text-red-300" onClick={() => removeRule(i)}>x</button>
            </div>
            {VALUE_RULES.includes(r.rule) && (
              <input type="text" className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                data-testid={`validate-value-${i}`}
                value={r.value ?? ''} onChange={(e) => updateRule(i, { value: e.target.value })}
                placeholder={VALUE_PLACEHOLDER[r.rule] ?? 'value'} />
            )}
            {r.rule === 'ENUM' && (
              <input type="text" className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={(r.values ?? []).join(',')} onChange={(e) => updateRule(i, { values: e.target.value.split(',').map((s) => s.trim()) })} placeholder="comma,separated,values" />
            )}
            {r.rule === 'REGEX' && (
              <input type="text" className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.pattern ?? ''} onChange={(e) => updateRule(i, { pattern: e.target.value })} placeholder="regex pattern" />
            )}
            {NUMERIC_RULES.includes(r.rule) && (
              <input type="number" className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                data-testid={`validate-numeric-${i}`}
                min={r.rule.startsWith('LENGTH') ? 0 : undefined}
                value={r.numericValue ?? 0} onChange={(e) => updateRule(i, { numericValue: Number(e.target.value) })} />
            )}
            {r.rule === 'DATE_RULE' && (
              <select className="w-full mt-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.dateRuleId ?? ''} onChange={(e) => updateRule(i, { dateRuleId: e.target.value })}>
                <option value="">{t('nodeConfig.validate.selectDateRule')}</option>
                {dateRules.map((dr) => <option key={dr.ruleId} value={dr.ruleId}>{dr.ruleId}</option>)}
              </select>
            )}
            <div className="mt-1">
              <label className="text-[10px] text-gray-500">
                {t('nodeConfig.validate.crossNodeRef')}
                <span title="Compare this tag against a value from another node. Syntax: {{node:nodeId:tagN}}. Leave blank to use the static Value above." className="ml-1 text-gray-600 cursor-help">?</span>
              </label>
              <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                value={r.ref ?? ''} onChange={(e) => updateRule(i, { ref: e.target.value })} placeholder="{{node:send-order:tag11}}" />
            </div>
          </div>
        ))}
      </div>
      <DateRulesEditor value={dateRules} onChange={(next) => patchConfig({ dateRules: next })} />
    </div>
  );
}
