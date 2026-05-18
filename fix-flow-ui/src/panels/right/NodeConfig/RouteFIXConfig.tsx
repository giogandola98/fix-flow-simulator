import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';
import { VarRefPanel } from './VarRefPanel';

interface MatcherRow { tag: number; value: string; }
interface RoutingRule { ruleId: string; label: string; matchers: MatcherRow[]; targetNodeId: string; }
interface RouteCfg { rules?: RoutingRule[]; }
interface Props { node: ScenarioNode; }

export function RouteFIXConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg = (node.config as RouteCfg) ?? {};
  const rules: RoutingRule[] = cfg.rules ?? [];

  const patchRules = (next: RoutingRule[]) =>
    updateNode(node.id, { config: { ...cfg, rules: next } });

  const addRule = () =>
    patchRules([...rules, { ruleId: crypto.randomUUID(), label: '', matchers: [], targetNodeId: '' }]);

  const removeRule = (i: number) => patchRules(rules.filter((_, idx) => idx !== i));

  const updateRule = (i: number, patch: Partial<RoutingRule>) =>
    patchRules(rules.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  const addMatcher = (i: number) =>
    updateRule(i, { matchers: [...rules[i].matchers, { tag: 0, value: '' }] });

  const removeMatcher = (ruleIdx: number, matcherIdx: number) =>
    updateRule(ruleIdx, { matchers: rules[ruleIdx].matchers.filter((_, idx) => idx !== matcherIdx) });

  const updateMatcher = (ruleIdx: number, matcherIdx: number, patch: Partial<MatcherRow>) =>
    updateRule(ruleIdx, {
      matchers: rules[ruleIdx].matchers.map((m, idx) => (idx === matcherIdx ? { ...m, ...patch } : m)),
    });

  const otherNodes = allNodes.filter((n) => n.id !== node.id);

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-pink-300 bg-pink-900/20 border border-pink-800/40 rounded px-2 py-1.5">
        Waits for an inbound FIX message and routes to the first rule whose matchers all match.
        Rules are evaluated top-to-bottom. A rule with no matchers acts as default/fallback.
      </div>

      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-[10px] text-gray-500">
            Routing Rules
            <span title="Each rule is tested in order. The first rule where all matchers match is selected. A rule with zero matchers is a catch-all default. The matched rule's target node becomes the next step." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <button className="text-[10px] px-2 py-0.5 bg-pink-700 hover:bg-pink-600 rounded" onClick={addRule}>+ Rule</button>
        </div>
        <div className="space-y-2">
          {rules.map((r, i) => (
            <div key={r.ruleId} className="border border-[#2a2d3a] rounded p-2 space-y-1">
              <div className="flex items-center justify-between">
                <input type="text" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 mr-1"
                  placeholder="Rule label (e.g. Quote, Reject, Default)"
                  value={r.label} onChange={(e) => updateRule(i, { label: e.target.value })} />
                <button className="text-red-400 hover:text-red-300" onClick={() => removeRule(i)}>x</button>
              </div>
              <div>
                <div className="text-[10px] text-gray-500 flex items-center justify-between">
                  <span>
                    Matchers
                    <span title="FIX tag-value pairs that must ALL match the incoming message (AND logic). Leave empty to make this rule a default/fallback that catches any message. Value supports {{node:id:tagN}} placeholders." className="ml-1 text-gray-600 cursor-help">?</span>
                    {r.matchers.length === 0 && (
                      <span className="ml-1 text-pink-400 font-medium">(default)</span>
                    )}
                  </span>
                  <button className="text-[10px] px-1 bg-blue-700 hover:bg-blue-600 rounded" onClick={() => addMatcher(i)}>+ Tag</button>
                </div>
                {r.matchers.map((m, j) => (
                  <div key={j} className="flex gap-1 mt-0.5">
                    <input type="number" className="w-14 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                      placeholder="Tag" value={m.tag}
                      onChange={(e) => updateMatcher(i, j, { tag: Number(e.target.value) })} />
                    <input type="text" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                      placeholder="Value or {{node:id:tagN}}"
                      value={m.value} onChange={(e) => updateMatcher(i, j, { value: e.target.value })} />
                    <button className="text-red-400 hover:text-red-300" onClick={() => removeMatcher(i, j)}>x</button>
                  </div>
                ))}
              </div>
              <div>
                <label className="text-[10px] text-gray-500">
                  Target Node
                  <span title="The node to execute when this rule matches. Draw an edge from this ROUTE_FIX block to the target node on the canvas to visualise the branch." className="ml-1 text-gray-600 cursor-help">?</span>
                </label>
                <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                  value={r.targetNodeId}
                  onChange={(e) => updateRule(i, { targetNodeId: e.target.value })}>
                  <option value="">— select —</option>
                  {otherNodes.map((n) => (
                    <option key={n.id} value={n.id}>{n.name} ({n.type})</option>
                  ))}
                </select>
              </div>
            </div>
          ))}
          {rules.length === 0 && (
            <div className="text-[10px] text-gray-600 italic">No rules yet. Add rules to route incoming FIX messages.</div>
          )}
        </div>
      </div>

      <VarRefPanel />

      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
