import { PropertiesPanel } from './PropertiesPanel';
import { SessionPanel } from './SessionPanel';

export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] flex flex-col overflow-hidden">
      <div className="flex-1 min-h-0 overflow-y-auto pr-3">
        <PropertiesPanel />
      </div>
      {/* flex-shrink-0: shrinks to content; max-h-[55%]: prevents swallowing entire column */}
      <div className="flex-shrink-0 max-h-[55%] overflow-y-auto pr-3">
        <SessionPanel />
      </div>
    </div>
  );
}
