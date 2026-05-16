import { useMemo } from 'react';
import { useExecutionStore } from '../../store/executionStore';
import { ValidationError } from '../../types';

export function ValidationErrors() {
  const events = useExecutionStore((s) => s.events);

  const errors: ValidationError[] = useMemo(() => {
    const collected: ValidationError[] = [];
    for (const e of events) {
      if (e.type !== 'VALIDATION_FAILED' || !e.detail) continue;
      try {
        const parsed = JSON.parse(e.detail) as ValidationError | ValidationError[];
        if (Array.isArray(parsed)) collected.push(...parsed);
        else collected.push(parsed);
      } catch {
        collected.push({ tag: 0, rule: 'UNKNOWN', expected: '', actual: e.detail });
      }
    }
    return collected;
  }, [events]);

  return (
    <div className="h-full overflow-y-auto px-2 py-1">
      {errors.length === 0 && <div className="text-gray-500 italic text-xs">No validation errors</div>}
      <table className="w-full text-xs">
        <thead className="text-left text-gray-500">
          <tr>
            <th className="py-1 pr-2">Tag</th>
            <th className="py-1 pr-2">Rule</th>
            <th className="py-1 pr-2">Expected</th>
            <th className="py-1 pr-2">Actual</th>
            <th className="py-1">Message</th>
          </tr>
        </thead>
        <tbody>
          {errors.map((e, i) => (
            <tr key={i} className="border-t border-[#2a2d3a]">
              <td className="py-1 pr-2 text-amber-300">{e.tag}</td>
              <td className="py-1 pr-2 text-blue-300">{e.rule}</td>
              <td className="py-1 pr-2 text-green-300">{e.expected}</td>
              <td className="py-1 pr-2 text-red-300">{e.actual}</td>
              <td className="py-1 text-gray-300">{e.message ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
