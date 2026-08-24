import { describe, expect, it } from 'vitest';
import en from './locales/en.json';
import itLocale from './locales/it.json';
import fr from './locales/fr.json';

const REQUIRED = [
  'nodeConfig.sendFix.groups',
  'nodeConfig.sendFix.addGroup',
  'nodeConfig.sendFix.addEntry',
  'nodeConfig.sendFix.entryFields',
  'nodeConfig.sendFix.addSubGroup',
  'nodeConfig.sendFix.nestingLimit',
  'topbar.shutdown',
  'topbar.shutdownConfirm',
  'topbar.shutdownDone',
  'topbar.shutdownFailed',
];

const at = (obj: unknown, path: string) =>
  path.split('.').reduce<unknown>((acc, k) => (acc as Record<string, unknown>)?.[k], obj);

describe('locales', () => {
  it.each([['en', en], ['it', itLocale], ['fr', fr]])('%s defines every required key', (_name, bundle) => {
    REQUIRED.forEach((key) => {
      expect(at(bundle, key), `missing ${key}`).toBeTruthy();
    });
  });

  it('all three locales have identical key sets', () => {
    const keys = (o: unknown, prefix = ''): string[] =>
      Object.entries(o as Record<string, unknown>).flatMap(([k, v]) =>
        typeof v === 'object' && v !== null ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`]);
    expect(keys(itLocale).sort()).toEqual(keys(en).sort());
    expect(keys(fr).sort()).toEqual(keys(en).sort());
  });
});
