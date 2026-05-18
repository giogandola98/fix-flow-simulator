import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getSessions,
  createSession,
  updateSession,
  connectSession,
  disconnectSession,
  deleteSession,
  getSession,
} from '../../api/sessions';
import { useSessionStore } from '../../store/sessionStore';
import { useSessionSubscription } from '../../hooks/useSessionSubscription';
import { FIXSessionCreateRequest, FIXVersion, FIXMode } from '../../types';

const FIX_VERSIONS: Array<{ value: FIXVersion; label: string }> = [
  { value: 'FIX_42', label: 'FIX 4.2' },
  { value: 'FIX_44', label: 'FIX 4.4' },
  { value: 'FIXT_11', label: 'FIX 5.0 SP2 (FIXT.1.1)' },
];
const MODES: FIXMode[] = ['INITIATOR', 'ACCEPTOR'];
type FormValues = FIXSessionCreateRequest;
const DEFAULTS: FormValues = {
  name: 'default', mode: 'INITIATOR', fixVersion: 'FIX_44', defaultApplVerID: 'FIX.5.0SP2',
  senderCompID: 'CLIENT', targetCompID: 'SERVER', host: 'localhost', port: 9876,
  heartbeatInterval: 30, resetOnLogon: true, resetOnLogout: false,
};

const STORAGE_KEY = 'fix-session-panel-collapsed';

export function SessionPanel() {
  const queryClient = useQueryClient();
  const setSessions = useSessionStore((s) => s.setSessions);
  const activeSession = useSessionStore((s) => s.activeSession);
  const setActiveSession = useSessionStore((s) => s.setActiveSession);

  const [collapsed, setCollapsed] = useState<boolean>(
    () => sessionStorage.getItem(STORAGE_KEY) === 'true',
  );

  const toggleCollapsed = () =>
    setCollapsed((prev) => {
      const next = !prev;
      sessionStorage.setItem(STORAGE_KEY, String(next));
      return next;
    });

  const { register, handleSubmit, reset, watch, setValue } = useForm<FormValues>({ defaultValues: DEFAULTS });
  const fixVersion = watch('fixVersion');

  const { data: sessions } = useQuery({ queryKey: ['sessions'], queryFn: getSessions });

  useEffect(() => { if (sessions) setSessions(sessions); }, [sessions, setSessions]);

  const sessionIds = useMemo(() => (sessions ?? []).map((s) => s.id), [sessions]);
  useSessionSubscription(sessionIds);

  useEffect(() => {
    if (activeSession) {
      reset({
        name: activeSession.name, mode: activeSession.mode, fixVersion: activeSession.fixVersion,
        defaultApplVerID: activeSession.defaultApplVerID, senderCompID: activeSession.senderCompID,
        targetCompID: activeSession.targetCompID, host: activeSession.host, port: activeSession.port,
        heartbeatInterval: activeSession.heartbeatInterval,
        resetOnLogon: activeSession.resetOnLogon, resetOnLogout: activeSession.resetOnLogout,
      });
    } else {
      reset(DEFAULTS);
    }
  }, [activeSession, reset]);

  const saveMutation = useMutation({
    mutationFn: async (values: FormValues) => {
      return activeSession ? updateSession(activeSession.id, values) : createSession(values);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(saved);
    },
  });

  const connectMutation = useMutation({
    mutationFn: async () => {
      if (!activeSession) throw new Error('Save session first');
      await connectSession(activeSession.id);
      return getSession(activeSession.id);
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(updated);
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: async () => {
      if (!activeSession) throw new Error('No session');
      await disconnectSession(activeSession.id);
      return getSession(activeSession.id);
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(updated);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async () => {
      if (!activeSession) return;
      if (activeSession.connected) {
        await disconnectSession(activeSession.id);
      }
      await deleteSession(activeSession.id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setActiveSession(null);
    },
    onError: (err) => console.error('Delete session failed:', err),
  });

  const connected = activeSession?.connected ?? false;
  const connecting = connectMutation.isPending;

  const statusDot = connected
    ? 'bg-green-500'
    : connecting
    ? 'bg-yellow-400 animate-pulse'
    : 'bg-gray-500';

  const statusLabel = connected ? 'CONNECTED' : connecting ? 'CONNECTING…' : 'DISCONNECTED';

  return (
    <div className="border-t border-[#2a2d3a]">
      {/* Header row — always visible */}
      <div
        className="flex items-center gap-2 px-2 py-1.5 cursor-pointer select-none hover:bg-[#1a1d27] transition-colors"
        onClick={toggleCollapsed}
        title={collapsed ? 'Expand session panel' : 'Collapse session panel'}
      >
        <span className={`w-2 h-2 rounded-full flex-shrink-0 ${statusDot}`} />
        <span className="text-xs text-gray-300 truncate flex-1 min-w-0">
          {activeSession?.name ?? 'No session'}
        </span>
        <span className={`text-[10px] font-medium flex-shrink-0 ${
          connected ? 'text-green-400' : connecting ? 'text-yellow-400' : 'text-gray-500'
        }`}>{statusLabel}</span>
        {/* Connect/Disconnect always accessible */}
        {activeSession && (
          <button
            type="button"
            className={`flex-shrink-0 px-1.5 py-0.5 rounded text-[10px] ${
              connected
                ? 'bg-red-700 hover:bg-red-600 text-white'
                : 'bg-green-700 hover:bg-green-600 text-white'
            }`}
            onClick={(e) => {
              e.stopPropagation();
              connected ? disconnectMutation.mutate() : connectMutation.mutate();
            }}
          >
            {connected ? 'Disconnect' : 'Connect'}
          </button>
        )}
        <svg
          className={`w-3 h-3 text-gray-500 flex-shrink-0 transition-transform duration-200 ${collapsed ? '' : 'rotate-180'}`}
          viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="2"
        >
          <polyline points="2,4 6,8 10,4" />
        </svg>
      </div>

      {/* Collapsible body */}
      <div
        className="overflow-hidden transition-all duration-200"
        style={{ maxHeight: collapsed ? 0 : 9999 }}
      >
        <div className="p-2 overflow-y-auto">
          <div className="mb-2">
            <label className="text-[10px] text-gray-500">Active session</label>
            <select
              className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1 text-xs"
              value={activeSession?.id ?? ''}
              onChange={(e) => {
                const s = (sessions ?? []).find((x) => x.id === e.target.value) ?? null;
                setActiveSession(s);
              }}
            >
              <option value="">-- new session --</option>
              {(sessions ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <form onSubmit={handleSubmit((v) => saveMutation.mutate(v))} className="text-xs space-y-2">
            <Field label="Name">
              <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('name', { required: true })} />
            </Field>
            <Field label="Mode" hint="INITIATOR connects to a counterparty. ACCEPTOR listens for incoming FIX connections.">
              <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('mode')}>
                {MODES.map((m) => <option key={m}>{m}</option>)}
              </select>
            </Field>
            <Field label="FIX Version" hint="FIX protocol version. FIX 4.4 most common. FIXT.1.1 (FIX 5.0 SP2) requires DefaultApplVerID.">
              <select
                className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1"
                disabled={connected}
                {...register('fixVersion', {
                  onChange: (e) => { if (e.target.value === 'FIXT_11') setValue('defaultApplVerID', 'FIX.5.0SP2'); },
                })}
              >
                {FIX_VERSIONS.map((v) => <option key={v.value} value={v.value}>{v.label}</option>)}
              </select>
              {connected && <div className="text-[10px] text-amber-400 mt-1">Disconnect before changing FIX version</div>}
            </Field>
            {fixVersion === 'FIXT_11' && (
              <Field label="DefaultApplVerID">
                <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('defaultApplVerID')} />
              </Field>
            )}
            <Field label="SenderCompID" hint="Your CompID — identifies this side of the FIX session (tag 49).">
              <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('senderCompID', { required: true })} />
            </Field>
            <Field label="TargetCompID" hint="Counterparty CompID — identifies the remote side (tag 56).">
              <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('targetCompID', { required: true })} />
            </Field>
            <Field label="Host" hint="IP or hostname of the ACCEPTOR. Only relevant for INITIATOR mode.">
              <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('host')} />
            </Field>
            <Field label="Port" hint="TCP port. ACCEPTOR listens; INITIATOR connects to it.">
              <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('port', { valueAsNumber: true })} />
            </Field>
            <Field label="Heartbeat Interval (sec)" hint="Seconds between heartbeat messages (FIX tag 108). Standard is 30.">
              <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('heartbeatInterval', { valueAsNumber: true })} />
            </Field>
            <label className="flex items-center gap-2"><input type="checkbox" {...register('resetOnLogon')} /> Reset on Logon</label>
            <label className="flex items-center gap-2"><input type="checkbox" {...register('resetOnLogout')} /> Reset on Logout</label>
            <div className="flex gap-1">
              <button type="submit" className="flex-1 px-2 py-1 rounded bg-gray-700 hover:bg-gray-600">Save</button>
              {connected ? (
                <button type="button" className="flex-1 px-2 py-1 rounded bg-red-600 hover:bg-red-500"
                  onClick={() => disconnectMutation.mutate()}>Disconnect</button>
              ) : (
                <button type="button" className="flex-1 px-2 py-1 rounded bg-green-600 hover:bg-green-500 disabled:opacity-40"
                  disabled={!activeSession} onClick={() => connectMutation.mutate()}>Connect</button>
              )}
              {activeSession && (
                <button
                  type="button"
                  className="px-2 py-1 rounded bg-red-900 hover:bg-red-800 text-red-300 text-xs disabled:opacity-40"
                  title="Delete this session"
                  disabled={deleteMutation.isPending}
                  onClick={() => {
                    if (window.confirm(`Delete session "${activeSession.name}"?`)) {
                      deleteMutation.mutate();
                    }
                  }}
                >
                  Del
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-[10px] text-gray-500">
        {label}
        {hint && <span title={hint} className="ml-1 text-gray-600 cursor-help">?</span>}
      </label>
      {children}
    </div>
  );
}
