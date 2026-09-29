import React from 'react';
import { FinanceProvider, useFinance } from './context/FinanceContext';
import { Header } from './components/Header';
import { ZenithFloatingNavigationBar } from './components/Navigation';
import { HomeScreen } from './components/screens/HomeScreen';
import { TransactionsScreen } from './components/screens/TransactionsScreen';
import { BudgetsAndGoalsScreen } from './components/screens/BudgetsAndGoalsScreen';
import { AnalyticsScreen } from './components/screens/AnalyticsScreen';
import { ProfileScreen } from './components/screens/ProfileScreen';

// Modals
import { AddTransactionModal } from './components/modals/AddTransactionModal';
import { VoiceAiModal } from './components/modals/VoiceAiModal';
import { ReceiptScanModal } from './components/modals/ReceiptScanModal';
import { UPIPayModal } from './components/modals/UPIPayModal';
import { FamilyMembersModal } from './components/modals/FamilyMembersModal';
import { TransactionDetailModal } from './components/modals/TransactionDetailModal';
import { AuthModal } from './components/modals/AuthModal';

const MainAppContent: React.FC = () => {
  const { selectedTab } = useFinance();

  return (
    <div className="relative min-h-screen bg-[#080c14] text-slate-100 flex flex-col justify-between selection:bg-indigo-500/30 selection:text-indigo-200">
      {/* Dynamic ambient background glow */}
      <div className="fixed top-0 left-1/2 -translate-x-1/2 w-full max-w-5xl h-[420px] bg-gradient-to-b from-indigo-950/35 via-cyan-950/10 to-transparent pointer-events-none -z-10 blur-3xl opacity-75" />
      <div className="fixed -top-24 right-10 w-96 h-96 bg-purple-900/10 rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="fixed top-1/3 -left-20 w-80 h-80 bg-cyan-950/15 rounded-full blur-3xl pointer-events-none -z-10" />

      {/* Sticky Full-Width Fintech Header */}
      <Header />

      {/* Main Container - Scalable & Responsive across mobile, tablet, and desktop */}
      <main className="w-full max-w-3xl lg:max-w-4xl xl:max-w-5xl mx-auto px-4 sm:px-6 flex-1 flex flex-col">
        {/* Tab Views */}
        <div className="flex-1 mt-2 sm:mt-4 pb-28 sm:pb-32">
          {selectedTab === 0 && <HomeScreen />}
          {selectedTab === 1 && <TransactionsScreen />}
          {selectedTab === 2 && <BudgetsAndGoalsScreen />}
          {selectedTab === 3 && <AnalyticsScreen />}
          {selectedTab === 4 && <ProfileScreen />}
        </div>
      </main>

      {/* Floating Bottom Navigation Bar */}
      <ZenithFloatingNavigationBar />

      {/* Global Dialogs & Modals */}
      <AddTransactionModal />
      <VoiceAiModal />
      <ReceiptScanModal />
      <UPIPayModal />
      <FamilyMembersModal />
      <TransactionDetailModal />
      <AuthModal />
    </div>
  );
};

export function App() {
  return (
    <FinanceProvider>
      <MainAppContent />
    </FinanceProvider>
  );
}

export default App;
