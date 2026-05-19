export interface ParsedFIX {
  msgType?: string;
  fields: Array<{ tag: number; value: string }>;
  skipped: number;
}

// Tags managed by QuickFIX/J engine — skip on paste
export const ENGINE_TAGS = new Set([8, 9, 10, 34, 49, 52, 56]);

export function parseFIXMessage(raw: string): ParsedFIX {
  const normalized = raw.replace(/\x01/g, '|');
  const segments = normalized.split('|').map(s => s.trim()).filter(Boolean);

  let msgType: string | undefined;
  const fields: Array<{ tag: number; value: string }> = [];
  let skipped = 0;

  for (const seg of segments) {
    const eq = seg.indexOf('=');
    if (eq < 0) { skipped++; continue; }
    const tag = parseInt(seg.slice(0, eq).trim(), 10);
    const value = seg.slice(eq + 1).trim();
    if (isNaN(tag) || tag <= 0) { skipped++; continue; }
    if (tag === 35) { msgType = value; continue; }
    if (ENGINE_TAGS.has(tag)) { skipped++; continue; }
    fields.push({ tag, value });
  }

  return { msgType, fields, skipped };
}
