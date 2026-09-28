import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    {
      name: 'gemini-ai-proxy',
      configureServer(server) {
        server.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith('/api/ai/')) {
            return next();
          }

          const apiKey = process.env.GEMINI_API_KEY;

          // Helper to read JSON body
          const readBody = async (): Promise<any> => {
            return new Promise((resolve, reject) => {
              let body = '';
              req.on('data', chunk => { body += chunk; });
              req.on('end', () => {
                try {
                  resolve(body ? JSON.parse(body) : {});
                } catch (e) {
                  resolve({});
                }
              });
              req.on('error', reject);
            });
          };

          const sendJson = (data: any, status = 200) => {
            res.statusCode = status;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify(data));
          };

          try {
            if (req.url === '/api/ai/coach-advice' && req.method === 'POST') {
              const { income, expense, category } = await readBody();
              if (!apiKey) {
                return sendJson({
                  advice: `Aim to save at least 20% of your income. Keep discretionary expenses in ${category || 'Food & Dining'} measured to strengthen your emergency fund.`
                });
              }

              const prompt = `Monthly Income: ₹${income}, Total Expenses: ₹${expense}, Top Expense Category: ${category}. Give 2 crisp, actionable tips in clean English.`;
              const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  systemInstruction: { parts: [{ text: "You are an empathetic financial coach. Provide a concise, actionable 2-sentence piece of financial advice in English based on the user's spending." }] },
                  generationConfig: { temperature: 0.2, maxOutputTokens: 256 },
                  contents: [{ parts: [{ text: prompt }] }]
                })
              });

              if (response.ok) {
                const data = await response.json();
                const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || '';
                return sendJson({ advice: text || `Focus on budgeting ${category} and maintaining a 20% savings buffer.` });
              }
              return sendJson({
                advice: `Focus on balancing your expenses and prioritizing savings in ${category || 'General'}.`
              });
            }

            if (req.url === '/api/ai/voice-parse' && req.method === 'POST') {
              const { prompt } = await readBody();
              if (!apiKey) {
                return sendJson({ parsed: null }); // Client will use local fallback parser
              }

              const systemInstruction = `You are a domain-expert financial transaction parser for Zenith CashFlow, an intelligent personal & family finance app.
The user will provide a transaction command in natural English or Romanized Tanglish (Tamil phrases written strictly in English/Latin alphabet).

PARSING RULES:
1. Language: Always output ALL JSON values (title, note, category, payment method) strictly in clean, professional English. Never use Tamil script characters.
2. Transaction Type: "INCOME" or "EXPENSE"
3. Scope: "FAMILY" or "PERSONAL"
4. Title: Concise, clear standard English title (e.g. "Lunch", "Movie Tickets", "Monthly Salary", "House Rent", "Medicines for Father")
5. Amount: Numeric value (e.g. 250.0).
6. Category: Choose best from: "Food & Dining", "Transportation", "Shopping", "Entertainment", "Bills & Utilities", "Housing & Rent", "Healthcare", "Education", "Salary & Income", "Investments", "Other"
7. Payment Method: Choose best from: "UPI", "Cash", "Credit Card", "Debit Card", "Bank Transfer"
Return plain JSON only without markdown formatting with keys: title, amount, type, category, paymentMethod, scope, note.`;

              const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  systemInstruction: { parts: [{ text: systemInstruction }] },
                  generationConfig: { temperature: 0.1, maxOutputTokens: 256, responseMimeType: 'application/json' },
                  contents: [{ parts: [{ text: prompt || '' }] }]
                })
              });

              if (response.ok) {
                const data = await response.json();
                const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || '';
                const parsed = JSON.parse(text.replace(/```json/g, '').replace(/```/g, '').trim());
                return sendJson({ parsed });
              }
              return sendJson({ parsed: null });
            }

            if (req.url === '/api/ai/receipt-scan' && req.method === 'POST') {
              const { text: receiptText, imageBase64 } = await readBody();
              if (!apiKey) {
                return sendJson({ parsed: null });
              }

              const systemInstruction = `You are a receipt scanner OCR AI. Analyze the receipt and return ONLY a JSON object:
- "merchantName": name of store or restaurant
- "receiptNumber": invoice / receipt number or null
- "dateString": detected date or "Today"
- "timeString": detected time or ""
- "category": ("Food & Dining", "Shopping", "Transportation", "Bills & Utilities", "Healthcare", "Other")
- "paymentMethod": ("UPI", "Cash", "Card", "Bank Transfer", "Other")
- "subtotal": number
- "discount": number
- "tax": number
- "totalAmount": number
- "itemsSummary": concise 1-line comma separated items list
- "items": array of { name: string, quantity: number, unitPrice: number, totalPrice: number }
Return plain JSON only without markdown formatting.`;

              const contents: any[] = [];
              if (imageBase64) {
                const cleanBase64 = imageBase64.includes(',') ? imageBase64.split(',')[1] : imageBase64;
                contents.push({
                  parts: [
                    { inlineData: { mimeType: 'image/jpeg', data: cleanBase64 } },
                    { text: 'Parse this receipt image.' }
                  ]
                });
              } else {
                contents.push({ parts: [{ text: receiptText || '' }] });
              }

              const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  systemInstruction: { parts: [{ text: systemInstruction }] },
                  generationConfig: { temperature: 0.1, maxOutputTokens: 512, responseMimeType: 'application/json' },
                  contents
                })
              });

              if (response.ok) {
                const data = await response.json();
                const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || '';
                const parsed = JSON.parse(text.replace(/```json/g, '').replace(/```/g, '').trim());
                return sendJson({ parsed });
              }
              return sendJson({ parsed: null });
            }

            next();
          } catch (err: any) {
            console.error('API Error:', err);
            sendJson({ error: err.message || 'Internal error' }, 500);
          }
        });
      }
    }
  ],
  server: {
    host: '0.0.0.0',
    port: 3000
  }
});
