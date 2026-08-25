/**
 * Nodes that route through named handles rather than through a plain success/failure pair.
 *
 * ROUTE_FIX carries `rules[]` keyed by `ruleId`; DECISION carries `branches[]` keyed by `branchId`
 * (issue #86). Both draw one canvas handle per entry and both store the drawn target *in the node
 * config*, not only as an edge — the engine traverses the config, so an edge whose target never
 * reaches it is a line that does nothing.
 *
 * Keeping the shape difference here means the canvas does not grow a second `type === '...'`
 * branch every time another node type gains routes.
 */
export interface HandleRoute {
  /** The canvas handle id: a rule id or a branch id. */
  id: string;
  label: string;
  targetNodeId?: string;
}

interface RouteSpec {
  /** Config key holding the array. */
  key: string;
  /** Property inside each entry that names its handle. */
  idKey: string;
}

const SPECS: Record<string, RouteSpec> = {
  ROUTE_FIX: { key: 'rules', idKey: 'ruleId' },
  DECISION: { key: 'branches', idKey: 'branchId' },
};

type Config = Record<string, unknown> | undefined | null;

function entriesOf(type: string | undefined, config: Config): { spec: RouteSpec; entries: Record<string, unknown>[] } | null {
  const spec = type ? SPECS[type] : undefined;
  if (!spec) return null;
  const raw = (config ?? {})[spec.key];
  if (!Array.isArray(raw)) return null;
  return { spec, entries: raw as Record<string, unknown>[] };
}

/** The handle-addressed routes a node exposes; empty for every other node type. */
export function handleRoutesOf(type: string | undefined, config: Config): HandleRoute[] {
  const found = entriesOf(type, config);
  if (!found) return [];
  return found.entries.map((entry) => ({
    id: String(entry[found.spec.idKey] ?? ''),
    label: String(entry.label ?? ''),
    targetNodeId: entry.targetNodeId === undefined ? undefined : String(entry.targetNodeId),
  }));
}

/**
 * The config with one handle's target set (or cleared, with `''`).
 * Returns null when the node has no routes or no such handle, so the caller can tell
 * "nothing to write" from "written".
 */
export function withRouteTarget(
  type: string | undefined,
  config: Config,
  handleId: string | null | undefined,
  targetNodeId: string,
): Record<string, unknown> | null {
  const found = entriesOf(type, config);
  if (!found || !handleId) return null;
  if (!found.entries.some((e) => String(e[found.spec.idKey] ?? '') === handleId)) return null;
  return {
    ...(config ?? {}),
    [found.spec.key]: found.entries.map((entry) =>
      String(entry[found.spec.idKey] ?? '') === handleId ? { ...entry, targetNodeId } : entry,
    ),
  };
}
