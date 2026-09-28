import React, { useState } from 'react';
import {
  CreditCard,
  QrCode,
  X,
  Check,
  Copy,
  ExternalLink,
  Sparkles
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';

export const UPIPayModal: React.FC = () => {
  const {
    isUpiModalOpen,
    isUpiScanModalOpen,
    closeUpiModal,
    closeUpiScanModal,
    addTransaction,
    currencySymbol,
    categories,
    currentScope,
    familyMembers,
    userProfile
  } = useFinance();

  const isOpen = isUpiModalOpen || isUpiScanModalOpen;
  const isScanMode = isUpiScanModalOpen;

  const [upiId, setUpiId] = useState('');
  const [payeeName, setPayeeName] = useState('');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState(categories[0]?.name || 'Shopping');
  const [note, setNote] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);

  if (!isOpen) return null;

  const handleClose = () => {
    closeUpiModal();
    closeUpiScanModal();
  };

  const upiIntentUri = `upi://pay?pa=${encodeURIComponent(upiId)}&pn=${encodeURIComponent(payeeName)}&am=${amount}&cu=INR&tn=${encodeURIComponent(note)}`;

  const handleCopyLink = () => {
    navigator.clipboard.writeText(upiIntentUri);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  const handleConfirmPayment = () => {
    const parsedAmount = parseFloat(amount);
    if (!payeeName.trim() || isNaN(parsedAmount) || parsedAmount <= 0) return;

    addTransaction({
      title: payeeName.trim(),
      amount: parsedAmount,
      type: 'EXPENSE',
      category,
      paymentMethod: 'UPI',
      financeScope: currentScope,
      upiId,
      upiTransactionId: `UTR${Math.floor(1000000000 + Math.random() * 9000000000)}`,
      note: `${note} (UPI: ${upiId})`,
      dateMillis: Date.now()
    });

    handleClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-sm rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-white/10">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center">
              {isScanMode ? <QrCode className="w-4 h-4" /> : <CreditCard className="w-4 h-4" />}
            </div>
            <div>
              <h2 className="text-sm font-extrabold text-white">
                {isScanMode ? 'UPI QR Scanner' : 'Direct UPI Pay'}
              </h2>
              <p className="text-[10px] text-slate-400">NPCI / BHIM Instant UPI Gateway</p>
            </div>
          </div>

          <button onClick={handleClose} className="text-slate-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scan banner if in scan mode */}
        {isScanMode && (
          <div className="p-3 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 flex items-center gap-2.5">
            <QrCode className="w-5 h-5 text-cyan-400 flex-shrink-0 animate-pulse" />
            <p className="text-xs text-cyan-200">
              {upiId
                ? `QR Code detected: ${upiId} (${payeeName || 'Merchant'}). Details loaded.`
                : 'Point camera at any UPI QR code, or enter UPI VPA / Payee ID below.'}
            </p>
          </div>
        )}

        {/* Form Inputs */}
        <div className="space-y-3">
          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Payee VPA / UPI ID
            </label>
            <input
              type="text"
              value={upiId}
              onChange={e => setUpiId(e.target.value)}
              placeholder="e.g. merchant@okhdfcbank"
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-mono font-bold focus:outline-none focus:border-emerald-500"
            />
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
                Payee Name
              </label>
              <input
                type="text"
                value={payeeName}
                onChange={e => setPayeeName(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div>
              <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
                Amount ({currencySymbol})
              </label>
              <input
                type="number"
                step="any"
                value={amount}
                onChange={e => setAmount(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-emerald-400 text-sm font-extrabold focus:outline-none focus:border-emerald-500"
              />
            </div>
          </div>

          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Category
            </label>
            <select
              value={category}
              onChange={e => setCategory(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-emerald-500"
            >
              {categories.map(c => (
                <option key={c.id} value={c.name}>{c.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Payment Remarks
            </label>
            <input
              type="text"
              value={note}
              onChange={e => setNote(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl bg-slate-800 border border-white/10 text-white text-xs focus:outline-none focus:border-emerald-500"
            />
          </div>

          {/* Copy Intent Link */}
          <div className="flex items-center justify-between p-2.5 rounded-xl bg-slate-950/60 border border-white/5 text-[11px]">
            <span className="text-slate-400 truncate max-w-[70%] font-mono">{upiIntentUri}</span>
            <button
              onClick={handleCopyLink}
              className="flex items-center gap-1 text-emerald-400 hover:text-emerald-300 font-bold cursor-pointer"
            >
              {copiedLink ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copiedLink ? 'Copied' : 'Copy'}</span>
            </button>
          </div>

          {/* Action Buttons */}
          <div className="flex gap-2 pt-2">
            <button
              type="button"
              onClick={handleClose}
              className="flex-1 py-3 rounded-2xl bg-white/10 hover:bg-white/15 text-slate-300 text-xs font-bold transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleConfirmPayment}
              className="flex-1 py-3 rounded-2xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-lg shadow-emerald-600/30 transition-all flex items-center justify-center gap-1.5 cursor-pointer"
            >
              <Check className="w-4 h-4" />
              <span>Record Payment</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
