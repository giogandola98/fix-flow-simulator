import { PropertiesPanel } from './PropertiesPanel';

export default function RightPanel() {
  return (
    <div className="bg-[#1a1d27] border-l border-[#2a2d3a] flex flex-col overflow-hidden">
      <div className="flex-1 min-h-0 overflow-y-auto">
        <PropertiesPanel />
      </div>
    </div>
  );
}
