import React, { useState, useEffect } from 'react';
import {
  Mic,
  MicOff,
  Send,
  X,
  Sparkles,
  Check,
  RotateCw,
  HelpCircle,
  Clock
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { AiService } from '../../services/aiService';
import { ParsedVoiceExpense, VoiceChatMessage } from '../../types';

export const VoiceAiModal: React.FC = () => {
  const {
    isVoiceModalOpen,
    closeVoiceModal,
    addTransaction,
    currencySymbol,
    categories,
    currentScope,
    userProfile
  } = useFinance();

  const [prompt, setPrompt] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [messages, setMessages] = useState<VoiceChatMessage[]>([
    {
      id: 'welcome',
      sender: 'assistant',
      text: "Hello! I am your Zenith Voice Assistant. Speak or type your expense in English or Tanglish (e.g. 'Spent 350 for lunch via UPI' or 'Innaiku movie ki 250 selavu').",
      timestamp: Date.now()
    }
  ]);
  const [currentParsed, setCurrentParsed] = useState<ParsedVoiceExpense | null>(null);

  // Edit fields for parsed expense before saving
  const [editTitle, setEditTitle] = useState('');
  const [editAmount, setEditAmount] = useState('');
  const [editCategory, setEditCategory] = useState('');
  const [editType, setEditType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [editMethod, setEditMethod] = useState<'UPI' | 'Cash' | 'Credit Card' | 'Debit Card' | 'Bank Transfer'>('UPI');
  const [editScope, setEditScope] = useState<'PERSONAL' | 'FAMILY'>('PERSONAL');

  useEffect(() => {
    if (currentParsed) {
      setEditTitle(currentParsed.title);
      setEditAmount(currentParsed.amount.toString());
      setEditCategory(currentParsed.category);
      setEditType(currentParsed.type);
      setEditMethod(currentParsed.paymentMethod);
      setEditScope(currentParsed.scope);
    }
  }, [currentParsed]);

  if (!isVoiceModalOpen) return null;

  // Process text prompt
  const handleProcessPrompt = async (text: string) => {
    if (!text.trim()) return;

    const userMsg: VoiceChatMessage = {
      id: `usr_${Date.now()}`,
      sender: 'user',
      text: text.trim(),
      timestamp: Date.now()
    };
    setMessages(prev => [...prev, userMsg]);
    setPrompt('');
    setIsProcessing(true);

    try {
      const parsed = await AiService.parseVoiceCommand(text.trim());
      setCurrentParsed(parsed);

      const assistantMsg: VoiceChatMessage = {
        id: `asst_${Date.now()}`,
        sender: 'assistant',
        text: `Understood! I parsed: "${parsed.title}" for ${currencySymbol}${parsed.amount} under ${parsed.category} via ${parsed.paymentMethod} (${parsed.scope.toLowerCase()} scope). Review and confirm below:`,
        timestamp: Date.now(),
        parsedExpense: parsed
      };
      setMessages(prev => [...prev, assistantMsg]);
    } catch (e) {
      const errorMsg: VoiceChatMessage = {
        id: `err_${Date.now()}`,
        sender: 'assistant',
        text: "Could not parse clearly. Please verify the amount and details below.",
        timestamp: Date.now()
      };
      setMessages(prev => [...prev, errorMsg]);
    } finally {
      setIsProcessing(false);
    }
  };

  // Speech Recognition integration
  const toggleSpeechRecognition = () => {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      alert('Speech recognition is not supported in this browser. You can type natural commands below!');
      return;
    }

    if (isListening) {
      setIsListening(false);
      return;
    }

    try {
      const recognition = new SpeechRecognition();
      recognition.lang = 'en-IN';
      recognition.continuous = false;
      recognition.interimResults = false;

      recognition.onstart = () => {
        setIsListening(true);
      };

      recognition.onresult = (event: any) => {
        const transcript = event.results?.[0]?.[0]?.transcript;
        if (transcript) {
          setPrompt(transcript);
          handleProcessPrompt(transcript);
        }
      };

      recognition.onerror = () => {
        setIsListening(false);
      };

      recognition.onend = () => {
        setIsListening(false);
      };

      recognition.start();
    } catch (e) {
      setIsListening(false);
    }
  };

  // Confirm and save parsed expense
  const handleConfirmSave = () => {
    const amt = parseFloat(editAmount);
    if (!editTitle.trim() || isNaN(amt) || amt <= 0) return;

    addTransaction({
      title: editTitle.trim(),
      amount: amt,
      type: editType,
      category: editCategory || 'Food & Dining',
      paymentMethod: editMethod,
      financeScope: editScope,
      note: currentParsed?.note || 'Logged via Voice AI',
      dateMillis: Date.now()
    });

    closeVoiceModal();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-md rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl max-h-[90vh] flex flex-col space-y-3">
        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-white/10">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-indigo-500/20 text-indigo-400 flex items-center justify-center">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-extrabold text-white">Zenith AI Voice Assistant</h2>
              <p className="text-[10px] text-slate-400">English & Tanglish natural speech parser</p>
            </div>
          </div>

          <button
            onClick={closeVoiceModal}
            className="p-1 rounded-xl text-slate-400 hover:text-white hover:bg-white/10"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Conversation Stream */}
        <div className="flex-1 overflow-y-auto space-y-2.5 pr-1 max-h-56 no-scrollbar">
          {messages.map(msg => (
            <div
              key={msg.id}
              className={`flex gap-2 ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              {msg.sender === 'assistant' && (
                <div className="w-6 h-6 rounded-full bg-indigo-500/20 text-indigo-300 flex items-center justify-center flex-shrink-0 text-[10px]">
                  AI
                </div>
              )}
              <div
                className={`max-w-[85%] p-3 rounded-2xl text-xs leading-relaxed ${
                  msg.sender === 'user'
                    ? 'bg-indigo-600 text-white rounded-tr-none'
                    : 'bg-slate-800 text-slate-200 border border-white/5 rounded-tl-none'
                }`}
              >
                {msg.text}
              </div>
            </div>
          ))}
          {isProcessing && (
            <div className="flex items-center gap-2 text-xs text-indigo-400 font-semibold p-2">
              <RotateCw className="w-3.5 h-3.5 animate-spin" />
              <span>Analyzing transaction details...</span>
            </div>
          )}
        </div>

        {/* Parsed Result Preview & Quick Edit Card */}
        {currentParsed && (
          <div className="p-3.5 rounded-2xl bg-indigo-950/40 border border-indigo-500/30 space-y-2.5">
            <div className="flex items-center justify-between text-xs font-bold text-indigo-300">
              <span className="flex items-center gap-1.5">
                <Check className="w-3.5 h-3.5 text-emerald-400" />
                Parsed Transaction
              </span>
              <span className="text-[10px] text-slate-400">Tap to edit before saving</span>
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <div>
                <label className="text-[10px] text-slate-400 block mb-0.5 font-bold">Title</label>
                <input
                  type="text"
                  value={editTitle}
                  onChange={e => setEditTitle(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded-xl bg-slate-900 border border-white/10 text-white font-bold"
                />
              </div>

              <div>
                <label className="text-[10px] text-slate-400 block mb-0.5 font-bold">Amount ({currencySymbol})</label>
                <input
                  type="number"
                  step="any"
                  value={editAmount}
                  onChange={e => setEditAmount(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded-xl bg-slate-900 border border-white/10 text-emerald-400 font-black"
                />
              </div>

              <div>
                <label className="text-[10px] text-slate-400 block mb-0.5 font-bold">Category</label>
                <select
                  value={editCategory}
                  onChange={e => setEditCategory(e.target.value)}
                  className="w-full px-2 py-1.5 rounded-xl bg-slate-900 border border-white/10 text-white"
                >
                  {categories.map(c => (
                    <option key={c.id} value={c.name}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-[10px] text-slate-400 block mb-0.5 font-bold">Payment Method</label>
                <select
                  value={editMethod}
                  onChange={e => setEditMethod(e.target.value as any)}
                  className="w-full px-2 py-1.5 rounded-xl bg-slate-900 border border-white/10 text-white"
                >
                  <option value="UPI">UPI</option>
                  <option value="Cash">Cash</option>
                  <option value="Credit Card">Credit Card</option>
                  <option value="Debit Card">Debit Card</option>
                  <option value="Bank Transfer">Bank Transfer</option>
                </select>
              </div>

              <div>
                <label className="text-[10px] text-slate-400 block mb-0.5 font-bold">Scope</label>
                <select
                  value={editScope}
                  onChange={e => setEditScope(e.target.value as any)}
                  className="w-full px-2 py-1.5 rounded-xl bg-slate-900 border border-white/10 text-white"
                >
                  <option value="PERSONAL">Personal</option>
                  <option value="FAMILY">Family Vault</option>
                </select>
              </div>

              <div>
                <label className="text-[10px] text-slate-400 block mb-0.5 font-bold">Type</label>
                <select
                  value={editType}
                  onChange={e => setEditType(e.target.value as any)}
                  className="w-full px-2 py-1.5 rounded-xl bg-slate-900 border border-white/10 text-white"
                >
                  <option value="EXPENSE">Expense</option>
                  <option value="INCOME">Income</option>
                </select>
              </div>
            </div>

            <button
              onClick={handleConfirmSave}
              className="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-md transition-all flex items-center justify-center gap-1.5 cursor-pointer"
            >
              <Check className="w-4 h-4" />
              <span>Confirm & Log Transaction</span>
            </button>
          </div>
        )}

        {/* Input Bar & Mic */}
        <div className="flex items-center gap-2 pt-1">
          <button
            type="button"
            onClick={toggleSpeechRecognition}
            className={`p-3 rounded-2xl flex items-center justify-center transition-all cursor-pointer ${
              isListening
                ? 'bg-rose-500 text-white animate-pulse'
                : 'bg-indigo-600 text-white hover:bg-indigo-500'
            }`}
            title={isListening ? 'Stop listening' : 'Start speaking'}
          >
            {isListening ? <MicOff className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
          </button>

          <input
            type="text"
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter' && prompt.trim()) {
                handleProcessPrompt(prompt);
              }
            }}
            placeholder="Type: 'Lunch 250 UPI' or 'Salary 50k in bank'..."
            className="flex-1 px-3.5 py-2.5 rounded-2xl bg-slate-800 border border-white/10 text-white text-xs focus:outline-none focus:border-indigo-500"
          />

          <button
            onClick={() => handleProcessPrompt(prompt)}
            disabled={!prompt.trim() || isProcessing}
            className="p-2.5 rounded-2xl bg-white/10 hover:bg-white/20 disabled:opacity-40 text-white transition-colors cursor-pointer"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>

        {/* Quick prompt suggestions */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-[10px] text-slate-400 no-scrollbar">
          <span className="flex-shrink-0 font-bold">Try:</span>
          {['Coffee 80 cash', 'Swiggy dinner 450 UPI', 'Innaiku movie ki 250 selavu', 'Veetu vaadagai 14000 family'].map(s => (
            <button
              key={s}
              onClick={() => {
                setPrompt(s);
                handleProcessPrompt(s);
              }}
              className="px-2 py-1 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 whitespace-nowrap cursor-pointer"
            >
              {s}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
