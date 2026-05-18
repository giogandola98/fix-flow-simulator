import { ScenarioNode } from '../../../types';
import { useScenarioStore } from '../../../store/scenarioStore';
import { TimeoutConfig } from './TimeoutConfig';

interface HeaderRow { key: string; value: string; }
interface HttpCfg { method?: string; url?: string; headers?: HeaderRow[]; body?: string; }
interface Props { node: ScenarioNode; }

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];
const BODY_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export function HttpRequestConfig({ node }: Props) {
  const updateNode = useScenarioStore((s) => s.updateNode);
  const cfg = (node.config as HttpCfg) ?? {};
  const headers: HeaderRow[] = cfg.headers ?? [];
  const method = cfg.method ?? 'GET';
  const hasBody = BODY_METHODS.has(method);

  const patchConfig = (patch: Partial<HttpCfg>) =>
    updateNode(node.id, { config: { ...cfg, ...patch } });

  const updateHeader = (i: number, patch: Partial<HeaderRow>) => {
    const next = headers.map((h, idx) => (idx === i ? { ...h, ...patch } : h));
    patchConfig({ headers: next });
  };
  const addHeader = () => patchConfig({ headers: [...headers, { key: '', value: '' }] });
  const removeHeader = (i: number) => patchConfig({ headers: headers.filter((_, idx) => idx !== i) });

  return (
    <div className="text-xs space-y-2">
      <div>
        <label className="text-[10px] text-gray-500">Node Name</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={node.name} onChange={(e) => updateNode(node.id, { name: e.target.value })} />
      </div>
      <div>
        <label className="text-[10px] text-gray-500">Method</label>
        <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={method} onChange={(e) => patchConfig({ method: e.target.value })}>
          {METHODS.map((m) => <option key={m} value={m}>{m}</option>)}
        </select>
      </div>
      <div>
        <label className="text-[10px] text-gray-500">URL</label>
        <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono"
          placeholder="https://example.com/api"
          value={cfg.url ?? ''} onChange={(e) => patchConfig({ url: e.target.value })} />
      </div>
      <div>
        <div className="flex items-center justify-between">
          <label className="text-[10px] text-gray-500">Headers</label>
          <button className="text-[10px] px-2 py-0.5 bg-blue-600 hover:bg-blue-500 rounded" onClick={addHeader}>+ Header</button>
        </div>
        <table className="w-full mt-1">
          <thead className="text-[10px] text-gray-500">
            <tr><th className="text-left">Key</th><th className="text-left">Value</th><th /></tr>
          </thead>
          <tbody>
            {headers.map((h, i) => (
              <tr key={i}>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    placeholder="Content-Type"
                    value={h.key} onChange={(e) => updateHeader(i, { key: e.target.value })} />
                </td>
                <td className="pr-1">
                  <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-1 py-0.5"
                    placeholder="application/json"
                    value={h.value} onChange={(e) => updateHeader(i, { value: e.target.value })} />
                </td>
                <td>
                  <button className="text-red-400 hover:text-red-300 text-xs" onClick={() => removeHeader(i)}>x</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {hasBody ? (
        <div>
          <label className="text-[10px] text-gray-500">Body</label>
          <textarea className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 font-mono resize-y"
            rows={4} placeholder='{"key": "{{var:value}}"}'
            value={cfg.body ?? ''} onChange={(e) => patchConfig({ body: e.target.value })} />
        </div>
      ) : (
        <div className="text-[10px] text-gray-500 italic">
          GET requests have no body — use query params in the URL (e.g. ?key=value)
        </div>
      )}
      <TimeoutConfig value={node.timeout} onChange={(next) => updateNode(node.id, { timeout: next })} currentNodeId={node.id} />
    </div>
  );
}
