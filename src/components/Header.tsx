import React, { useMemo } from 'react';
import { Search, RotateCw, Mic, Users } from 'lucide-react';
import { useFinance } from '../context/FinanceContext';

export const Header: React.FC = () => {
  const {
    userProfile,
    currentScope,
    syncState,
    syncNow,
    setSelectedTab,
    openVoiceModal,
    familyName = 'Family Vault'
  } = useFinance() as any;

  const isFamily = currentScope === 'FAMILY';

  const timeGreeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour >= 5 && hour < 12) return 'Good morning';
    if (hour >= 12 && hour < 17) return 'Good afternoon';
    if (hour >= 17 && hour < 22) return 'Good evening';
    return 'Welcome back';
  }, []);

  const formattedDate = useMemo(() => {
    return new Intl.DateTimeFormat('en-US', {
      weekday: 'short',
      day: 'numeric',
      month: 'short'
    }).format(new Date());
  }, []);

  const userInitial = userProfile.fullName ? userProfile.fullName.charAt(0).toUpperCase() : 'Y';

  return (
    <header className="flex items-center justify-between py-2 pt-4">
      {/* Left: User Profile Avatar & Greeting */}
      <div
        onClick={() => setSelectedTab(4)}
        className="flex items-center gap-3 cursor-pointer group flex-1 min-w-0"
        title="View Profile"
      >
        <div className="relative flex-shrink-0">
          <div className="w-11 h-11 rounded-full p-[2px] bg-gradient-to-tr from-indigo-500 via-cyan-400 to-indigo-600 shadow-md">
            <div className="w-full h-full rounded-full bg-slate-800 flex items-center justify-center">
              {isFamily ? (
                <Users className="w-5 h-5 text-cyan-400" />
              ) : (
                <span className="text-white text-base font-black">{userInitial}</span>
              )}
            </div>
          </div>

          {/* Sync Dot */}
          <span
            className={`absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-slate-900 ${
              syncState === 'SYNCING'
                ? 'bg-cyan-400 animate-pulse'
                : syncState === 'ERROR'
                ? 'bg-amber-400'
                : 'bg-emerald-500'
            }`}
          />
        </div>

        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-semibold text-slate-400 truncate">
            {isFamily ? `Family Vault • ${formattedDate}` : `${timeGreeting} • ${formattedDate}`}
          </p>
          <h1 className="text-lg font-extrabold text-white tracking-tight truncate leading-tight group-hover:text-indigo-300 transition-colors">
            {isFamily ? 'Family Ledger' : userProfile.fullName}
          </h1>
        </div>
      </div>

      {/* Right Action Pills */}
      <div className="flex items-center gap-1.5 flex-shrink-0">
        {/* Quick Search */}
        <button
          onClick={() => setSelectedTab(1)}
          className="w-9 h-9 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center text-slate-300 hover:text-white hover:bg-white/10 transition-all cursor-pointer"
          title="Search Transactions"
        >
          <Search className="w-4 h-4" />
        </button>

        {/* Live Cloud Sync Pill */}
        <button
          onClick={syncNow}
          disabled={syncState === 'SYNCING'}
          className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl border text-xs font-bold transition-all cursor-pointer ${
            syncState === 'SYNCING'
              ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-300'
              : syncState === 'ERROR'
              ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
              : 'bg-white/5 border-white/10 text-slate-300 hover:bg-white/10'
          }`}
          title="Sync Cloud Data"
        >
          <RotateCw className={`w-3.5 h-3.5 ${syncState === 'SYNCING' ? 'animate-spin text-cyan-400' : 'text-slate-400'}`} />
          <span className="hidden sm:inline">{syncState === 'SYNCING' ? 'Syncing' : 'Synced'}</span>
        </button>

        {/* Voice AI Pill */}
        <button
          onClick={openVoiceModal}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-indigo-500/15 border border-indigo-500/30 text-indigo-300 hover:bg-indigo-500/25 transition-all text-xs font-bold shadow-sm cursor-pointer"
          title="Voice AI Assistant"
        >
          <Mic className="w-3.5 h-3.5 text-indigo-400" />
          <span>Voice</span>
        </button>
      </div>
    </header>
  );
};
