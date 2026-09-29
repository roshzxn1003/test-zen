import React from 'react';
import { Home, ReceiptText, PieChart, BarChart3, User } from 'lucide-react';
import { useFinance } from '../context/FinanceContext';

const NAV_TABS = [
  { id: 0, label: 'Home', icon: Home, testTag: 'nav_home' },
  { id: 1, label: 'Activity', icon: ReceiptText, testTag: 'nav_transactions' },
  { id: 2, label: 'Budgets', icon: PieChart, testTag: 'nav_budgets' },
  { id: 3, label: 'Analytics', icon: BarChart3, testTag: 'nav_analytics' },
  { id: 4, label: 'Profile', icon: User, testTag: 'nav_profile' }
];

export const ZenithFloatingNavigationBar: React.FC = () => {
  const { selectedTab, setSelectedTab } = useFinance();

  return (
    <div className="fixed bottom-4 sm:bottom-6 left-0 right-0 z-40 pointer-events-none px-4 flex justify-center">
      <nav
        aria-label="Bottom Navigation"
        className="pointer-events-auto relative w-full max-w-md sm:max-w-lg h-16 rounded-full glass-dock flex items-center justify-between p-1.5 transition-all duration-300"
      >
        {/* Ambient Top Rim Reflection Highlight */}
        <div className="absolute inset-x-6 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/40 to-transparent pointer-events-none rounded-full" />

        {NAV_TABS.map(tab => {
          const isSelected = selectedTab === tab.id;
          const Icon = tab.icon;

          return (
            <button
              key={tab.id}
              onClick={() => setSelectedTab(tab.id)}
              data-testid={tab.testTag}
              className={`relative flex flex-col items-center justify-center flex-1 h-full rounded-full transition-all duration-300 cursor-pointer select-none group ${
                isSelected
                  ? 'glass-dock-item-active text-white'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.06]'
              }`}
            >
              <div className="relative">
                <Icon
                  className={`w-5 h-5 transition-all duration-300 ${
                    isSelected
                      ? 'stroke-[2.5px] scale-110 text-indigo-300 drop-shadow-[0_0_8px_rgba(99,102,241,0.6)]'
                      : 'stroke-[1.8px] group-hover:scale-105'
                  }`}
                />

                {/* Glowing Active Dot */}
                {isSelected && (
                  <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1.5 h-1.5 rounded-full bg-cyan-400 shadow-[0_0_8px_#22d3ee]" />
                )}
              </div>

              <span
                className={`text-[10px] mt-0.5 tracking-tight transition-all duration-200 ${
                  isSelected
                    ? 'font-extrabold text-white text-shadow-sm'
                    : 'font-medium group-hover:text-slate-300'
                }`}
              >
                {tab.label}
              </span>
            </button>
          );
        })}
      </nav>
    </div>
  );
};
