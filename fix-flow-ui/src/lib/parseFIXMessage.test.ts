import { describe, it, expect } from 'vitest';
import { parseFIXMessage, ENGINE_TAGS } from './parseFIXMessage';

describe('ENGINE_TAGS', () => {
  it('contains exactly the QuickFIX/J session-managed tags', () => {
    expect([...ENGINE_TAGS].sort((a, b) => a - b)).toEqual([8, 9, 10, 34, 49, 52, 56]);
  });
});

describe('parseFIXMessage', () => {
  it('parses pipe-delimited tag=value segments', () => {
    const res = parseFIXMessage('35=D|11=ORD-001|55=AAPL|54=1|38=100');
    expect(res.msgType).toBe('D');
    expect(res.fields).toEqual([
      { tag: 11, value: 'ORD-001' },
      { tag: 55, value: 'AAPL' },
      { tag: 54, value: '1' },
      { tag: 38, value: '100' },
    ]);
  });

  it('parses SOH (\\x01) delimited messages', () => {
    const res = parseFIXMessage('35=D\x0111=X\x0155=IBM');
    expect(res.msgType).toBe('D');
    expect(res.fields).toEqual([
      { tag: 11, value: 'X' },
      { tag: 55, value: 'IBM' },
    ]);
  });

  it('skips engine-managed tags and counts them', () => {
    const res = parseFIXMessage('8=FIX.4.4|9=100|35=D|49=CLIENT|56=SERVER|52=now|34=1|10=123|55=AAPL');
    // 8,9,49,56,52,34,10 skipped (7), 35 becomes msgType (not skipped), 55 kept
    expect(res.msgType).toBe('D');
    expect(res.fields).toEqual([{ tag: 55, value: 'AAPL' }]);
    expect(res.skipped).toBe(7);
    for (const f of res.fields) expect(ENGINE_TAGS.has(f.tag)).toBe(false);
  });

  it('skips malformed segments (no =, bad tag) and counts them', () => {
    const res = parseFIXMessage('garbage|=nope|abc=1|55=AAPL|0=x');
    expect(res.fields).toEqual([{ tag: 55, value: 'AAPL' }]);
    // garbage (no =), =nope (empty->NaN), abc=1 (NaN), 0=x (tag<=0) => 4 skipped
    expect(res.skipped).toBe(4);
  });

  it('trims whitespace and ignores trailing empty segments', () => {
    const res = parseFIXMessage(' 35 = D | 55 = AAPL |');
    expect(res.msgType).toBe('D');
    expect(res.fields).toEqual([{ tag: 55, value: 'AAPL' }]);
  });

  it('handles values that contain = characters', () => {
    const res = parseFIXMessage('58=a=b=c');
    expect(res.fields).toEqual([{ tag: 58, value: 'a=b=c' }]);
  });
});
