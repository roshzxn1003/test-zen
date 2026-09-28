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
    <div className="relative min-h-screen bg-[#090d16] text-slate-100 flex flex-col justify-between selection:bg-indigo-500/30 selection:text-indigo-200">
      {/* Ambient background glow matching Android AmbientBackgroundBrush */}
      <div className="fixed top-0 left-1/2 -translate-x-1/2 w-full max-w-lg h-96 bg-gradient-to-b from-indigo-950/25 via-slate-900/10 to-transparent pointer-events-none -z-10 blur-3xl" />

      {/* Main Container constrained to mobile applet width */}
      <main className="w-full max-w-lg mx-auto px-4 flex-1 flex flex-col">
        {/* Top Header */}
        <Header />

        {/* Tab Views with fluid appearance */}
        <div className="flex-1 mt-2">
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
