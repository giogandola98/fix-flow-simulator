import { describe, it, expect } from 'vitest';
import { fixTagName, FIX_TAGS } from './fixTags';

describe('fixTags', () => {
  it('returns the field name for known tags', () => {
    expect(fixTagName(55)).toBe('Symbol');
    expect(fixTagName(11)).toBe('ClOrdID');
    expect(fixTagName(8)).toBe('BeginString');
    expect(fixTagName(35)).toBe('MsgType');
    expect(fixTagName(54)).toBe('Side');
  });

  it('returns undefined for unknown tags', () => {
    expect(fixTagName(99999)).toBeUndefined();
    expect(fixTagName(0)).toBeUndefined();
    expect(fixTagName(-1)).toBeUndefined();
  });

  it('dictionary is non-empty and internally consistent', () => {
    const entries = Object.entries(FIX_TAGS);
    expect(entries.length).toBeGreaterThan(50);
    for (const [tag, name] of entries) {
      expect(Number(tag)).toBeGreaterThan(0);
      expect(typeof name).toBe('string');
      expect(name.length).toBeGreaterThan(0);
    }
  });

  it('has no duplicate field names for the engine session tags', () => {
    expect(FIX_TAGS[49]).toBe('SenderCompID');
    expect(FIX_TAGS[56]).toBe('TargetCompID');
    expect(FIX_TAGS[34]).toBe('MsgSeqNum');
  });
});
