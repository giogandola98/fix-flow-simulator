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

describe('repeating groups on paste', () => {
  const multileg =
    '8=FIXT.1.1|35=AB|49=CLIENT|56=SERVER|11=ORD-1|55=EUR/USD|167=FXSWAP|' +
    '555=2|600=EUR/USD|624=1|587=0|600=EUR/USD|624=2|587=6|60=20260824-10:00:00|';

  it('rebuilds two leg entries', () => {
    const r = parseFIXMessage(multileg);
    expect(r.msgType).toBe('AB');
    expect(r.groups).toHaveLength(1);
    expect(r.groups[0].counterTag).toBe(555);
    expect(r.groups[0].entries).toHaveLength(2);
    expect(r.groups[0].entries[0].fields).toEqual([
      { tag: 600, value: 'EUR/USD' }, { tag: 624, value: '1' }, { tag: 587, value: '0' },
    ]);
    expect(r.groups[0].entries[1].fields[1]).toEqual({ tag: 624, value: '2' });
  });

  it('keeps fields before and after the group at top level', () => {
    const r = parseFIXMessage(multileg);
    const tags = r.fields.map((f) => f.tag);
    expect(tags).toContain(11);
    expect(tags).toContain(167);
    expect(tags).toContain(60);
    expect(tags).not.toContain(600);
    expect(tags).not.toContain(555);
  });

  it('reports unknown counter tags and leaves their content flat', () => {
    const r = parseFIXMessage('8=FIXT.1.1|35=8|9999=2|8001=a|8001=b|11=ORD-1|');
    expect(r.unknownCounters).toEqual([]);
    expect(r.groups).toHaveLength(0);
    expect(r.fields.map((f) => f.tag)).toContain(8001);
  });

  it('flags a known counter whose declared count does not match', () => {
    const r = parseFIXMessage('8=FIXT.1.1|35=AB|555=3|600=EUR/USD|624=1|11=ORD-1|');
    expect(r.unknownCounters).toContain(555);
  });

  it('leaves a message without groups exactly as before', () => {
    const r = parseFIXMessage('8=FIX.4.4|35=D|49=C|56=S|11=ORD-1|55=AAPL|38=100|');
    expect(r.groups).toEqual([]);
    expect(r.fields).toEqual([
      { tag: 11, value: 'ORD-1' }, { tag: 55, value: 'AAPL' }, { tag: 38, value: '100' },
    ]);
    expect(r.skipped).toBe(3);
  });

  it('does not swallow a trailing top-level field whose tag was also used inside the group', () => {
    // Both legs carry tag 624; a top-level 624 immediately follows the completed group.
    // Once declared entry count is reached, the group is done regardless of which tags
    // were seen inside it — the trailing 624 must fall through to top-level fields.
    const r = parseFIXMessage(
      '8=FIXT.1.1|35=AB|555=2|600=EUR/USD|624=1|600=GBP/USD|624=2|624=99|11=ORD-1|',
    );
    expect(r.groups[0].entries).toHaveLength(2);
    expect(r.groups[0].entries[1].fields).toEqual([
      { tag: 600, value: 'GBP/USD' }, { tag: 624, value: '2' },
    ]);
    expect(r.fields).toContainEqual({ tag: 624, value: '99' });
    expect(r.fields.map((f) => f.tag)).toContain(11);
  });
});
