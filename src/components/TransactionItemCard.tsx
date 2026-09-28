import React from 'react';
import {
  Utensils,
  ShoppingBag,
  Home,
  Car,
  Receipt,
  Film,
  HeartPulse,
  Briefcase,
  Laptop,
  TrendingUp,
  Tag,
  FileText,
  UserCheck
} from 'lucide-react';
import { Transaction } from '../types';
import { useFinance } from '../context/FinanceContext';

const CATEGORY_ICONS: Record<string, React.ElementType> = {
  'Food & Dining': Utensils,
  'Shopping': ShoppingBag,
  'Housing & Rent': Home,
  'Transportation': Car,
  'Bills & Utilities': Receipt,
  'Entertainment': Film,
  'Healthcare': HeartPulse,
  'Salary & Income': Briefcase,
  'Freelance / Business': Laptop,
  'Investments': TrendingUp
};

export const TransactionItemCard: React.FC<{ transaction: Transaction }> = ({ transaction }) => {
  const { currencySymbol, openDetailModal, currentScope } = useFinance();
  const IconComponent = CATEGORY_ICONS[transaction.category] || Tag;
  const isIncome = transaction.type === 'INCOME';

  const formattedDate = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric'
  }).format(new Date(transaction.dateMillis));

  return (
    <div
      onClick={() => openDetailModal(transaction)}
      className="group relative flex items-center justify-between p-3.5 rounded-2xl bg-slate-900/60 hover:bg-slate-800/80 border border-white/5 hover:border-white/10 transition-all cursor-pointer shadow-sm hover:shadow-md"
    >
      <div className="flex items-center gap-3 min-w-0 flex-1">
        {/* Category Icon Badge */}
        <div
          className={`w-11 h-11 rounded-2xl flex items-center justify-center flex-shrink-0 border ${
            isIncome
              ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
              : 'bg-indigo-500/10 border-indigo-500/20 text-indigo-400'
          }`}
        >
          <IconComponent className="w-5 h-5" />
        </div>

        {/* Transaction Info */}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5 flex-wrap">
            <h4 className="text-sm font-bold text-white truncate group-hover:text-indigo-200 transition-colors">
              {transaction.title}
            </h4>
            {transaction.receipt && (
              <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded-md bg-amber-500/15 border border-amber-500/30 text-[9px] font-bold text-amber-300">
                <FileText className="w-2.5 h-2.5" />
                Receipt
              </span>
            )}
          </div>

          <div className="flex items-center gap-2 mt-1 text-[11px] text-slate-400">
            <span>{formattedDate}</span>
            <span>•</span>
            <span className="text-slate-300 font-medium">{transaction.category}</span>
            {transaction.paymentMethod && (
              <>
                <span>•</span>
                <span className="text-slate-400">{transaction.paymentMethod}</span>
              </>
            )}
            {currentScope === 'FAMILY' && transaction.createdByName && (
              <>
                <span>•</span>
                <span className="inline-flex items-center gap-1 text-cyan-300 font-medium">
                  <UserCheck className="w-3 h-3" />
                  {transaction.createdByName}
                </span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Amount */}
      <div className="text-right pl-3 flex-shrink-0">
        <p
          className={`text-sm sm:text-base font-extrabold tracking-tight ${
            isIncome ? 'text-emerald-400' : 'text-slate-100'
          }`}
        >
          {isIncome ? '+' : '-'}{currencySymbol}{transaction.amount.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}
        </p>
      </div>
    </div>
  );
};
