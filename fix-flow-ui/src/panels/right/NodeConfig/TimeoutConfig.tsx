import { useTranslation } from 'react-i18next';
import { ScenarioNode, TimeUnit, TimeoutAction, TimeoutConfig as Cfg } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';

const UNITS: TimeUnit[] = ['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS'];
const ACTIONS: TimeoutAction[] = ['FAIL', 'RETRY', 'CONTINUE', 'JUMP'];

interface Props {
  value: Cfg | undefined;
  onChange: (next: Cfg | undefined) => void;
  currentNodeId?: string;
}

export function TimeoutConfig({ value, onChange, currentNodeId }: Props) {
  const { t } = useTranslation();
  const allNodes = useScenarioStore((s) => s.nodes);
  const cfg: Cfg = value ?? { value: 30, unit: 'SECONDS', onTimeout: 'FAIL' };
  const update = (patch: Partial<Cfg>) => onChange({ ...cfg, ...patch });
  const jumpTargets = allNodes.filter((n: ScenarioNode) => n.id !== currentNodeId);

  return (
    <div className="border border-[#2a2d3a] rounded p-2 mt-2">
      <div className="text-[10px] uppercase text-gray-500 mb-1">{t('nodeConfig.timeout.title')}</div>
      <div className="flex gap-1 items-end">
        <div>
          <label className="text-[10px] text-gray-500">{t('nodeConfig.timeout.value')}<span title="How long to wait before triggering the timeout action." className="ml-1 text-gray-600 cursor-help">?</span></label>
          <input
            type="number"
            className="w-20 bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs block"
            value={cfg.value}
            onChange={(e) => update({ value: Number(e.target.value) })}
          />
        </div>
        <div>
          <label className="text-[10px] text-gray-500">{t('nodeConfig.timeout.unit')}<span title="Time unit: MILLISECONDS, SECONDS, MINUTES, or HOURS." className="ml-1 text-gray-600 cursor-help">?</span></label>
          <select
            className="bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs block"
            value={cfg.unit}
            onChange={(e) => update({ unit: e.target.value as TimeUnit })}
          >
            {UNITS.map((u) => <option key={u}>{u}</option>)}
          </select>
        </div>
      </div>
      <div className="mt-1">
        <label className="text-[10px] text-gray-500">{t('nodeConfig.timeout.onTimeout')}<span title="Action when timeout fires: CONTINUE proceeds to next node, FAIL marks node failed, RETRY re-runs the node, JUMP goes to another node." className="ml-1 text-gray-600 cursor-help">?</span></label>
        <select
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
          value={cfg.onTimeout}
          onChange={(e) => update({ onTimeout: e.target.value as TimeoutAction })}
        >
          {ACTIONS.map((a) => <option key={a}>{a}</option>)}
        </select>
      </div>
      {cfg.onTimeout === 'JUMP' && (
        <div className="mt-1">
          <label className="text-[10px] text-gray-500">{t('nodeConfig.timeout.jumpToNode')}<span title="Target node to jump to when On Timeout is JUMP." className="ml-1 text-gray-600 cursor-help">?</span></label>
          <select
            className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
            value={cfg.jumpTo ?? ''}
            onChange={(e) => update({ jumpTo: e.target.value || undefined })}
          >
            <option value="">{t('nodeConfig.timeout.selectNode')}</option>
            {jumpTargets.map((n: ScenarioNode) => (
              <option key={n.id} value={n.id}>{n.name}</option>
            ))}
          </select>
        </div>
      )}
    </div>
  );
}
