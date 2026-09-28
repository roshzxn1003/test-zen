import React, { useState } from 'react';
import {
  FileText,
  Upload,
  Camera,
  X,
  Check,
  RotateCw,
  ShoppingBag,
  Plus,
  Trash2
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { AiService } from '../../services/aiService';
import { ReceiptData, ReceiptItem } from '../../types';

const SAMPLE_RECEIPT = `SUPERMARKET MART
123 Market St, Chennai
Date: 26/09/2026 Time: 18:30
----------------------------
Organic Milk 1L       x2  80.00
Whole Wheat Bread     x1  45.00
Apples 1kg            x1 140.00
Basmati Rice 2kg      x1 190.00
Dark Chocolate        x1  95.00
----------------------------
Subtotal:                550.00
GST Tax (5%):             27.50
Total Paid:              577.50
Payment Method: UPI
Thanks for visiting!`;

export const ReceiptScanModal: React.FC = () => {
  const {
    isReceiptModalOpen,
    closeReceiptModal,
    addTransaction,
    currencySymbol,
    currentScope,
    categories
  } = useFinance();

  const [receiptText, setReceiptText] = useState('');
  const [imageBase64, setImageBase64] = useState<string | null>(null);
  const [isScanning, setIsScanning] = useState(false);
  const [parsedReceipt, setParsedReceipt] = useState<ReceiptData | null>(null);

  // Editable parsed values
  const [merchant, setMerchant] = useState('');
  const [total, setTotal] = useState('');
  const [category, setCategory] = useState('Shopping');
  const [paymentMethod, setPaymentMethod] = useState<'UPI' | 'Cash' | 'Credit Card' | 'Debit Card' | 'Bank Transfer'>('UPI');
  const [items, setItems] = useState<ReceiptItem[]>([]);

  if (!isReceiptModalOpen) return null;

  const handleProcessScan = async (text: string, imgBase64?: string) => {
    setIsScanning(true);
    try {
      const result = await AiService.parseReceipt({
        text,
        imageBase64: imgBase64 || undefined
      });
      setParsedReceipt(result);
      setMerchant(result.merchantName);
      setTotal(result.total.toString());
      setCategory(result.category || 'Shopping');
      setPaymentMethod((result.paymentMethod as any) || 'UPI');
      setItems(result.items || []);
    } catch (e) {
      alert('Could not scan receipt. Please enter details manually.');
    } finally {
      setIsScanning(false);
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const b64 = event.target?.result as string;
      setImageBase64(b64);
      handleProcessScan('Receipt image upload', b64);
    };
    reader.readAsDataURL(file);
  };

  const handleSaveTransaction = () => {
    const numTotal = parseFloat(total);
    if (!merchant.trim() || isNaN(numTotal) || numTotal <= 0) return;

    addTransaction({
      title: `${merchant.trim()} Receipt`,
      amount: numTotal,
      type: 'EXPENSE',
      category: category || 'Shopping',
      paymentMethod,
      financeScope: currentScope,
      note: `Receipt scanned: ${items.map(it => it.name).join(', ') || merchant}`,
      dateMillis: Date.now(),
      receipt: {
        merchantName: merchant.trim(),
        total: numTotal,
        subtotal: parsedReceipt?.subtotal || numTotal,
        discount: parsedReceipt?.discount || 0,
        tax: parsedReceipt?.tax || 0,
        currency: currencySymbol,
        paymentMethod,
        items
      }
    });

    closeReceiptModal();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-md rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl max-h-[90vh] overflow-y-auto space-y-4 no-scrollbar">
        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-white/10">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center">
              <FileText className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-extrabold text-white">Receipt Scanner & OCR</h2>
              <p className="text-[10px] text-slate-400">Extracts items, taxes, and merchant totals</p>
            </div>
          </div>

          <button onClick={closeReceiptModal} className="text-slate-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Upload or Paste Area */}
        {!parsedReceipt ? (
          <div className="space-y-3">
            {/* Image upload box */}
            <label className="border-2 border-dashed border-white/20 hover:border-amber-400/50 rounded-2xl p-6 flex flex-col items-center justify-center cursor-pointer transition-colors bg-slate-950/40">
              <Upload className="w-8 h-8 text-amber-400 mb-2" />
              <span className="text-xs font-bold text-white">Upload Receipt Image</span>
              <span className="text-[10px] text-slate-400 mt-1">PNG, JPG, WEBP up to 5MB</span>
              <input type="file" accept="image/*" onChange={handleFileUpload} className="hidden" />
            </label>

            {/* Paste or sample text */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Or Paste Receipt Text</span>
                <button
                  onClick={() => {
                    setReceiptText(SAMPLE_RECEIPT);
                    handleProcessScan(SAMPLE_RECEIPT);
                  }}
                  className="text-[11px] font-bold text-amber-400 hover:underline cursor-pointer"
                >
                  Load Sample Receipt
                </button>
              </div>

              <textarea
                rows={4}
                value={receiptText}
                onChange={e => setReceiptText(e.target.value)}
                placeholder="Paste receipt lines with item prices and totals..."
                className="w-full p-3 rounded-2xl bg-slate-800 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-amber-500"
              />

              <button
                onClick={() => handleProcessScan(receiptText)}
                disabled={!receiptText.trim() || isScanning}
                className="w-full py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-extrabold flex items-center justify-center gap-1.5 transition-all cursor-pointer disabled:opacity-50"
              >
                {isScanning ? (
                  <>
                    <RotateCw className="w-4 h-4 animate-spin" />
                    <span>Analyzing Receipt...</span>
                  </>
                ) : (
                  <span>Extract Receipt Details</span>
                )}
              </button>
            </div>
          </div>
        ) : (
          /* Parsed Breakdown Review */
          <div className="space-y-4">
            <div className="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-amber-300">Scanned Merchant & Amount</span>
                <button
                  onClick={() => setParsedReceipt(null)}
                  className="text-[11px] font-bold text-slate-400 hover:text-white"
                >
                  Rescan
                </button>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div>
                  <label className="text-[10px] text-slate-400 font-bold block mb-1">Merchant</label>
                  <input
                    type="text"
                    value={merchant}
                    onChange={e => setMerchant(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/10 text-white font-bold"
                  />
                </div>

                <div>
                  <label className="text-[10px] text-slate-400 font-bold block mb-1">Total ({currencySymbol})</label>
                  <input
                    type="number"
                    step="any"
                    value={total}
                    onChange={e => setTotal(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/10 text-white font-black"
                  />
                </div>

                <div>
                  <label className="text-[10px] text-slate-400 font-bold block mb-1">Category</label>
                  <select
                    value={category}
                    onChange={e => setCategory(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/10 text-white"
                  >
                    {categories.map(c => (
                      <option key={c.id} value={c.name}>{c.name}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="text-[10px] text-slate-400 font-bold block mb-1">Payment</label>
                  <select
                    value={paymentMethod}
                    onChange={e => setPaymentMethod(e.target.value as any)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/10 text-white"
                  >
                    <option value="UPI">UPI</option>
                    <option value="Cash">Cash</option>
                    <option value="Credit Card">Credit Card</option>
                    <option value="Debit Card">Debit Card</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Itemized Table */}
            {items.length > 0 && (
              <div className="space-y-2">
                <div className="flex items-center justify-between text-xs font-bold text-slate-300">
                  <span>Itemized Items ({items.length})</span>
                </div>

                <div className="max-h-36 overflow-y-auto space-y-1.5 rounded-2xl bg-slate-950/60 p-2.5 border border-white/5 no-scrollbar">
                  {items.map((it, i) => (
                    <div key={i} className="flex items-center justify-between text-xs py-1 border-b border-white/5 last:border-none">
                      <span className="text-slate-300 truncate max-w-[60%]">{it.name}</span>
                      <span className="text-slate-400 text-[11px]">x{it.quantity}</span>
                      <span className="font-bold text-white">{currencySymbol}{it.totalPrice.toFixed(2)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={closeReceiptModal}
                className="flex-1 py-3 rounded-2xl bg-white/10 text-slate-300 text-xs font-bold hover:bg-white/15 cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSaveTransaction}
                className="flex-1 py-3 rounded-2xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-md cursor-pointer"
              >
                Save Receipt
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
