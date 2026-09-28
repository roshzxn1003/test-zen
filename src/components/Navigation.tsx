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
    <div className="fixed bottom-0 left-0 right-0 z-40 pointer-events-none pb-4 pt-2 px-4 max-w-lg mx-auto">
      <nav
        aria-label="Bottom Navigation"
        className="pointer-events-auto h-16 w-full rounded-full bg-[#0b101d]/90 backdrop-blur-xl border border-white/10 shadow-[0_20px_35px_-5px_rgba(0,0,0,0.6)] flex items-center justify-around px-2"
      >
        {NAV_TABS.map(tab => {
          const isSelected = selectedTab === tab.id;
          const Icon = tab.icon;

          return (
            <button
              key={tab.id}
              onClick={() => setSelectedTab(tab.id)}
              data-testid={tab.testTag}
              className={`relative flex flex-col items-center justify-center flex-1 h-12 rounded-full transition-all duration-200 cursor-pointer ${
                isSelected
                  ? 'bg-indigo-500/20 text-indigo-300 scale-105'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Icon className={`w-5 h-5 transition-transform duration-200 ${isSelected ? 'stroke-[2.5px]' : 'stroke-[1.8px]'}`} />
              <span className={`text-[10px] mt-0.5 tracking-tight font-medium ${isSelected ? 'font-bold text-indigo-200' : ''}`}>
                {tab.label}
              </span>
            </button>
          );
        })}
      </nav>
    </div>
  );
};
