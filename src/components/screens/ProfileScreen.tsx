import React, { useRef, useState } from 'react';
import {
  User,
  Users,
  Coins,
  Download,
  Upload,
  FileSpreadsheet,
  RotateCcw,
  Shield,
  Copy,
  Check,
  LogOut,
  Info
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { StorageService } from '../../services/storage';

const CURRENCIES = [
  { symbol: '₹', code: 'INR', name: 'Indian Rupee' },
  { symbol: '$', code: 'USD', name: 'US Dollar' },
  { symbol: '€', code: 'EUR', name: 'Euro' },
  { symbol: '£', code: 'GBP', name: 'British Pound' },
  { symbol: '¥', code: 'JPY', name: 'Japanese Yen' },
  { symbol: 'د.إ', code: 'AED', name: 'UAE Dirham' },
  { symbol: 'S$', code: 'SGD', name: 'Singapore Dollar' }
];

export const ProfileScreen: React.FC = () => {
  const {
    userProfile,
    currencySymbol,
    setCurrencySymbol,
    familyVault,
    familyMembers,
    openFamilyModal,
    openAuthModal,
    resetAllData
  } = useFinance();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [copiedCode, setCopiedCode] = useState(false);
  const [importStatus, setImportStatus] = useState<string | null>(null);

  const handleCopyCode = () => {
    navigator.clipboard.writeText(familyVault.inviteCode);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const handleExportJson = () => {
    const jsonStr = StorageService.exportBackupJson();
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `zenith_cashflow_backup_${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleExportCsv = () => {
    const csvStr = StorageService.exportTransactionsCsv();
    const blob = new Blob([csvStr], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `zenith_transactions_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleFileImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      const success = StorageService.importBackupJson(content);
      if (success) {
        setImportStatus('Backup restored successfully!');
        setTimeout(() => window.location.reload(), 1000);
      } else {
        setImportStatus('Failed to restore backup. Invalid format.');
      }
    };
    reader.readAsText(file);
  };

  const handleReset = () => {
    if (confirm('Are you sure you want to reset all data back to initial defaults?')) {
      resetAllData();
      alert('Data reset successfully.');
    }
  };

  return (
    <div className="space-y-4 pb-24">
      {/* 1. User Profile Card */}
      <div className="p-5 rounded-3xl bg-slate-900/80 border border-white/10 shadow-lg space-y-4">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-full p-[2px] bg-gradient-to-tr from-indigo-500 via-cyan-400 to-indigo-600 flex-shrink-0">
            {userProfile.avatarUrl ? (
              <img
                src={userProfile.avatarUrl}
                alt={userProfile.fullName}
                className="w-full h-full rounded-full object-cover"
              />
            ) : (
              <div className="w-full h-full rounded-full bg-slate-800 flex items-center justify-center">
                <span className="text-xl font-black text-white">
                  {userProfile.fullName ? userProfile.fullName.charAt(0).toUpperCase() : 'Y'}
                </span>
              </div>
            )}
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-base font-black text-white truncate">{userProfile.fullName}</h2>
              {userProfile.isGuest ? (
                <span className="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 text-[10px] font-bold">
                  Guest / Local Mode
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 text-[10px] font-bold flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                  Firebase Synced
                </span>
              )}
            </div>
            <p className="text-xs text-slate-400 truncate">{userProfile.email}</p>
          </div>

          <button
            onClick={openAuthModal}
            className="px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 hover:text-white text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5"
            title="Account Settings"
          >
            {userProfile.isGuest ? 'Sign In' : 'Account'}
          </button>
        </div>

        {userProfile.isGuest && (
          <div className="pt-2 border-t border-white/10 flex items-center justify-between">
            <p className="text-[11px] text-slate-400">
              Connect Google account to sync records via Firebase Cloud
            </p>
            <button
              onClick={openAuthModal}
              className="px-3 py-1 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-[11px] font-bold transition-all cursor-pointer flex-shrink-0"
            >
              Connect Cloud
            </button>
          </div>
        )}
      </div>

      {/* 2. Currency Selector */}
      <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-3">
        <div className="flex items-center gap-2">
          <Coins className="w-4 h-4 text-indigo-400" />
          <h3 className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Default Currency</h3>
        </div>

        <div className="grid grid-cols-4 sm:grid-cols-7 gap-2">
          {CURRENCIES.map(curr => {
            const isSelected = currencySymbol === curr.symbol;
            return (
              <button
                key={curr.code}
                onClick={() => setCurrencySymbol(curr.symbol)}
                className={`py-2 px-2 rounded-xl border flex flex-col items-center justify-center transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-indigo-600 border-indigo-400 text-white font-extrabold shadow-md'
                    : 'bg-white/5 border-white/10 text-slate-300 hover:bg-white/10'
                }`}
              >
                <span className="text-base font-bold">{curr.symbol}</span>
                <span className="text-[10px] text-slate-400">{curr.code}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* 3. Family Vault Management Card */}
      <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Users className="w-4 h-4 text-cyan-400" />
            <h3 className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Family Vault</h3>
          </div>
          <button
            onClick={openFamilyModal}
            className="text-xs font-bold text-cyan-400 hover:underline cursor-pointer"
          >
            Manage Members
          </button>
        </div>

        <div className="p-3 rounded-2xl bg-slate-950/60 border border-white/5 flex items-center justify-between">
          <div>
            <p className="text-sm font-bold text-white">{familyVault.name}</p>
            <p className="text-xs text-slate-400">{familyMembers.length} active family members</p>
          </div>

          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-500/10 border border-cyan-500/20">
            <span className="font-mono text-xs font-bold text-cyan-300">{familyVault.inviteCode}</span>
            <button
              onClick={handleCopyCode}
              className="p-1 rounded text-cyan-300 hover:text-white cursor-pointer"
              title="Copy Invite Code"
            >
              {copiedCode ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
          </div>
        </div>
      </div>

      {/* 4. Data Backup & Export / Restore */}
      <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-3">
        <div className="flex items-center gap-2">
          <Shield className="w-4 h-4 text-emerald-400" />
          <h3 className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Data & Backup</h3>
        </div>

        {importStatus && (
          <p className="text-xs font-bold text-emerald-400 bg-emerald-500/10 p-2 rounded-xl border border-emerald-500/20">
            {importStatus}
          </p>
        )}

        <div className="grid grid-cols-2 gap-2">
          <button
            onClick={handleExportJson}
            className="p-3 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 text-left space-y-1 transition-all cursor-pointer"
          >
            <Download className="w-4 h-4 text-indigo-400" />
            <p className="text-xs font-bold text-white">Export Backup (JSON)</p>
            <p className="text-[10px] text-slate-400">Full offline data backup</p>
          </button>

          <button
            onClick={handleExportCsv}
            className="p-3 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 text-left space-y-1 transition-all cursor-pointer"
          >
            <FileSpreadsheet className="w-4 h-4 text-emerald-400" />
            <p className="text-xs font-bold text-white">Export CSV</p>
            <p className="text-[10px] text-slate-400">Excel / Spreadsheet format</p>
          </button>
        </div>

        <div>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileImport}
            accept=".json"
            className="hidden"
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            className="w-full p-3 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 flex items-center justify-center gap-2 text-xs font-bold text-slate-300 transition-all cursor-pointer"
          >
            <Upload className="w-4 h-4 text-cyan-400" />
            <span>Restore Backup from JSON</span>
          </button>
        </div>
      </div>

      {/* 5. Danger Zone */}
      <div className="p-4 rounded-3xl bg-slate-900/80 border border-rose-500/20 space-y-3">
        <h3 className="text-xs font-extrabold uppercase tracking-wider text-rose-400">Reset Application</h3>
        <p className="text-xs text-slate-400">
          Reset all transactions, budgets, goals, and family vault back to the default sample dataset.
        </p>

        <button
          onClick={handleReset}
          className="w-full py-2.5 rounded-2xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-400 text-xs font-bold flex items-center justify-center gap-2 transition-all cursor-pointer"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          <span>Reset All Data to Defaults</span>
        </button>
      </div>

      {/* 6. About */}
      <div className="text-center py-4 space-y-1 text-slate-500 text-xs">
        <div className="flex items-center justify-center gap-1.5 text-slate-400 font-bold">
          <Info className="w-3.5 h-3.5" />
          <span>Zenith CashFlow v1.0.0</span>
        </div>
        <p>Intelligent Personal & Family Shared Finance</p>
      </div>
    </div>
  );
};
