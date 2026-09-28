import React, { useState, useMemo } from 'react';
import {
  Search,
  Plus,
  Filter,
  Mic,
  X,
  Calendar,
  CalendarDays,
  RotateCcw,
  SlidersHorizontal,
  ChevronDown,
  Tag,
  Check
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { ScopeSwitcher } from '../ScopeSwitcher';
import { TransactionItemCard } from '../TransactionItemCard';

type DatePreset = 'ALL' | 'TODAY' | 'THIS_WEEK' | 'THIS_MONTH' | 'LAST_30_DAYS' | 'THIS_YEAR' | 'CUSTOM';

export const TransactionsScreen: React.FC = () => {
  const {
    currentScope,
    categories,
    familyMembers,
    displayedTransactions,
    searchQuery,
    setSearchQuery,
    filterType,
    setFilterType,
    filterCategory,
    setFilterCategory,
    filterMember,
    setFilterMember,
    filterStartDate,
    filterEndDate,
    setFilterStartDate,
    setFilterEndDate,
    setDateRange,
    currencySymbol,
    openAddModal,
    openVoiceModal
  } = useFinance();

  const isFamily = currentScope === 'FAMILY';
  const [isDatePanelOpen, setIsDatePanelOpen] = useState(false);
  const [activeDatePreset, setActiveDatePreset] = useState<DatePreset>('ALL');

  // Compute total for filtered view
  const filteredTotal = useMemo(() => {
    return displayedTransactions.reduce((acc, t) => {
      return acc + (t.type === 'INCOME' ? t.amount : -t.amount);
    }, 0);
  }, [displayedTransactions]);

  // Compute quick total income and expense for the current filtered slice
  const { filteredIncome, filteredExpense } = useMemo(() => {
    let inc = 0;
    let exp = 0;
    displayedTransactions.forEach(t => {
      if (t.type === 'INCOME') inc += t.amount;
      else if (t.type === 'EXPENSE') exp += t.amount;
    });
    return { filteredIncome: inc, filteredExpense: exp };
  }, [displayedTransactions]);

  const toLocalDateString = (d: Date) => {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const applyDatePreset = (preset: DatePreset) => {
    setActiveDatePreset(preset);
    const now = new Date();

    if (preset === 'ALL') {
      setDateRange(null, null);
    } else if (preset === 'TODAY') {
      const todayStr = toLocalDateString(now);
      setDateRange(todayStr, todayStr);
    } else if (preset === 'THIS_WEEK') {
      const day = now.getDay();
      const diffToMonday = now.getDate() - (day === 0 ? 6 : day - 1);
      const monday = new Date(now.getFullYear(), now.getMonth(), diffToMonday);
      setDateRange(toLocalDateString(monday), toLocalDateString(now));
    } else if (preset === 'THIS_MONTH') {
      const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
      setDateRange(toLocalDateString(firstDay), toLocalDateString(now));
    } else if (preset === 'LAST_30_DAYS') {
      const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      setDateRange(toLocalDateString(thirtyDaysAgo), toLocalDateString(now));
    } else if (preset === 'THIS_YEAR') {
      const firstDayOfYear = new Date(now.getFullYear(), 0, 1);
      setDateRange(toLocalDateString(firstDayOfYear), toLocalDateString(now));
    }
  };

  const clearAllFilters = () => {
    setSearchQuery('');
    setFilterType('ALL');
    setFilterCategory(null);
    setFilterMember(null);
    setDateRange(null, null);
    setActiveDatePreset('ALL');
  };

  const hasDateFilter = Boolean(filterStartDate || filterEndDate);
  const hasActiveFilters = Boolean(
    searchQuery.trim() ||
    filterType !== 'ALL' ||
    filterCategory !== null ||
    filterMember !== null ||
    hasDateFilter
  );

  return (
    <div className="space-y-4 pb-24">
      {/* 1. Scope Switcher */}
      <ScopeSwitcher />

      {/* 2. Main Search & Filter Control Hub */}
      <div className="space-y-3 p-4 rounded-3xl bg-slate-900/95 border border-white/10 shadow-xl backdrop-blur-md">
        {/* Search Input Bar with Clear Button & Active Count Indicator */}
        <div className="relative flex items-center">
          <Search className="w-4 h-4 text-indigo-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Search by title, category, notes, or date (e.g. 'Coffee', 'Food', 'Sep 26')..."
            className="w-full pl-10 pr-20 py-2.5 rounded-2xl bg-slate-950/80 border border-white/15 text-white placeholder-slate-400 text-xs focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/30 transition-all font-medium"
          />
          <div className="absolute right-2.5 top-1/2 -translate-y-1/2 flex items-center gap-1">
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="p-1 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                title="Clear search query"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
            {hasActiveFilters && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                Filtered
              </span>
            )}
          </div>
        </div>

        {/* Quick Category Scroll Strip */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 no-scrollbar text-xs">
          <button
            onClick={() => setFilterCategory(null)}
            className={`px-2.5 py-1 rounded-xl text-xs font-bold whitespace-nowrap transition-all cursor-pointer flex items-center gap-1 ${
              filterCategory === null
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
            }`}
          >
            <span>All Categories</span>
          </button>
          {categories.slice(0, 8).map(c => {
            const isSelected = filterCategory?.toLowerCase() === c.name.toLowerCase();
            return (
              <button
                key={c.id}
                onClick={() => setFilterCategory(isSelected ? null : c.name)}
                className={`px-2.5 py-1 rounded-xl text-xs font-semibold whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
                  isSelected
                    ? 'bg-purple-600 text-white shadow-sm font-bold'
                    : 'bg-white/5 border border-white/10 text-slate-300 hover:text-white hover:bg-white/10'
                }`}
              >
                <span
                  className="w-2 h-2 rounded-full inline-block"
                  style={{ backgroundColor: c.colorHex || '#6366f1' }}
                />
                <span>{c.name}</span>
              </button>
            );
          })}
        </div>

        {/* Filter Toolbar: Type Toggle, Full Category Dropdown, Member, Date Range Button */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar text-xs">
          {/* Type Toggle: All */}
          <button
            onClick={() => setFilterType('ALL')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all cursor-pointer ${
              filterType === 'ALL'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
            }`}
          >
            All Types
          </button>

          {/* Type Toggle: Expenses */}
          <button
            onClick={() => setFilterType('EXPENSE')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all cursor-pointer ${
              filterType === 'EXPENSE'
                ? 'bg-rose-500 text-white shadow-md'
                : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
            }`}
          >
            Expenses
          </button>

          {/* Type Toggle: Income */}
          <button
            onClick={() => setFilterType('INCOME')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all cursor-pointer ${
              filterType === 'INCOME'
                ? 'bg-emerald-500 text-white shadow-md'
                : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
            }`}
          >
            Income
          </button>

          {/* Full Category Dropdown (for any custom or remaining categories) */}
          <div className="relative">
            <select
              value={filterCategory || ''}
              onChange={e => setFilterCategory(e.target.value || null)}
              aria-label="Filter by Category"
              className={`px-3 py-1.5 pr-7 rounded-xl border text-xs font-semibold focus:outline-none focus:border-indigo-500 cursor-pointer appearance-none ${
                filterCategory
                  ? 'bg-indigo-500/20 border-indigo-500/40 text-indigo-300 font-bold'
                  : 'bg-white/5 border-white/10 text-slate-300'
              }`}
            >
              <option value="" className="bg-slate-900 text-slate-300">More Categories...</option>
              {categories.map(c => (
                <option key={c.id} value={c.name} className="bg-slate-900 text-slate-200">
                  {c.name}
                </option>
              ))}
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>

          {/* Member Dropdown (Family Scope Only) */}
          {isFamily && (
            <div className="relative">
              <select
                value={filterMember || ''}
                onChange={e => setFilterMember(e.target.value || null)}
                aria-label="Filter by Family Member"
                className={`px-3 py-1.5 pr-7 rounded-xl border text-xs font-semibold focus:outline-none focus:border-cyan-500 cursor-pointer appearance-none ${
                  filterMember
                    ? 'bg-cyan-500/20 border-cyan-500/40 text-cyan-300 font-bold'
                    : 'bg-white/5 border-white/10 text-slate-300'
                }`}
              >
                <option value="" className="bg-slate-900 text-slate-300">All Members</option>
                {familyMembers.map(m => (
                  <option key={m.userId} value={m.userId} className="bg-slate-900 text-slate-200">
                    {m.name}
                  </option>
                ))}
              </select>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>
          )}

          {/* Date Filter Toggle Button */}
          <button
            onClick={() => setIsDatePanelOpen(!isDatePanelOpen)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl border font-bold text-xs whitespace-nowrap transition-all cursor-pointer ${
              hasDateFilter
                ? 'bg-amber-500/20 border-amber-500/40 text-amber-300'
                : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
            }`}
          >
            <CalendarDays className="w-3.5 h-3.5" />
            <span>
              {hasDateFilter
                ? `${filterStartDate || 'Start'} → ${filterEndDate || 'End'}`
                : 'Date Range'}
            </span>
            <ChevronDown className={`w-3 h-3 transition-transform ${isDatePanelOpen ? 'rotate-180' : ''}`} />
          </button>

          {/* Reset All Filters Button */}
          {hasActiveFilters && (
            <button
              onClick={clearAllFilters}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-300 font-bold text-xs whitespace-nowrap transition-all cursor-pointer"
              title="Reset all filters"
            >
              <RotateCcw className="w-3 h-3" />
              <span>Reset All</span>
            </button>
          )}
        </div>

        {/* Collapsible Date Range Controls & Presets Panel */}
        {isDatePanelOpen && (
          <div className="pt-2.5 mt-2 border-t border-white/10 space-y-3 animate-in fade-in slide-in-from-top-2 duration-200">
            {/* Quick Date Presets */}
            <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar pb-0.5">
              {[
                { id: 'ALL', label: 'All Time' },
                { id: 'TODAY', label: 'Today' },
                { id: 'THIS_WEEK', label: 'This Week' },
                { id: 'THIS_MONTH', label: 'This Month' },
                { id: 'LAST_30_DAYS', label: 'Last 30 Days' },
                { id: 'THIS_YEAR', label: 'This Year' }
              ].map(preset => (
                <button
                  key={preset.id}
                  onClick={() => applyDatePreset(preset.id as DatePreset)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-bold whitespace-nowrap transition-all cursor-pointer ${
                    activeDatePreset === preset.id && (!hasDateFilter || preset.id !== 'ALL')
                      ? 'bg-amber-500 text-slate-950 shadow-sm'
                      : 'bg-white/5 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>

            {/* Custom Date Pickers (From / To) */}
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
                  From Date
                </label>
                <div className="relative">
                  <input
                    type="date"
                    value={filterStartDate || ''}
                    onChange={e => {
                      setActiveDatePreset('CUSTOM');
                      setFilterStartDate(e.target.value || null);
                    }}
                    className="w-full px-3 py-1.5 rounded-xl bg-slate-950 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-amber-500"
                  />
                  {filterStartDate && (
                    <button
                      onClick={() => setFilterStartDate(null)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
                      title="Clear start date"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  )}
                </div>
              </div>

              <div>
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
                  To Date
                </label>
                <div className="relative">
                  <input
                    type="date"
                    value={filterEndDate || ''}
                    onChange={e => {
                      setActiveDatePreset('CUSTOM');
                      setFilterEndDate(e.target.value || null);
                    }}
                    className="w-full px-3 py-1.5 rounded-xl bg-slate-950 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-amber-500"
                  />
                  {filterEndDate && (
                    <button
                      onClick={() => setFilterEndDate(null)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
                      title="Clear end date"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  )}
                </div>
              </div>
            </div>

            {hasDateFilter && (
              <div className="flex justify-end">
                <button
                  onClick={() => {
                    setDateRange(null, null);
                    setActiveDatePreset('ALL');
                  }}
                  className="text-[11px] font-bold text-amber-400 hover:text-amber-300 underline cursor-pointer"
                >
                  Clear Date Range
                </button>
              </div>
            )}
          </div>
        )}

        {/* Active Filter Tags Pill List */}
        {hasActiveFilters && (
          <div className="flex items-center gap-1.5 flex-wrap pt-1 text-[11px]">
            <span className="text-slate-500 font-bold text-[10px] uppercase">Active:</span>

            {searchQuery && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-indigo-500/20 text-indigo-300 font-medium">
                "{searchQuery}"
                <button onClick={() => setSearchQuery('')} className="hover:text-white">
                  <X className="w-3 h-3" />
                </button>
              </span>
            )}

            {filterCategory && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-purple-500/20 text-purple-300 font-medium">
                {filterCategory}
                <button onClick={() => setFilterCategory(null)} className="hover:text-white">
                  <X className="w-3 h-3" />
                </button>
              </span>
            )}

            {filterType !== 'ALL' && (
              <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-medium ${
                filterType === 'EXPENSE' ? 'bg-rose-500/20 text-rose-300' : 'bg-emerald-500/20 text-emerald-300'
              }`}>
                {filterType === 'EXPENSE' ? 'Expenses' : 'Income'}
                <button onClick={() => setFilterType('ALL')} className="hover:text-white">
                  <X className="w-3 h-3" />
                </button>
              </span>
            )}

            {hasDateFilter && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-500/20 text-amber-300 font-medium">
                {filterStartDate || 'Start'} to {filterEndDate || 'End'}
                <button onClick={() => { setDateRange(null, null); setActiveDatePreset('ALL'); }} className="hover:text-white">
                  <X className="w-3 h-3" />
                </button>
              </span>
            )}

            {filterMember && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-cyan-500/20 text-cyan-300 font-medium">
                {familyMembers.find(m => m.userId === filterMember)?.name || 'Member'}
                <button onClick={() => setFilterMember(null)} className="hover:text-white">
                  <X className="w-3 h-3" />
                </button>
              </span>
            )}
          </div>
        )}
      </div>

      {/* 3. Summary Count & Financial Stats Bar */}
      <div className="flex items-center justify-between px-1.5 text-xs text-slate-400">
        <span>
          Showing <strong className="text-white">{displayedTransactions.length}</strong> transaction{displayedTransactions.length === 1 ? '' : 's'}
        </span>
        <div className="flex items-center gap-3">
          {displayedTransactions.length > 0 && (
            <>
              {filteredIncome > 0 && (
                <span className="text-emerald-400 font-bold">
                  +{currencySymbol}{filteredIncome.toLocaleString()}
                </span>
              )}
              {filteredExpense > 0 && (
                <span className="text-rose-400 font-bold">
                  -{currencySymbol}{filteredExpense.toLocaleString()}
                </span>
              )}
            </>
          )}
          <span className="font-extrabold text-white">
            Net: {filteredTotal >= 0 ? '+' : ''}{currencySymbol}{filteredTotal.toLocaleString()}
          </span>
        </div>
      </div>

      {/* 4. Transactions List */}
      {displayedTransactions.length === 0 ? (
        <div className="p-8 rounded-3xl bg-slate-900/60 border border-white/5 text-center space-y-2.5">
          <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center mx-auto mb-2">
            <Filter className="w-6 h-6" />
          </div>
          <p className="text-sm font-bold text-slate-200">No matching transactions</p>
          <p className="text-xs text-slate-400 max-w-xs mx-auto leading-relaxed">
            {hasActiveFilters
              ? 'No records match your active search keyword, category, or date range filters.'
              : 'Start logging your expenses and income to see them listed here.'}
          </p>
          {hasActiveFilters ? (
            <button
              onClick={clearAllFilters}
              className="mt-2 px-4 py-2 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-bold transition-all cursor-pointer inline-flex items-center gap-1.5"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>Clear All Filters</span>
            </button>
          ) : (
            <button
              onClick={() => openAddModal({ financeScope: currentScope })}
              className="mt-2 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all cursor-pointer"
            >
              + Add Transaction
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-2">
          {displayedTransactions.map(tx => (
            <TransactionItemCard key={tx.id} transaction={tx} />
          ))}
        </div>
      )}

      {/* Floating Action Buttons */}
      <div className="fixed right-6 bottom-22 z-30 flex flex-col gap-2.5">
        <button
          onClick={openVoiceModal}
          className="w-11 h-11 rounded-2xl bg-slate-800 border border-indigo-500/30 text-indigo-300 shadow-md flex items-center justify-center transition-all hover:scale-105 active:scale-95 cursor-pointer"
          title="Voice AI Entry"
        >
          <Mic className="w-5 h-5 text-indigo-400" />
        </button>

        <button
          onClick={() => openAddModal({ financeScope: currentScope })}
          className="w-13 h-13 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white shadow-[0_10px_25px_-5px_rgba(99,102,241,0.5)] flex items-center justify-center transition-all hover:scale-105 active:scale-95 cursor-pointer"
          title="Add Transaction"
        >
          <Plus className="w-6 h-6 stroke-[2.5]" />
        </button>
      </div>
    </div>
  );
};

