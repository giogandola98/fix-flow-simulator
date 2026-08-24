import { GroupSpec, GroupEntry } from '../panels/right/NodeConfig/GroupEditor';

export interface ParsedFIX {
  msgType?: string;
  fields: Array<{ tag: number; value: string }>;
  groups: GroupSpec[];
  unknownCounters: number[];
  skipped: number;
}

// Tags managed by QuickFIX/J engine — skip on paste
export const ENGINE_TAGS = new Set([8, 9, 10, 34, 49, 52, 56]);

/** Counter tag -> delimiter tag (the first field of every entry). */
export const GROUP_DELIMITERS: Record<number, number> = {
  78: 79,     // NoAllocs   -> AllocAccount
  453: 448,   // NoPartyIDs -> PartyID
  555: 600,   // NoLegs     -> LegSymbol
  702: 703,   // NoPositions-> PosType
  711: 311,   // NoUnderlyings -> UnderlyingSymbol
  864: 865,   // NoEvents   -> EventType
};

export function parseFIXMessage(raw: string): ParsedFIX {
  const normalized = raw.replace(/\x01/g, '|');
  const segments = normalized.split('|').map((s) => s.trim()).filter(Boolean);

  let msgType: string | undefined;
  const fields: Array<{ tag: number; value: string }> = [];
  const groups: GroupSpec[] = [];
  const unknownCounters: number[] = [];
  let skipped = 0;

  let i = 0;
  while (i < segments.length) {
    const seg = segments[i];
    const eq = seg.indexOf('=');
    if (eq < 0) { skipped++; i++; continue; }

    const tag = parseInt(seg.slice(0, eq).trim(), 10);
    const value = seg.slice(eq + 1).trim();
    if (isNaN(tag) || tag <= 0) { skipped++; i++; continue; }
    if (tag === 35) { msgType = value; i++; continue; }
    if (ENGINE_TAGS.has(tag)) { skipped++; i++; continue; }

    const delimiter = GROUP_DELIMITERS[tag];
    if (delimiter !== undefined) {
      const declared = parseInt(value, 10);
      const consumed = readGroup(segments, i + 1, tag, delimiter, declared);
      if (consumed.entries.length === declared && declared > 0) {
        groups.push({ counterTag: tag, entries: consumed.entries });
        i = consumed.nextIndex;
        continue;
      }
      // Declared count and reconstructed entries disagree: fall back to flat,
      // and tell the user rather than silently producing a wrong message.
      unknownCounters.push(tag);
      i++;
      continue;
    }

    fields.push({ tag, value });
    i++;
  }

  return { msgType, fields, groups, unknownCounters, skipped };
}

function readGroup(
  segments: string[],
  start: number,
  _counterTag: number,
  delimiter: number,
  declared: number,
): { entries: GroupEntry[]; nextIndex: number } {
  const entries: GroupEntry[] = [];
  const seenTags = new Set<number>();
  let current: GroupEntry | null = null;
  let i = start;

  for (; i < segments.length; i++) {
    const eq = segments[i].indexOf('=');
    if (eq < 0) break;
    const tag = parseInt(segments[i].slice(0, eq).trim(), 10);
    const value = segments[i].slice(eq + 1).trim();
    if (isNaN(tag)) break;

    if (tag === delimiter) {
      if (entries.length === declared) break;      // group complete
      current = { fields: [] };
      entries.push(current);
      seenTags.add(tag);
      current.fields.push({ tag, value });
      continue;
    }
    if (current === null) break;                   // first tag was not the delimiter
    if (entries.length === declared && !seenTags.has(tag)) break;
    if (!seenTags.has(tag) && entries.length > 1) break;
    seenTags.add(tag);
    current.fields.push({ tag, value });
  }

  return { entries, nextIndex: i };
}
