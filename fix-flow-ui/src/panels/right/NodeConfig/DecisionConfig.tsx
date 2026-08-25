import { useTranslation } from 'react-i18next';
import { ScenarioEdge, ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { VarRefPanel } from './VarRefPanel';

interface Branch { branchId: string; label: string; conditions: string[]; targetNodeId: string; }
interface DecisionCfg { condition?: string; branches?: Branch[]; }
interface Props { node: ScenarioNode; }

/** Keeps the canvas edge for one branch handle in step with its configured target. */
function syncBranchEdge(
  nodeId: string,
  branch: Branch,
  edges: ScenarioEdge[],
  setEdges: (e: ScenarioEdge[]) => void,
) {
  const without = edges.filter((e) => !(e.from === nodeId && e.sourceHandle === branch.branchId));
  if (branch.targetNodeId) {
    setEdges([
      ...without,
      { from: nodeId, to: branch.targetNodeId, label: branch.label || branch.branchId, sourceHandle: branch.branchId },
    ]);
  } else {
    setEdges(without);
  }
}

export function DecisionConfig({ node }: Props) {
  const { t } = useTranslation();
  const updateNode = useScenarioStore((s) => s.updateNode);
  const allNodes = useScenarioStore((s) => s.nodes);
  const edges = useScenarioStore((s) => s.edges);
  const setEdges = useScenarioStore((s) => s.setEdges);
  // Read the node live from the store so consecutive edits build on each other rather than on a
  // stale render-time snapshot (same reason as ValidateConfig).
  const storeNode = useScenarioStore((s) => s.nodes.find((n) => n.id === node.id));
  const liveNode = storeNode ?? node;
  const cfg = (liveNode.config as DecisionCfg) ?? {};
  const branches: Branch[] = cfg.branches ?? [];

  const patch = (next: Partial<DecisionCfg>) => updateNode(node.id, { config: { ...cfg, ...next } });

  const addBranch = () =>
    patch({
      branches: [
        ...branches,
        { branchId: crypto.randomUUID(), label: '', conditions: [''], targetNodeId: '' },
      ],
    });

  const removeBranch = (i: number) => {
    const b = branches[i];
    setEdges(edges.filter((e) => !(e.from === node.id && e.sourceHandle === b.branchId)));
    patch({ branches: branches.filter((_, idx) => idx !== i) });
  };

  const updateBranch = (i: number, next: Partial<Branch>) => {
    const updated = branches.map((b, idx) => (idx === i ? { ...b, ...next } : b));
    patch({ branches: updated });
    syncBranchEdge(node.id, updated[i], edges, setEdges);
  };

  const addCondition = (i: number) =>
    updateBranch(i, { conditions: [...branches[i].conditions, ''] });

  const removeCondition = (branchIdx: number, condIdx: number) =>
    updateBranch(branchIdx, {
      conditions: branches[branchIdx].conditions.filter((_, idx) => idx !== condIdx),
    });

  const updateCondition = (branchIdx: number, condIdx: number, value: string) =>
    updateBranch(branchIdx, {
      conditions: branches[branchIdx].conditions.map((c, idx) => (idx === condIdx ? value : c)),
    });

  const otherNodes = allNodes.filter((n) => n.id !== node.id);

  return (
    <div className="text-xs space-y-2">
      <div className="text-[10px] text-yellow-300 bg-yellow-900/20 border border-yellow-800/40 rounded px-2 py-1.5">
        {t('nodeConfig.decision.desc')}
      </div>

      <div>
        <label className="text-[10px] text-gray-500">{t('nodeConfig.nodeName')}</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={liveNode.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>

      {branches.length === 0 && (
        <div>
          <label className="text-[10px] text-gray-500">
            {t('nodeConfig.decision.condition')}
            <span title="Expression: LEFT OP RIGHT. Operators: == (exact match), != (not equal), contains (substring). Use {{node:id:tagN}} to reference FIX fields from previous nodes. Example: {{node:expect-er:tag39}} == 0" className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono"
            data-testid="decision-condition"
            placeholder='{{node:expect-er:tag39}} == "0"'
            value={cfg.condition ?? ''}
            onChange={(e) => patch({ condition: e.target.value })} />
          <div className="text-[10px] text-gray-600 mt-0.5">{t('nodeConfig.decision.operators')} <code>==</code> <code>!=</code> <code>contains</code></div>
        </div>
      )}

      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-[10px] text-gray-500">
            {t('nodeConfig.decision.branches')}
            <span title="Branches are tested in order. A branch is taken when ALL of its conditions hold. A branch with no conditions is a catch-all default. With no branch matched and no default, the block fails down its failure edge." className="ml-1 text-gray-600 cursor-help">?</span>
          </label>
          <button
            className="text-[10px] px-2 py-0.5 bg-orange-700 hover:bg-orange-600 rounded"
            data-testid="decision-add-branch"
            onClick={addBranch}
          >
            {t('nodeConfig.decision.addBranch')}
          </button>
        </div>

        {branches.length === 0 && (
          <div className="text-[10px] text-gray-600 italic">{t('nodeConfig.decision.noBranches')}</div>
        )}

        <div className="space-y-2">
          {branches.map((b, i) => (
            <div key={b.branchId} className="border border-[#2a2d3a] rounded p-2 space-y-1">
              <div className="flex items-center justify-between">
                <input type="text" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 mr-1"
                  data-testid={`decision-branch-label-${i}`}
                  placeholder="Branch label (e.g. Filled, Rejected, Default)"
                  value={b.label} onChange={(e) => updateBranch(i, { label: e.target.value })} />
                <button
                  className="text-red-400 hover:text-red-300"
                  data-testid={`decision-remove-branch-${i}`}
                  onClick={() => removeBranch(i)}
                >x</button>
              </div>

              <div>
                <div className="text-[10px] text-gray-500 flex items-center justify-between">
                  <span>
                    {t('nodeConfig.decision.conditions')}
                    {b.conditions.filter((c) => c.trim() !== '').length === 0 && (
                      <span className="ml-1 text-orange-400 font-medium">{t('nodeConfig.decision.defaultBranch')}</span>
                    )}
                  </span>
                  <button
                    className="text-[10px] px-1 bg-blue-700 hover:bg-blue-600 rounded"
                    data-testid={`decision-add-condition-${i}`}
                    onClick={() => addCondition(i)}
                  >
                    {t('nodeConfig.decision.addCondition')}
                  </button>
                </div>
                {b.conditions.map((c, j) => (
                  <div key={j} className="flex gap-1 mt-0.5">
                    <input type="text" className="flex-1 bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5 font-mono"
                      data-testid={`decision-condition-${i}-${j}`}
                      placeholder='{{node:expect-er:tag39}} == "2"'
                      value={c} onChange={(e) => updateCondition(i, j, e.target.value)} />
                    <button className="text-red-400 hover:text-red-300" onClick={() => removeCondition(i, j)}>x</button>
                  </div>
                ))}
              </div>

              <div>
                <label className="text-[10px] text-gray-500">{t('nodeConfig.decision.targetNode')}</label>
                <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                  data-testid={`decision-target-${i}`}
                  value={b.targetNodeId}
                  onChange={(e) => updateBranch(i, { targetNodeId: e.target.value })}>
                  <option value="">{t('nodeConfig.decision.selectNode')}</option>
                  {otherNodes.map((n) => (
                    <option key={n.id} value={n.id}>{n.name} ({n.type})</option>
                  ))}
                </select>
              </div>
            </div>
          ))}
        </div>
      </div>

      <VarRefPanel />
    </div>
  );
}
