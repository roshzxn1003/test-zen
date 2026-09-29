import React, { useMemo } from 'react';
import {
  RotateCw,
  Mic,
  Users,
  User,
  Plus,
  Search,
  Cloud,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import { useFinance } from '../context/FinanceContext';

export const Header: React.FC = () => {
  const {
    userProfile,
    currentScope,
    setCurrentScope,
    setSelectedTab,
    syncState,
    syncNow,
    openVoiceModal,
    openAuthModal,
    openAddModal,
    familyMembers,
    isFirebaseAuthenticated
  } = useFinance();

  const isFamily = currentScope === 'FAMILY';

  const userInitial = userProfile?.fullName ? userProfile.fullName.charAt(0).toUpperCase() : 'Y';
  const firstName = userProfile?.fullName ? userProfile.fullName.split(' ')[0] : 'You';

  return (
    <header className="sticky top-0 z-30 w-full bg-[#080c14]/75 backdrop-blur-2xl backdrop-saturate-150 border-b border-white/[0.08] shadow-[0_4px_30px_rgba(0,0,0,0.5)] transition-all">
      {/* Top Rim Reflection Highlight */}
      <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/20 to-transparent pointer-events-none" />

      <div className="max-w-5xl lg:max-w-5xl xl:max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-2 sm:gap-4">
        {/* Left Section: Brand & Scope Switcher */}
        <div className="flex items-center gap-2.5 sm:gap-4 min-w-0">
          {/* Brand Logo & Name */}
          <button
            onClick={() => setSelectedTab(0)}
            className="flex items-center gap-2.5 group cursor-pointer text-left focus:outline-none flex-shrink-0"
            title="Zenith Home"
          >
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-cyan-400 p-[1.5px] shadow-[0_0_20px_-3px_rgba(99,102,241,0.5)] transition-transform group-hover:scale-105 active:scale-95">
              <div className="w-full h-full rounded-[10px] bg-[#0c1220] flex items-center justify-center">
                <span className="font-black text-transparent bg-clip-text bg-gradient-to-r from-indigo-200 via-white to-cyan-200 text-sm tracking-tighter">
                  Z
                </span>
              </div>
            </div>

            <div className="hidden sm:block">
              <div className="flex items-center gap-1.5 leading-none">
                <span className="text-sm font-black tracking-tight text-white group-hover:text-indigo-200 transition-colors">
                  Zenith
                </span>
                <span className="text-[9px] font-bold uppercase tracking-wider text-indigo-300 bg-indigo-500/15 px-1.5 py-0.5 rounded border border-indigo-400/25">
                  CashFlow
                </span>
              </div>
            </div>
          </button>

          {/* Scope Switcher Segmented Control */}
          <div className="flex items-center p-0.5 rounded-xl bg-slate-900/80 border border-white/10 shadow-inner flex-shrink-0">
            <button
              onClick={() => setCurrentScope('PERSONAL')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                !isFamily
                  ? 'bg-gradient-to-r from-indigo-600 to-indigo-500 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
              title="Switch to Personal Ledger"
            >
              <User className="w-3.5 h-3.5" />
              <span className="text-[11px]">Personal</span>
            </button>
            <button
              onClick={() => setCurrentScope('FAMILY')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                isFamily
                  ? 'bg-gradient-to-r from-cyan-400 to-cyan-500 text-slate-950 shadow-sm font-extrabold'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
              title="Switch to Family Vault"
            >
              <Users className="w-3.5 h-3.5" />
              <span className="text-[11px]">Family</span>
              {familyMembers.length > 0 && (
                <span className="px-1 py-0.2 rounded-full text-[9px] bg-slate-900/50 text-current font-extrabold">
                  {familyMembers.length}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* Center Section: Quick Search Trigger (Desktop/Tablet) */}
        <div className="hidden md:flex items-center flex-1 max-w-xs mx-2">
          <button
            onClick={() => setSelectedTab(1)}
            className="w-full h-9 px-3 rounded-xl bg-white/[0.04] hover:bg-white/[0.07] border border-white/10 hover:border-white/20 text-slate-400 hover:text-slate-200 flex items-center justify-between text-xs transition-all cursor-pointer group"
          >
            <div className="flex items-center gap-2">
              <Search className="w-3.5 h-3.5 text-slate-400 group-hover:text-indigo-300 transition-colors" />
              <span>Search transactions...</span>
            </div>
            <kbd className="px-1.5 py-0.5 rounded text-[10px] bg-white/5 border border-white/10 text-slate-400">
              Activity
            </kbd>
          </button>
        </div>

        {/* Right Section: Action Controls Suite */}
        <div className="flex items-center gap-1.5 sm:gap-2.5 flex-shrink-0">
          {/* Voice AI Trigger */}
          <button
            onClick={openVoiceModal}
            className="flex items-center gap-1.5 h-9 px-2.5 sm:px-3 rounded-xl bg-indigo-500/10 hover:bg-indigo-500/20 border border-indigo-500/30 text-indigo-300 hover:text-white transition-all text-xs font-semibold cursor-pointer shadow-sm"
            title="Voice AI Assistant"
          >
            <Mic className="w-3.5 h-3.5 text-indigo-400" />
            <span className="hidden sm:inline text-xs">Voice AI</span>
          </button>

          {/* Cloud Sync Status Button */}
          <button
            onClick={syncNow}
            disabled={syncState === 'SYNCING'}
            className={`h-9 px-2.5 rounded-xl border flex items-center gap-1.5 text-xs font-medium transition-all cursor-pointer ${
              syncState === 'SYNCING'
                ? 'bg-cyan-500/15 border-cyan-400/30 text-cyan-300'
                : syncState === 'ERROR'
                ? 'bg-rose-500/15 border-rose-400/30 text-rose-300'
                : isFirebaseAuthenticated
                ? 'bg-emerald-500/10 border-emerald-500/25 text-emerald-300 hover:bg-emerald-500/20'
                : 'bg-white/5 border-white/10 text-slate-300 hover:text-white hover:bg-white/10'
            }`}
            title={isFirebaseAuthenticated ? "Firebase Cloud Sync: Active" : "Local mode (Click to sync)"}
          >
            <RotateCw
              className={`w-3.5 h-3.5 ${
                syncState === 'SYNCING' ? 'animate-spin text-cyan-400' : 'text-slate-400'
              }`}
            />
            <span className="hidden lg:inline text-[11px]">
              {syncState === 'SYNCING' ? 'Syncing...' : isFirebaseAuthenticated ? 'Cloud' : 'Local'}
            </span>
          </button>

          {/* Quick Add Transaction Button */}
          <button
            onClick={() => openAddModal({ financeScope: currentScope })}
            className="flex items-center gap-1.5 h-9 px-3 sm:px-3.5 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 text-white font-bold text-xs shadow-md shadow-indigo-600/30 hover:scale-[1.02] active:scale-[0.98] transition-all cursor-pointer"
            title="Log new transaction"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span className="hidden xs:inline">Add</span>
          </button>

          {/* User Profile Avatar / Auth Trigger */}
          <button
            onClick={openAuthModal}
            className="group relative flex-shrink-0 cursor-pointer focus:outline-none rounded-full ml-0.5"
            title={isFirebaseAuthenticated ? `Signed in as ${firstName}` : 'Sign in with Google / Account Settings'}
          >
            <div className="w-9 h-9 rounded-full p-[1.5px] bg-gradient-to-tr from-indigo-500 via-cyan-400 to-indigo-600 group-hover:scale-105 transition-transform shadow-sm">
              {userProfile?.avatarUrl ? (
                <img
                  src={userProfile.avatarUrl}
                  alt={userProfile.fullName}
                  className="w-full h-full rounded-full object-cover"
                />
              ) : (
                <div className="w-full h-full rounded-full bg-slate-900 flex items-center justify-center">
                  <span className="text-white text-xs font-black">{userInitial}</span>
                </div>
              )}
            </div>

            {/* Cloud Status Dot */}
            <span
              className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-[#080c14] ${
                isFirebaseAuthenticated ? 'bg-emerald-400 shadow-[0_0_6px_#34d399]' : 'bg-amber-400'
              }`}
              title={isFirebaseAuthenticated ? 'Firebase Cloud Connected' : 'Guest Mode (Local Storage)'}
            />
          </button>
        </div>
      </div>
    </header>
  );
};
