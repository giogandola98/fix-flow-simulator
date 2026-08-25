import { describe, expect, it } from 'vitest';
import { handleRoutesOf, withRouteTarget } from './handleRoutes';

const routeCfg = {
  rules: [
    { ruleId: 'r1', label: 'Filled', matchers: [], targetNodeId: 'n-filled' },
    { ruleId: 'r2', label: '', matchers: [], targetNodeId: '' },
  ],
};

const decisionCfg = {
  branches: [
    { branchId: 'b1', label: 'Filled', conditions: ['a == a'], targetNodeId: 'n-filled' },
    { branchId: 'b2', label: 'Default', conditions: [], targetNodeId: '' },
  ],
};

describe('handleRoutesOf', () => {
  it('reads ROUTE_FIX rules', () => {
    expect(handleRoutesOf('ROUTE_FIX', routeCfg)).toEqual([
      { id: 'r1', label: 'Filled', targetNodeId: 'n-filled' },
      { id: 'r2', label: '', targetNodeId: '' },
    ]);
  });

  it('reads DECISION branches the same way', () => {
    expect(handleRoutesOf('DECISION', decisionCfg)).toEqual([
      { id: 'b1', label: 'Filled', targetNodeId: 'n-filled' },
      { id: 'b2', label: 'Default', targetNodeId: '' },
    ]);
  });

  it('is empty for a node type that does not route through handles', () => {
    expect(handleRoutesOf('VALIDATE', { rules: [{ tag: 35 }] })).toEqual([]);
    expect(handleRoutesOf('SEND_FIX', { fields: [] })).toEqual([]);
  });

  it('is empty for a missing or malformed config', () => {
    expect(handleRoutesOf('DECISION', undefined)).toEqual([]);
    expect(handleRoutesOf('DECISION', {})).toEqual([]);
    expect(handleRoutesOf('DECISION', { branches: 'nope' })).toEqual([]);
    expect(handleRoutesOf(undefined, decisionCfg)).toEqual([]);
  });
});

describe('withRouteTarget', () => {
  it('sets the target of one DECISION branch and leaves the others alone', () => {
    const patched = withRouteTarget('DECISION', decisionCfg, 'b2', 'n-default');
    expect(patched).not.toBeNull();
    const branches = patched!.branches as Array<Record<string, unknown>>;
    expect(branches[1].targetNodeId).toBe('n-default');
    expect(branches[1].conditions).toEqual([]); // the rest of the branch survives
    expect(branches[0].targetNodeId).toBe('n-filled');
  });

  it('sets the target of one ROUTE_FIX rule', () => {
    const patched = withRouteTarget('ROUTE_FIX', routeCfg, 'r2', 'n-two');
    const rules = patched!.rules as Array<Record<string, unknown>>;
    expect(rules[1].targetNodeId).toBe('n-two');
    expect(rules[0].targetNodeId).toBe('n-filled');
  });

  it('clears a target with an empty string', () => {
    const patched = withRouteTarget('DECISION', decisionCfg, 'b1', '');
    const branches = patched!.branches as Array<Record<string, unknown>>;
    expect(branches[0].targetNodeId).toBe('');
  });

  it('does not mutate the config it was given', () => {
    withRouteTarget('DECISION', decisionCfg, 'b1', 'somewhere-else');
    expect(decisionCfg.branches[0].targetNodeId).toBe('n-filled');
  });

  it('returns null when there is nothing to write', () => {
    expect(withRouteTarget('DECISION', decisionCfg, 'unknown-handle', 'x')).toBeNull();
    expect(withRouteTarget('DECISION', decisionCfg, null, 'x')).toBeNull();
    expect(withRouteTarget('VALIDATE', { rules: [] }, 'r1', 'x')).toBeNull();
  });

  it('keeps other config keys', () => {
    const patched = withRouteTarget('DECISION', { ...decisionCfg, condition: 'legacy' }, 'b1', 'n');
    expect(patched!.condition).toBe('legacy');
  });
});
