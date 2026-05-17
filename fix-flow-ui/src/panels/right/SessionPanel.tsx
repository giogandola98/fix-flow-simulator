import { useEffect, useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getSessions,
  createSession,
  updateSession,
  connectSession,
  disconnectSession,
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
  heartbeatInterval: 30, reconnectInterval: 5, resetOnLogon: true, resetOnLogout: false,
};

export function SessionPanel() {
  const queryClient = useQueryClient();
  const setSessions = useSessionStore((s) => s.setSessions);
  const activeSession = useSessionStore((s) => s.activeSession);
  const setActiveSession = useSessionStore((s) => s.setActiveSession);

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
        heartbeatInterval: activeSession.heartbeatInterval, reconnectInterval: activeSession.reconnectInterval,
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

  const connected = activeSession?.connected ?? false;
  const connecting = connectMutation.isPending;

  return (
    <div className="p-2 overflow-y-auto border-t border-[#2a2d3a]">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs uppercase tracking-wider text-gray-500">Session</div>
        <div className={`px-2 py-0.5 rounded text-[10px] ${
          connected ? 'bg-green-700 text-white' :
          connecting ? 'bg-yellow-600 text-white' :
          'bg-gray-700 text-gray-300'
        }`}>
          {connected ? 'CONNECTED' : connecting ? 'CONNECTING...' : 'DISCONNECTED'}
        </div>
      </div>
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
        <Field label="Mode">
          <select className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('mode')}>
            {MODES.map((m) => <option key={m}>{m}</option>)}
          </select>
        </Field>
        <Field label="FIX Version">
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
        <Field label="SenderCompID">
          <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('senderCompID', { required: true })} />
        </Field>
        <Field label="TargetCompID">
          <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('targetCompID', { required: true })} />
        </Field>
        <Field label="Host">
          <input type="text" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('host')} />
        </Field>
        <Field label="Port">
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('port', { valueAsNumber: true })} />
        </Field>
        <Field label="Heartbeat Interval (sec)">
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('heartbeatInterval', { valueAsNumber: true })} />
        </Field>
        <Field label="Reconnect Interval (sec)">
          <input type="number" className="w-full bg-[#0f1117] border border-[#2a2d3a] rounded px-2 py-1" {...register('reconnectInterval', { valueAsNumber: true })} />
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
        </div>
      </form>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-[10px] text-gray-500">{label}</label>
      {children}
    </div>
  );
}
