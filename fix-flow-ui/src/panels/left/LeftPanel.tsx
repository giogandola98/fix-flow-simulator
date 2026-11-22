import { useTranslation } from 'react-i18next';
import { NodePalette } from './NodePalette';
import { ScenarioList } from './ScenarioList';
import { useDataSetStore } from '../../store/dataSetStore';
import { usePackStore } from '../../store/packStore';

const pageButton = 'border-t border-[#2a2d3a] px-3 py-2 text-xs text-left text-gray-300 hover:bg-[#242736]';

export default function LeftPanel() {
  const { t } = useTranslation();
  const openDataSets = useDataSetStore((s) => s.open);
  const openPacks = usePackStore((s) => s.open);
  return (
    <div className="bg-[#1a1d27] border-r border-[#2a2d3a] flex flex-col overflow-hidden">
      <div className="flex-1 min-h-0 overflow-hidden">
        <NodePalette />
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <ScenarioList />
      </div>
      <button className={pageButton} onClick={openDataSets}>
        {t('datasets.open')}
      </button>
      <button className={pageButton} onClick={openPacks}>
        {t('packs.open')}
      </button>
    </div>
  );
}
