import { ParsedVoiceExpense, ReceiptData, ReceiptItem, FinanceScope, TransactionType, PaymentMethod } from '../types';

interface ItemMeta {
  standardName: string;
  category: string;
  defaultAmount: number;
}

const ENGLISH_ITEM_DICTIONARY: Record<string, ItemMeta> = {
  // Income & Investments
  salary: { standardName: 'Monthly Salary', category: 'Salary & Income', defaultAmount: 35000 },
  sambalam: { standardName: 'Monthly Salary', category: 'Salary & Income', defaultAmount: 35000 },
  freelance: { standardName: 'Freelance Payment', category: 'Salary & Income', defaultAmount: 10000 },
  bonus: { standardName: 'Bonus', category: 'Salary & Income', defaultAmount: 5000 },
  stipend: { standardName: 'Stipend', category: 'Salary & Income', defaultAmount: 8000 },
  allowance: { standardName: 'Allowance', category: 'Salary & Income', defaultAmount: 3000 },
  cashback: { standardName: 'Cashback', category: 'Investments', defaultAmount: 200 },
  dividend: { standardName: 'Dividend', category: 'Investments', defaultAmount: 1000 },
  interest: { standardName: 'Interest Income', category: 'Investments', defaultAmount: 500 },
  'mutual fund': { standardName: 'Mutual Fund SIP', category: 'Investments', defaultAmount: 2500 },
  stocks: { standardName: 'Stock Investment', category: 'Investments', defaultAmount: 5000 },

  // Food & Dining / Delivery
  swiggy: { standardName: 'Swiggy Order', category: 'Food & Dining', defaultAmount: 350 },
  zomato: { standardName: 'Zomato Order', category: 'Food & Dining', defaultAmount: 380 },
  lunch: { standardName: 'Lunch', category: 'Food & Dining', defaultAmount: 200 },
  dinner: { standardName: 'Dinner', category: 'Food & Dining', defaultAmount: 350 },
  breakfast: { standardName: 'Breakfast', category: 'Food & Dining', defaultAmount: 100 },
  saapadu: { standardName: 'Food & Dining', category: 'Food & Dining', defaultAmount: 150 },
  kaalaila: { standardName: 'Morning Breakfast', category: 'Food & Dining', defaultAmount: 80 },
  mathiyam: { standardName: 'Afternoon Lunch', category: 'Food & Dining', defaultAmount: 150 },
  iravu: { standardName: 'Night Dinner', category: 'Food & Dining', defaultAmount: 180 },
  hotel: { standardName: 'Restaurant Dining', category: 'Food & Dining', defaultAmount: 450 },
  coffee: { standardName: 'Coffee', category: 'Food & Dining', defaultAmount: 80 },
  tea: { standardName: 'Tea & Snacks', category: 'Food & Dining', defaultAmount: 25 },
  chai: { standardName: 'Tea & Snacks', category: 'Food & Dining', defaultAmount: 25 },
  pizza: { standardName: 'Pizza', category: 'Food & Dining', defaultAmount: 400 },
  burger: { standardName: 'Burger', category: 'Food & Dining', defaultAmount: 180 },
  biryani: { standardName: 'Biryani', category: 'Food & Dining', defaultAmount: 250 },
  snacks: { standardName: 'Snacks', category: 'Food & Dining', defaultAmount: 120 },
  bakery: { standardName: 'Bakery Items', category: 'Food & Dining', defaultAmount: 150 },

  // Shopping & Quick Commerce
  groceries: { standardName: 'Groceries', category: 'Shopping', defaultAmount: 600 },
  grocery: { standardName: 'Groceries', category: 'Shopping', defaultAmount: 600 },
  blinkit: { standardName: 'Blinkit Groceries', category: 'Shopping', defaultAmount: 450 },
  zepto: { standardName: 'Zepto Groceries', category: 'Shopping', defaultAmount: 350 },
  instamart: { standardName: 'Instamart Delivery', category: 'Shopping', defaultAmount: 400 },
  bigbasket: { standardName: 'BigBasket Groceries', category: 'Shopping', defaultAmount: 1200 },
  supermarket: { standardName: 'Supermarket', category: 'Shopping', defaultAmount: 800 },
  dmart: { standardName: 'D-Mart Shopping', category: 'Shopping', defaultAmount: 1500 },
  'd-mart': { standardName: 'D-Mart Shopping', category: 'Shopping', defaultAmount: 1500 },
  provisions: { standardName: 'Provisions & Groceries', category: 'Shopping', defaultAmount: 1500 },
  provision: { standardName: 'Provisions & Groceries', category: 'Shopping', defaultAmount: 1000 },
  kadai: { standardName: 'Store Purchase', category: 'Shopping', defaultAmount: 300 },
  maligai: { standardName: 'Provisions / Maligai', category: 'Shopping', defaultAmount: 800 },
  vegetables: { standardName: 'Vegetables', category: 'Shopping', defaultAmount: 150 },
  fruits: { standardName: 'Fruits', category: 'Shopping', defaultAmount: 200 },
  milk: { standardName: 'Milk', category: 'Food & Dining', defaultAmount: 35 },
  bread: { standardName: 'Bread & Dairy', category: 'Food & Dining', defaultAmount: 45 },
  eggs: { standardName: 'Eggs', category: 'Food & Dining', defaultAmount: 70 },
  chicken: { standardName: 'Chicken', category: 'Food & Dining', defaultAmount: 220 },
  amazon: { standardName: 'Amazon Shopping', category: 'Shopping', defaultAmount: 1200 },
  flipkart: { standardName: 'Flipkart Shopping', category: 'Shopping', defaultAmount: 1100 },
  myntra: { standardName: 'Myntra Fashion', category: 'Shopping', defaultAmount: 1500 },
  clothes: { standardName: 'Clothing', category: 'Shopping', defaultAmount: 1800 },

  // Transportation & Rides
  rapido: { standardName: 'Rapido Ride', category: 'Transportation', defaultAmount: 75 },
  uber: { standardName: 'Uber Ride', category: 'Transportation', defaultAmount: 250 },
  ola: { standardName: 'Ola Ride', category: 'Transportation', defaultAmount: 200 },
  cab: { standardName: 'Cab Ride', category: 'Transportation', defaultAmount: 250 },
  auto: { standardName: 'Auto Fare', category: 'Transportation', defaultAmount: 100 },
  bus: { standardName: 'Bus Ticket', category: 'Transportation', defaultAmount: 50 },
  train: { standardName: 'Train Ticket', category: 'Transportation', defaultAmount: 300 },
  flight: { standardName: 'Flight Ticket', category: 'Transportation', defaultAmount: 4500 },
  metro: { standardName: 'Metro Fare', category: 'Transportation', defaultAmount: 40 },
  petrol: { standardName: 'Petrol / Fuel', category: 'Transportation', defaultAmount: 500 },
  fuel: { standardName: 'Fuel', category: 'Transportation', defaultAmount: 500 },
  diesel: { standardName: 'Diesel', category: 'Transportation', defaultAmount: 1000 },
  vandi: { standardName: 'Vehicle Maintenance / Fuel', category: 'Transportation', defaultAmount: 400 },
  toll: { standardName: 'Highway Toll', category: 'Transportation', defaultAmount: 120 },

  // Entertainment
  movie: { standardName: 'Movie Tickets', category: 'Entertainment', defaultAmount: 350 },
  cinema: { standardName: 'Cinema', category: 'Entertainment', defaultAmount: 350 },
  bookmyshow: { standardName: 'BookMyShow Movie Tickets', category: 'Entertainment', defaultAmount: 450 },
  netflix: { standardName: 'Netflix Subscription', category: 'Entertainment', defaultAmount: 649 },
  spotify: { standardName: 'Spotify Subscription', category: 'Entertainment', defaultAmount: 119 },
  prime: { standardName: 'Amazon Prime', category: 'Entertainment', defaultAmount: 299 },
  hotstar: { standardName: 'Disney Hotstar', category: 'Entertainment', defaultAmount: 299 },

  // Utilities & Bills
  electricity: { standardName: 'Electricity Bill', category: 'Bills & Utilities', defaultAmount: 1200 },
  'power bill': { standardName: 'Electricity Bill', category: 'Bills & Utilities', defaultAmount: 1200 },
  'current bill': { standardName: 'Electricity Bill', category: 'Bills & Utilities', defaultAmount: 1200 },
  water: { standardName: 'Water Bill', category: 'Bills & Utilities', defaultAmount: 300 },
  gas: { standardName: 'Gas Cylinder', category: 'Bills & Utilities', defaultAmount: 950 },
  cylinder: { standardName: 'LPG Gas Cylinder', category: 'Bills & Utilities', defaultAmount: 950 },
  wifi: { standardName: 'Wi-Fi / Internet Bill', category: 'Bills & Utilities', defaultAmount: 800 },
  internet: { standardName: 'Internet Bill', category: 'Bills & Utilities', defaultAmount: 800 },
  recharge: { standardName: 'Mobile Recharge', category: 'Bills & Utilities', defaultAmount: 299 },
  mobile: { standardName: 'Mobile Bill', category: 'Bills & Utilities', defaultAmount: 399 },

  // Housing & Rent
  rent: { standardName: 'House Rent', category: 'Housing & Rent', defaultAmount: 12000 },
  veedu: { standardName: 'House Rent', category: 'Housing & Rent', defaultAmount: 12000 },
  vaadagai: { standardName: 'House Rent', category: 'Housing & Rent', defaultAmount: 12000 },
  maintenance: { standardName: 'Maintenance Fee', category: 'Housing & Rent', defaultAmount: 2000 },

  // Healthcare
  medicine: { standardName: 'Medicines', category: 'Healthcare', defaultAmount: 350 },
  marundhu: { standardName: 'Medicines / Pharmacy', category: 'Healthcare', defaultAmount: 350 },
  pharmacy: { standardName: 'Pharmacy', category: 'Healthcare', defaultAmount: 400 },
  doctor: { standardName: 'Doctor Consultation', category: 'Healthcare', defaultAmount: 500 },
  gym: { standardName: 'Gym Membership', category: 'Healthcare', defaultAmount: 1500 }
};

export class AiService {
  /**
   * Fast rule-based parser ported directly from GeminiAiService.kt
   * Handles English and Tanglish inputs safely in browser offline mode.
   */
  static fallbackParseVoiceCommand(prompt: string): ParsedVoiceExpense {
    const lower = prompt.toLowerCase().trim();

    // 1. Scope Detection (Family Vault vs Personal)
    const isFamilyScope = (
      lower.includes('family') || lower.includes('shared') ||
      lower.includes('vault') || lower.includes('household') ||
      lower.includes('split') || lower.includes('joint') ||
      lower.includes('our ') || lower.endsWith('our') ||
      lower.includes('roommate') || lower.includes('veetu') ||
      lower.includes('amma') || lower.includes('appa') ||
      lower.includes('veedu') || lower.includes('vaadagai')
    );
    const scope: FinanceScope = isFamilyScope ? 'FAMILY' : 'PERSONAL';

    // 2. Explicit Income vs Expense Detection
    const isCreditCard = lower.includes('credit card') || lower.includes('credit-card');
    const isIncome = !isCreditCard && (
      lower.includes('salary') || lower.includes('sambalam') ||
      lower.includes('varavu') || lower.includes('received') ||
      lower.includes('earned') || lower.includes('income') ||
      lower.includes('got paid') || lower.includes('credited') ||
      lower.includes('bonus') || lower.includes('cashback') ||
      lower.includes('freelance') || lower.includes('refund') ||
      lower.includes('dividend') || lower.includes('allowance') ||
      lower.includes('stipend') || lower.includes('panam vanthuchu') ||
      lower.includes('kaasu vanthuchu')
    );
    const type: TransactionType = isIncome ? 'INCOME' : 'EXPENSE';

    // 3. Payment Method Extraction
    let paymentMethod: PaymentMethod = 'UPI';
    if (lower.includes('cash')) {
      paymentMethod = 'Cash';
    } else if (lower.includes('credit card') || lower.includes('credit') || lower.includes('cc')) {
      paymentMethod = 'Credit Card';
    } else if (lower.includes('debit card') || lower.includes('debit') || lower.includes('card')) {
      paymentMethod = 'Debit Card';
    } else if (lower.includes('bank') || lower.includes('transfer') || lower.includes('netbanking') || lower.includes('neft') || lower.includes('imps')) {
      paymentMethod = 'Bank Transfer';
    } else {
      paymentMethod = 'UPI';
    }

    // 4. Amount Extraction (Handling number words, 'k', 'thousand', 'lakh', 'crore' multipliers)
    let detectedAmount: number | null = null;

    const numberWords: [string, number][] = [
      ['iruvathaayiram', 20000],
      ['pathaayiram', 10000],
      ['anjaayiram', 5000],
      ['naalaayiram', 4000],
      ['moonaayiram', 3000],
      ['rendaayiram', 2000],
      ['aayiram', 1000],
      ['oru aayiram', 1000],
      ['thollaayiram', 900],
      ['ennooru', 800],
      ['elanooru', 700],
      ['arunooru', 600],
      ['ainooru', 500],
      ['naanooru', 400],
      ['munnooru', 300],
      ['irunooru', 200],
      ['nooru', 100],
      ['oru nooru', 100],
      ['two thousand', 2000],
      ['one thousand', 1000],
      ['five hundred', 500]
    ];

    for (const [word, val] of numberWords) {
      if (lower.includes(word)) {
        detectedAmount = val;
        break;
      }
    }

    if (!detectedAmount || detectedAmount <= 0) {
      const multMatch = lower.match(/(?:(?:[$₹€£]|rs|rupees|inr|paid|spent|cost|of)\s*)?(\d+(?:\.\d+)?)\s*(k|thousand|lakh|lakhs|lac|lacs|crore|crores|cr)\b/i);
      if (multMatch) {
        const base = parseFloat(multMatch[1]);
        const unit = multMatch[2].toLowerCase();
        let mult = 1;
        if (unit === 'k' || unit === 'thousand') mult = 1000;
        else if (unit.startsWith('la')) mult = 100000;
        else if (unit.startsWith('cr')) mult = 10000000;
        if (!isNaN(base) && base > 0) {
          detectedAmount = base * mult;
        }
      }
    }

    if (!detectedAmount || detectedAmount <= 0) {
      const amountMatch = lower.match(/(?:[$₹€£]|rs|rupees|inr|paid|spent|cost|of)\s*(\d+(?:\.\d{1,2})?)|(\d+(?:\.\d{1,2})?)\s*(?:rs|rupees|inr|bucks)?/i);
      const allNumbers = (lower.match(/\b\d+(?:\.\d{1,2})?\b/g) || []).map(Number);

      if (amountMatch) {
        const n1 = amountMatch[1] ? parseFloat(amountMatch[1]) : NaN;
        const n2 = amountMatch[2] ? parseFloat(amountMatch[2]) : NaN;
        detectedAmount = !isNaN(n1) ? n1 : (!isNaN(n2) ? n2 : null);
      }
      if (!detectedAmount && allNumbers.length > 0) {
        detectedAmount = Math.max(...allNumbers);
      }
    }

    // 5. Item & Category Matching
    let matchedMeta: ItemMeta | null = null;
    let bestKey = '';

    for (const [key, meta] of Object.entries(ENGLISH_ITEM_DICTIONARY)) {
      const matched = key.length <= 3 ? new RegExp(`\\b${key}\\b`, 'i').test(lower) : lower.includes(key);
      if (matched && key.length > bestKey.length) {
        bestKey = key;
        matchedMeta = meta;
      }
    }

    const finalAmount = detectedAmount && detectedAmount > 0 ? detectedAmount : (matchedMeta?.defaultAmount || 100);
    const finalTitle = matchedMeta?.standardName || (() => {
      const clean = lower
        .replace(/\b(spent|paid|for|in|via|on|got|received|rs|rupees|upi|cash|card|bank|transfer|selavu|varavu|panam|kaasu|poten|kuduthen|vanginen|kattinen|innaiku|veetu|office|ku|la)\b/gi, '')
        .trim();
      if (clean) {
        return clean.charAt(0).toUpperCase() + clean.slice(1);
      }
      return isIncome ? 'Income' : 'Expense';
    })();

    const finalCategory = isIncome
      ? (matchedMeta?.category || 'Salary & Income')
      : (matchedMeta?.category || 'Food & Dining');

    return {
      title: finalTitle,
      amount: finalAmount,
      type,
      category: finalCategory,
      paymentMethod,
      note: prompt,
      scope
    };
  }

  /**
   * Parses voice command via Gemini API proxy, with automatic fallback
   */
  static async parseVoiceCommand(prompt: string): Promise<ParsedVoiceExpense> {
    try {
      const res = await fetch('/api/ai/voice-parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt })
      });
      if (res.ok) {
        const data = await res.json();
        if (data.parsed && data.parsed.amount > 0) {
          return {
            title: data.parsed.title || 'Voice Expense',
            amount: Number(data.parsed.amount) || 100,
            type: data.parsed.type === 'INCOME' ? 'INCOME' : 'EXPENSE',
            category: data.parsed.category || 'Food & Dining',
            paymentMethod: (data.parsed.paymentMethod as PaymentMethod) || 'UPI',
            note: data.parsed.note || prompt,
            scope: data.parsed.scope === 'FAMILY' ? 'FAMILY' : 'PERSONAL'
          };
        }
      }
    } catch (e) {
      // Fallback below
    }
    return this.fallbackParseVoiceCommand(prompt);
  }

  /**
   * Fast rule-based receipt text parser
   */
  static fallbackParseReceipt(rawText: string): ReceiptData {
    const lines = rawText.split('\n').map(l => l.trim()).filter(Boolean);
    const merchant = lines.find(l => l.length > 2 && !l.startsWith('Date') && !l.startsWith('Time')) || 'Store Purchase';

    const grandTotalRegex = /(?:grand\s*total|total\s*paid|amount\s*paid|net\s*payable|total)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)/i;
    const grandMatch = rawText.match(grandTotalRegex);
    let detectedTotal = grandMatch ? parseFloat(grandMatch[1]) : 0;
    if (!detectedTotal) {
      const numbers = (rawText.match(/\d+\.\d{2}/g) || []).map(Number);
      detectedTotal = numbers.length > 0 ? Math.max(...numbers) : 250;
    }

    const subtotalRegex = /(?:subtotal|sub\s*total|sub-total)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)/i;
    const subMatch = rawText.match(subtotalRegex);
    const subtotal = subMatch ? parseFloat(subMatch[1]) : detectedTotal;

    const taxRegex = /(?:tax|gst|vat)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)/i;
    const taxMatch = rawText.match(taxRegex);
    const tax = taxMatch ? parseFloat(taxMatch[1]) : 0;

    const discountRegex = /(?:discount|disc|saved)[\s:]*[$₹€£]?\s*(\d+(?:\.\d{1,2})?)/i;
    const discMatch = rawText.match(discountRegex);
    const discount = discMatch ? parseFloat(discMatch[1]) : 0;

    const dateMatch = rawText.match(/\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b/);
    const detectedDate = dateMatch ? dateMatch[1] : new Date().toLocaleDateString('en-GB');

    const timeMatch = rawText.match(/\b(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AaPp][Mm])?)\b/);
    const detectedTime = timeMatch ? timeMatch[1] : '';

    const items: ReceiptItem[] = [];
    for (const line of lines) {
      const itemMatch = line.match(/^([a-zA-Z\s]+?)\s+(?:(\d+)\s*[xX]\s*)?[$₹€£]?\s*(\d+(?:\.\d{1,2})?)$/);
      if (itemMatch) {
        const name = itemMatch[1].trim();
        const qty = itemMatch[2] ? parseFloat(itemMatch[2]) : 1;
        const total = parseFloat(itemMatch[3]);
        if (name.length > 2 && !isNaN(total)) {
          items.push({
            name,
            quantity: qty,
            unitPrice: total / qty,
            totalPrice: total
          });
        }
      }
    }

    if (items.length === 0) {
      items.push({
        name: 'Item Purchase',
        quantity: 1,
        unitPrice: detectedTotal,
        totalPrice: detectedTotal
      });
    }

    return {
      merchantName: merchant,
      receiptDate: detectedDate,
      receiptTime: detectedTime,
      total: detectedTotal,
      subtotal,
      discount,
      tax,
      currency: '₹',
      paymentMethod: rawText.toLowerCase().includes('cash') ? 'Cash' : 'UPI',
      category: rawText.toLowerCase().includes('restaurant') || rawText.toLowerCase().includes('cafe') ? 'Food & Dining' : 'Shopping',
      items,
      rawText
    };
  }

  /**
   * Scans a receipt via API or fallback parser
   */
  static async parseReceipt(options: { text?: string; imageBase64?: string }): Promise<ReceiptData> {
    try {
      const res = await fetch('/api/ai/receipt-scan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(options)
      });
      if (res.ok) {
        const data = await res.json();
        if (data.parsed && (data.parsed.totalAmount > 0 || data.parsed.merchantName)) {
          const p = data.parsed;
          return {
            merchantName: p.merchantName || 'Store Purchase',
            receiptNumber: p.receiptNumber || undefined,
            receiptDate: p.dateString || new Date().toLocaleDateString('en-GB'),
            receiptTime: p.timeString || '',
            subtotal: p.subtotal || p.totalAmount || 0,
            discount: p.discount || 0,
            tax: p.tax || 0,
            total: p.totalAmount || 0,
            currency: '₹',
            paymentMethod: p.paymentMethod || 'UPI',
            items: Array.isArray(p.items) && p.items.length > 0
              ? p.items.map((it: any) => ({
                  name: it.name || 'Item',
                  quantity: it.quantity || 1,
                  unitPrice: it.unitPrice || 0,
                  totalPrice: it.totalPrice || (it.quantity * it.unitPrice) || 0
                }))
              : [{ name: 'Scanned Purchase', quantity: 1, unitPrice: p.totalAmount || 0, totalPrice: p.totalAmount || 0 }],
            rawText: options.text || ''
          };
        }
      }
    } catch (e) {
      // Fallback
    }

    return this.fallbackParseReceipt(options.text || 'Store Receipt Total: 350.00');
  }

  /**
   * Generates AI Financial Coach advice
   */
  static async getCoachAdvice(income: number, expense: number, category: string): Promise<string> {
    try {
      const res = await fetch('/api/ai/coach-advice', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ income, expense, category })
      });
      if (res.ok) {
        const data = await res.json();
        if (data.advice) return data.advice;
      }
    } catch (e) {
      // Fallback
    }

    if (expense > income && income > 0) {
      return `Your spending in ${category} is currently outpacing income. Consider pausing non-essential purchases this week to bring your cash flow back into surplus.`;
    }
    return `Aim to direct at least 20% of your earnings straight into savings. Keeping discretionary spending in ${category} modest will protect your monthly goals.`;
  }
}
