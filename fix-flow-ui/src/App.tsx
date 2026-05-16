import TopBar from './components/TopBar';
import FlowCanvas from './canvas/FlowCanvas';
import LeftPanel from './panels/left/LeftPanel';
import RightPanel from './panels/right/RightPanel';
import BottomPanel from './panels/bottom/BottomPanel';

export default function App() {
  return (
    <div className="h-screen flex flex-col bg-[#0f1117] text-gray-100">
      <TopBar />
      <div
        className="flex-1 overflow-hidden"
        style={{
          display: 'grid',
          gridTemplateColumns: '240px 1fr 320px',
          gridTemplateRows: '1fr',
        }}
      >
        <LeftPanel />
        <FlowCanvas />
        <RightPanel />
      </div>
      <BottomPanel />
    </div>
  );
}
