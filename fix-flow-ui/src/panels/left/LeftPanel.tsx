import { NodePalette } from './NodePalette';
import { ScenarioList } from './ScenarioList';

export default function LeftPanel() {
  return (
    <div className="bg-[#1a1d27] border-r border-[#2a2d3a] flex flex-col overflow-hidden">
      <div className="flex-1 min-h-0 overflow-hidden">
        <NodePalette />
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <ScenarioList />
      </div>
    </div>
  );
}
