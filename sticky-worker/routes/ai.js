const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY || '';
const DEEPSEEK_API_URL = 'https://api.deepseek.com/chat/completions';

// Proxy DeepSeek API calls — keeps API key server-side
router.post('/', async (req, res) => {
  try {
    if (!DEEPSEEK_API_KEY) {
      return res.status(500).json({ error: 'DEEPSEEK_API_KEY not configured on server' });
    }

    const { messages, temperature, max_tokens } = req.body;
    if (!messages || !Array.isArray(messages) || messages.length === 0) {
      return res.status(400).json({ error: 'messages array is required' });
    }

    const resp = await fetch(DEEPSEEK_API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${DEEPSEEK_API_KEY}`
      },
      body: JSON.stringify({
        model: 'deepseek-chat',
        messages,
        temperature: typeof temperature === 'number' ? temperature : 0.8,
        max_tokens: typeof max_tokens === 'number' ? max_tokens : 4096,
      })
    });

    if (!resp.ok) {
      const errText = await resp.text();
      return res.status(resp.status).json({ error: 'DeepSeek API error', detail: errText.slice(0,500) });
    }

    const data = await resp.json();
    res.json({ content: data.choices?.[0]?.message?.content || '' });
  } catch (err) {
    console.error('[AI] Proxy error:', err.message);
    res.status(500).json({ error: 'AI proxy error', message: err.message });
  }
});

module.exports = router;
