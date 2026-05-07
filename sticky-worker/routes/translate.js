const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const TARGET_LANGUAGES = ['tr','de','fr','es','pt','it','ru','ar','hi','ja','ko','zh','th','vi','id','fil'];

async function translateText(text, targetLang) {
  if (!text || !text.trim()) return text;
  const lang = targetLang === 'fil' ? 'tl' : targetLang;
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${lang}&dt=t&q=${encodeURIComponent(text)}`;
  const resp = await fetch(url);
  const data = await resp.json();
  return data?.[0]?.[0]?.[0] || text;
}

router.all('/', async (req, res) => {
  try {
    const text = req.body?.text || req.query?.text;
    if (!text) return res.status(400).json({ error: 'text required' });

    const targets = req.body?.targets || TARGET_LANGUAGES;
    const translations = { name_en: text };

    await Promise.all(targets.map(async (lang) => {
      try {
        translations[`name_${lang}`] = await translateText(text, lang);
      } catch (e) {
        console.error(`[Translate] ${lang}:`, e.message);
      }
    }));

    res.json(translations);
  } catch (err) {
    console.error('[Translate]', err.message);
    res.status(500).json({ error: 'Translation error', message: err.message });
  }
});

module.exports = router;
