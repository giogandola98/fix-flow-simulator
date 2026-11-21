import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useExecutionStore } from '../store/executionStore';
import { answerOperator } from '../api/executions';

/** Shown while the active execution waits for a person (OPERATOR node): confirm or fail with a note. */
export default function OperatorPrompt() {
  const { t } = useTranslation();
  const prompt = useExecutionStore((s) => s.operatorPrompt);
  const setPrompt = useExecutionStore((s) => s.setOperatorPrompt);
  const refresh = useExecutionStore((s) => s.refreshOperatorPrompt);
  const activeId = useExecutionStore((s) => s.activeExecutionId);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);

  if (!prompt) return null;

  const answer = async (ok: boolean) => {
    setBusy(true);
    setError(false);
    try {
      await answerOperator(prompt.executionId, prompt.nodeId, ok, note);
      setNote('');
      // Ask the server what is open now instead of clearing: the answered event may already have
      // brought the next sibling prompt, which a blind clear would wipe.
      if (activeId) await refresh(activeId); else setPrompt(null);
    } catch {
      // A 404 means the prompt is already gone (timeout, stop, answered elsewhere); the next event clears it.
      setError(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div role="dialog" aria-label={t('operator.title')} data-testid="operator-prompt"
      className="fixed bottom-4 right-4 z-50 w-96 bg-[#1a1d27] border-2 border-rose-500 rounded shadow-xl p-3 text-xs space-y-2">
      <div className="text-rose-300 font-semibold">{t('operator.title')}</div>
      <div className="whitespace-pre-wrap text-gray-100 text-sm">{prompt.instruction}</div>
      <div>
        <label htmlFor="operator-note" className="text-[10px] text-gray-500">{t('operator.note')}</label>
        <textarea id="operator-note" rows={2}
          className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
          value={note} onChange={(e) => setNote(e.target.value)} />
      </div>
      {error && <div className="text-red-400">{t('operator.error')}</div>}
      <div className="flex gap-2 justify-end">
        <button disabled={busy} onClick={() => answer(false)}
          className="px-3 py-1 bg-red-700 hover:bg-red-600 rounded disabled:opacity-50">
          {prompt.failLabel || t('operator.fail')}
        </button>
        <button disabled={busy} onClick={() => answer(true)}
          className="px-3 py-1 bg-green-700 hover:bg-green-600 rounded disabled:opacity-50">
          {prompt.confirmLabel || t('operator.confirm')}
        </button>
      </div>
    </div>
  );
}
