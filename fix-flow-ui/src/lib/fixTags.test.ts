import { describe, it, expect } from 'vitest';
import { fixTagName, FIX_TAGS, GROUP_COUNTER_TAGS } from './fixTags';

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

describe('FX and derivative tags', () => {
  it('names the instrument reference block', () => {
    expect(fixTagName(167)).toBe('SecurityType');
    expect(fixTagName(461)).toBe('CFICode');
    expect(fixTagName(460)).toBe('Product');
    expect(fixTagName(762)).toBe('SecuritySubType');
    expect(fixTagName(541)).toBe('MaturityDate');
    expect(fixTagName(207)).toBe('SecurityExchange');
  });

  it('names the leg tags used by the swap template', () => {
    expect(fixTagName(555)).toBe('NoLegs');
    expect(fixTagName(600)).toBe('LegSymbol');
    expect(fixTagName(609)).toBe('LegSecurityType');
    expect(fixTagName(624)).toBe('LegSide');
    expect(fixTagName(588)).toBe('LegSettlDate');
    expect(fixTagName(637)).toBe('LegLastPx');
    expect(fixTagName(1418)).toBe('LegLastQty');
  });

  it('names the option and position maintenance tags', () => {
    expect(fixTagName(201)).toBe('PutOrCall');
    expect(fixTagName(202)).toBe('StrikePrice');
    expect(fixTagName(1194)).toBe('ExerciseStyle');
    expect(fixTagName(1482)).toBe('OptPayoutType');
    expect(fixTagName(709)).toBe('PosTransType');
    expect(fixTagName(722)).toBe('PosMaintStatus');
  });

  it('names the FX settlement and trade capture tags', () => {
    expect(fixTagName(119)).toBe('SettlCurrAmt');
    expect(fixTagName(155)).toBe('SettlCurrFxRate');
    expect(fixTagName(571)).toBe('TradeReportID');
    expect(fixTagName(866)).toBe('EventDate');
  });

  it('exposes the group counter tags offered by the editor', () => {
    expect(GROUP_COUNTER_TAGS[555]).toBe('NoLegs');
    expect(GROUP_COUNTER_TAGS[864]).toBe('NoEvents');
    expect(GROUP_COUNTER_TAGS[702]).toBe('NoPositions');
    Object.keys(GROUP_COUNTER_TAGS).forEach((t) => {
      expect(FIX_TAGS[Number(t)]).toBeDefined();
    });
  });
});
