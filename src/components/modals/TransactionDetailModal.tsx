import React from 'react';
import {
  X,
  Edit2,
  Trash2,
  FileText,
  Calendar,
  CreditCard,
  User,
  Users,
  Tag,
  Hash,
  ArrowDownLeft,
  ArrowUpRight
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';

export const TransactionDetailModal: React.FC = () => {
  const {
    selectedDetailTx,
    closeDetailModal,
    openAddModal,
    deleteTransaction,
    currencySymbol
  } = useFinance();

  if (!selectedDetailTx) return null;

  const isIncome = selectedDetailTx.type === 'INCOME';
  const formattedDate = new Date(selectedDetailTx.dateMillis).toLocaleDateString('en-US', {
    weekday: 'short',
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });

  const handleEdit = () => {
    const tx = selectedDetailTx;
    closeDetailModal();
    openAddModal(tx);
  };

  const handleDelete = () => {
    if (confirm('Are you sure you want to delete this transaction?')) {
      deleteTransaction(selectedDetailTx.id);
      closeDetailModal();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-sm rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl max-h-[90vh] overflow-y-auto space-y-4 no-scrollbar">
        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-white/10">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Transaction Details
          </span>
          <button onClick={closeDetailModal} className="text-slate-400 hover:text-white cursor-pointer">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Hero Amount & Title */}
        <div className="text-center py-2 space-y-1">
          <div className="inline-flex items-center justify-center p-3 rounded-2xl bg-white/5 border border-white/10 mb-1">
            {isIncome ? (
              <ArrowDownLeft className="w-6 h-6 text-emerald-400" />
            ) : (
              <ArrowUpRight className="w-6 h-6 text-rose-400" />
            )}
          </div>
          <h2 className="text-2xl font-black text-white tracking-tight">
            {isIncome ? '+' : '-'}{currencySymbol}{selectedDetailTx.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </h2>
          <p className="text-sm font-bold text-slate-300">{selectedDetailTx.title}</p>
        </div>

        {/* Info Grid */}
        <div className="p-3.5 rounded-2xl bg-slate-950/60 border border-white/5 space-y-2.5 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-slate-400 flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" /> Date
            </span>
            <span className="font-bold text-white">{formattedDate}</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-400 flex items-center gap-1.5">
              <Tag className="w-3.5 h-3.5" /> Category
            </span>
            <span className="font-bold text-indigo-300">{selectedDetailTx.category}</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-400 flex items-center gap-1.5">
              <CreditCard className="w-3.5 h-3.5" /> Payment Method
            </span>
            <span className="font-bold text-white">{selectedDetailTx.paymentMethod}</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-400 flex items-center gap-1.5">
              {selectedDetailTx.financeScope === 'FAMILY' ? <Users className="w-3.5 h-3.5" /> : <User className="w-3.5 h-3.5" />} Scope
            </span>
            <span className={`font-extrabold ${selectedDetailTx.financeScope === 'FAMILY' ? 'text-cyan-300' : 'text-slate-200'}`}>
              {selectedDetailTx.financeScope === 'FAMILY' ? 'Family Vault' : 'Personal'}
            </span>
          </div>

          {selectedDetailTx.createdByName && (
            <div className="flex items-center justify-between">
              <span className="text-slate-400">Paid By</span>
              <span className="font-bold text-cyan-300">{selectedDetailTx.createdByName}</span>
            </div>
          )}

          {selectedDetailTx.upiId && (
            <div className="flex items-center justify-between">
              <span className="text-slate-400">UPI Payee</span>
              <span className="font-mono text-slate-300">{selectedDetailTx.upiId}</span>
            </div>
          )}

          {selectedDetailTx.upiTransactionId && (
            <div className="flex items-center justify-between">
              <span className="text-slate-400">UPI Ref / UTR</span>
              <span className="font-mono text-slate-400">{selectedDetailTx.upiTransactionId}</span>
            </div>
          )}

          {selectedDetailTx.note && (
            <div className="pt-2 border-t border-white/5">
              <span className="text-slate-400 block mb-0.5">Notes:</span>
              <p className="text-slate-300 text-[11px] leading-relaxed">{selectedDetailTx.note}</p>
            </div>
          )}
        </div>

        {/* Attached Receipt Breakdown */}
        {selectedDetailTx.receipt && (
          <div className="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20 space-y-2">
            <div className="flex items-center gap-1.5 text-xs font-bold text-amber-300">
              <FileText className="w-3.5 h-3.5" />
              <span>Attached Receipt: {selectedDetailTx.receipt.merchantName}</span>
            </div>

            {selectedDetailTx.receipt.items && selectedDetailTx.receipt.items.length > 0 && (
              <div className="space-y-1 pt-1 max-h-32 overflow-y-auto no-scrollbar text-xs">
                {selectedDetailTx.receipt.items.map((it, i) => (
                  <div key={i} className="flex justify-between text-slate-300 py-0.5 border-b border-white/5 last:border-none">
                    <span className="truncate max-w-[65%]">{it.name} x{it.quantity}</span>
                    <span className="font-bold">{currencySymbol}{it.totalPrice.toFixed(2)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Actions (Edit / Delete) */}
        <div className="flex gap-2 pt-2">
          <button
            onClick={handleEdit}
            className="flex-1 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-white text-xs font-bold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          >
            <Edit2 className="w-3.5 h-3.5 text-indigo-400" />
            <span>Edit</span>
          </button>

          <button
            onClick={handleDelete}
            className="flex-1 py-2.5 rounded-xl bg-rose-500/15 hover:bg-rose-500/25 border border-rose-500/30 text-rose-300 text-xs font-bold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span>Delete</span>
          </button>
        </div>
      </div>
    </div>
  );
};
