/**
 * scraper-proxy.js
 * Geizhals.eu Cloudflare Delici Stealth Proxy Sunucusu
 *
 * Bağımlılıklar:
 *   npm install express puppeteer-extra puppeteer-extra-plugin-stealth
 *
 * Çalıştırma:
 *   node scripts/scraper-proxy.js
 *
 * Portlar:
 *   Proxy dinleme: 3333
 *
 * Endpoints:
 *   GET  /health
 *   POST /category-links   { url, waitSelector? }
 *   POST /scrape-product   { url }
 */

"use strict";

const express = require("express");
const puppeteer = require("puppeteer-extra");
const StealthPlugin = require("puppeteer-extra-plugin-stealth");

puppeteer.use(StealthPlugin());

const PORT = process.env.PROXY_PORT || 3333;
const NAV_TIMEOUT = 60_000;        // Sayfa yüklenme genel timeout
const SELECTOR_TIMEOUT = 30_000;   // Kullanıcının CF'i elle geçmesi için 30 sn

const app = express();
app.use(express.json());

// CORS — Admin panelin farklı origin'den bağlanabilmesi için
app.use((req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

// ─── Tarayıcı yönetimi ──────────────────────────────────────────────────────

let browser = null;

async function getBrowser() {
  if (browser && browser.connected) return browser;

  browser = await puppeteer.launch({
    headless: false,          // KESİN KURAL: tarayıcı görünür açılacak
    defaultViewport: null,    // Gerçek pencere boyutunu kullan
    args: [
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-blink-features=AutomationControlled",
      "--disable-infobars",
      "--window-size=1366,768",
      // Mobil 4G bağlantı görünümü
      "--window-position=0,0",
    ],
    ignoreDefaultArgs: ["--enable-automation"],
  });

  browser.on("disconnected", () => {
    console.warn("[Proxy] Tarayıcı kapandı, bir sonraki istekte yeniden açılacak.");
    browser = null;
  });

  console.log("[Proxy] Tarayıcı başlatıldı (headless: false)");
  return browser;
}

async function getPage() {
  const b = await getBrowser();
  const pages = await b.pages();
  // Varolan boş sekmeyi kullan, yoksa yeni aç
  const page = pages.find((p) => p.url() === "about:blank") || (await b.newPage());

  await page.setExtraHTTPHeaders({
    "Accept-Language": "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7",
  });

  await page.setUserAgent(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
  );

  page.setDefaultNavigationTimeout(NAV_TIMEOUT);
  return page;
}

// ─── Yardımcı: Sayfa gezintisi + selector bekleme ──────────────────────────

/**
 * URL'ye git; verilen selectorları 30 sn içinde bekle.
 * Başarısız olursa { ok: false } döner — asla HTML fırlatmaz.
 */
async function navigateAndWait(page, url, selectors) {
  try {
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: NAV_TIMEOUT });
  } catch (e) {
    // Navigation timeout — sayfa yine de kısmen yüklenmiş olabilir, devam et
    console.warn("[Proxy] Navigasyon timeout, selector bekleniyor...", e.message);
  }

  // Selectorlardan herhangi biri görünürse başarılı say
  const selectorList = Array.isArray(selectors) ? selectors : [selectors];

  try {
    await Promise.race(
      selectorList.map((sel) =>
        page.waitForSelector(sel, { timeout: SELECTOR_TIMEOUT, visible: true })
      )
    );
    return { ok: true };
  } catch {
    return { ok: false };
  }
}

// ─── Endpoint: /health ──────────────────────────────────────────────────────

app.get("/health", (req, res) => {
  res.json({ status: "ok", browserConnected: !!(browser && browser.connected) });
});

// ─── Endpoint: /category-links ─────────────────────────────────────────────
/**
 * Body: { url: string, waitSelector?: string }
 * Returns: { links: string[] }
 *
 * Geizhals kategori listesindeki YALNIZCA ana ürün linklerini döner.
 * Sidebar / Top-10 / Popüler listelerini kesinlikle dışlar.
 */
app.post("/category-links", async (req, res) => {
  const { url, waitSelector } = req.body || {};
  if (!url) return res.status(400).json({ error: "url_required", message: "url parametresi zorunludur." });

  let page;
  try {
    page = await getPage();

    // Ürün listesi için bilinen Geizhals selectorları
    const listSelectors = [
      ".productlist",
      "#productlist",
      ".offer-list",
      '[data-testid="product-list"]',
      ".list-page",
    ];
    if (waitSelector) listSelectors.unshift(waitSelector);

    const nav = await navigateAndWait(page, url, listSelectors);

    if (!nav.ok) {
      return res
        .status(503)
        .json({ error: "cloudflare_challenge", message: "Cloudflare aşılamadı" });
    }

    // ── Ana listedeki ürün linklerini topla ────────────────────────────────
    const links = await page.evaluate(() => {
      /**
       * Geizhals'ta ürün listesi ya .productlist ya da #productlist içindedir.
       * Sidebar selectorları:
       *   - .widget, .aside, aside, [class*="sidebar"], [class*="top10"],
       *     [class*="popular"], [class*="ad-"], .nav-bar-inner
       *
       * Strateji:
       *   1. Ana liste kapsayıcısını bul.
       *   2. Yalnızca doğrudan çocuk ürün öğelerinden <a> al.
       *   3. Geizhals ürün URL pattern'ini doğrula: /p/ veya /offer/ içermeli.
       */
      const PRODUCT_URL_PATTERN = /\/p\/\d+|\/offer\//;

      // Sidebar dışarıda bırakılacak ancestor selectorları
      const EXCLUDE_ANCESTORS = [
        ".widget",
        "aside",
        ".aside",
        '[class*="sidebar"]',
        '[class*="top10"]',
        '[class*="popular"]',
        '[class*="recommendation"]',
        '[class*="ad-"]',
        ".nav-bar-inner",
        ".nav",
        "nav",
        "header",
        "footer",
      ];

      function isInsideExcluded(el) {
        let node = el.parentElement;
        while (node) {
          for (const sel of EXCLUDE_ANCESTORS) {
            try {
              if (node.matches(sel)) return true;
            } catch {}
          }
          node = node.parentElement;
        }
        return false;
      }

      // Ana liste kapsayıcısı
      const listContainer =
        document.querySelector(".productlist") ||
        document.querySelector("#productlist") ||
        document.querySelector(".offer-list") ||
        document.querySelector('[data-testid="product-list"]') ||
        document.querySelector(".list-page");

      if (!listContainer) return [];

      const anchors = listContainer.querySelectorAll("a[href]");
      const seen = new Set();
      const results = [];

      for (const a of anchors) {
        const href = a.getAttribute("href");
        if (!href) continue;

        // Tam URL oluştur
        let fullUrl;
        try {
          fullUrl = new URL(href, "https://geizhals.eu").href;
        } catch {
          continue;
        }

        // Ürün URL pattern kontrolü
        if (!PRODUCT_URL_PATTERN.test(fullUrl)) continue;

        // Dışlanan alan kontrolü
        if (isInsideExcluded(a)) continue;

        // Tekrar kontrolü
        if (seen.has(fullUrl)) continue;
        seen.add(fullUrl);
        results.push(fullUrl);
      }

      return results;
    });

    return res.json({ links });
  } catch (err) {
    console.error("[Proxy] /category-links hatası:", err);
    return res
      .status(500)
      .json({ error: "internal_error", message: err.message });
  } finally {
    // Sekmeyi kapatma — kullanıcı CF kutusunu görebilmeli
    // page?.close(); // KAPATILDI — headless:false modunda açık kalmalı
  }
});

// ─── Endpoint: /scrape-product ─────────────────────────────────────────────
/**
 * Body: { url: string }
 * Returns: { title, price, specs, images, url }
 *
 * Ürün özellikleri çekilirken span/div/li düğümleri gezilip
 * aralarına ", " eklenir — virgül yutma bug'ı giderilmiş.
 */
app.post("/scrape-product", async (req, res) => {
  const { url } = req.body || {};
  if (!url) return res.status(400).json({ error: "url_required", message: "url parametresi zorunludur." });

  let page;
  try {
    page = await getPage();

    // Ürün detay sayfası için beklenen selectorlar
    const detailSelectors = [
      ".productpage",
      "#productpage",
      ".product-detail",
      '[class*="product-header"]',
      ".offer-list-item",
      "h1.product-name",
      "h1",
    ];

    const nav = await navigateAndWait(page, url, detailSelectors);

    if (!nav.ok) {
      return res
        .status(503)
        .json({ error: "cloudflare_challenge", message: "Cloudflare aşılamadı" });
    }

    const product = await page.evaluate((productUrl) => {
      // ── Başlık ────────────────────────────────────────────────────────────
      const titleEl =
        document.querySelector("h1.product-name") ||
        document.querySelector("h1") ||
        document.querySelector('[class*="product-title"]');
      const title = titleEl ? titleEl.innerText.trim() : "";

      // ── Fiyat ─────────────────────────────────────────────────────────────
      const priceEl =
        document.querySelector(".price--best") ||
        document.querySelector('[class*="price"]') ||
        document.querySelector(".offerlist-price") ||
        document.querySelector(".best_price");
      const price = priceEl ? priceEl.innerText.trim() : "";

      // ── Özellikler (VİRGÜL BUG FIX) ───────────────────────────────────────
      /**
       * Sorun: Farklı etiketlerin textContent'i doğrudan birleştirilince
       *        "32GB GDDR7 512bit" gibi virgülsüz çıktı oluşuyor.
       *
       * Çözüm: Her spec satırının ALT DÜĞÜMLER (childNodes) üzerinde gezilerek
       *        etiket sınırlarına ", " eklenmesi.
       */
      function extractSpecText(container) {
        /**
         * Bir element'in alt düğümlerini gezerek metin parçalarını toplar.
         * Etiket geçişlerinde (element node → element node) araya ", " ekler.
         * TextNode'lardan sadece boş olmayan içerik alır.
         */
        const parts = [];
        let lastWasElement = false;

        function walk(node) {
          if (node.nodeType === Node.TEXT_NODE) {
            const text = node.textContent.trim();
            if (text) {
              if (lastWasElement && parts.length > 0) {
                // Bir önceki etiketin ardından gelen metin — araya virgül
                const last = parts[parts.length - 1];
                if (!last.endsWith(",") && !last.endsWith(":")) {
                  parts.push(",");
                }
              }
              parts.push(text);
              lastWasElement = false;
            }
          } else if (node.nodeType === Node.ELEMENT_NODE) {
            const tag = node.tagName.toLowerCase();
            // İç içe span/strong/em vs. — walk et
            if (["span", "strong", "em", "b", "i", "small", "a"].includes(tag)) {
              const hadParts = parts.length;
              Array.from(node.childNodes).forEach(walk);
              if (parts.length > hadParts) lastWasElement = true;
            } else if (["br", "wbr"].includes(tag)) {
              // Satır kırma → virgül
              if (parts.length > 0) {
                const last = parts[parts.length - 1];
                if (last !== "," && last !== ", ") parts.push(",");
              }
            } else {
              // div, li, p, td vs. → özyinelemeli, ayrı parça
              const before = parts.length;
              Array.from(node.childNodes).forEach(walk);
              if (parts.length > before) lastWasElement = true;
            }
          }
        }

        Array.from(container.childNodes).forEach(walk);

        // Parçaları birleştir: virgüllerden önce boşluk kaldır, sonra boşluk ekle
        let result = "";
        for (let i = 0; i < parts.length; i++) {
          const cur = parts[i];
          if (cur === ",") {
            // Trailing whitespace kaldır, virgül ekle
            result = result.trimEnd() + ", ";
          } else {
            if (result && !result.endsWith(" ")) result += " ";
            result += cur;
          }
        }
        return result.trim().replace(/,\s*,/g, ",").replace(/\s{2,}/g, " ");
      }

      // Spec tablosu/listesi
      const specs = {};
      const specRows = document.querySelectorAll(
        [
          ".spec-table tr",
          ".specs-table tr",
          ".tech-specs tr",
          ".product-specs li",
          '[class*="spec"] tr',
          '[class*="spec"] li',
          "table.specs tr",
          "dl.specs dt",
          ".attribute-list li",
        ].join(", ")
      );

      if (specRows.length > 0) {
        specRows.forEach((row) => {
          // dt/dd veya th/td veya key/value yapısı
          const labelEl =
            row.querySelector("th") ||
            row.querySelector("dt") ||
            row.querySelector('[class*="label"]') ||
            row.querySelector('[class*="key"]') ||
            row.querySelector('[class*="name"]');

          const valueEl =
            row.querySelector("td") ||
            row.querySelector("dd") ||
            row.querySelector('[class*="value"]');

          if (labelEl && valueEl) {
            const label = labelEl.innerText.trim().replace(/:$/, "");
            const value = extractSpecText(valueEl);
            if (label && value) specs[label] = value;
          } else if (!labelEl && !valueEl) {
            // Sadece tek sütunlu li vs.
            const text = extractSpecText(row);
            if (text) {
              const colonIdx = text.indexOf(":");
              if (colonIdx > 0) {
                specs[text.slice(0, colonIdx).trim()] = text.slice(colonIdx + 1).trim();
              }
            }
          }
        });
      }

      // Specs boşsa genel description dene
      if (Object.keys(specs).length === 0) {
        const descEl =
          document.querySelector(".product-description") ||
          document.querySelector('[class*="description"]');
        if (descEl) specs["Açıklama"] = extractSpecText(descEl);
      }

      // ── Görseller ─────────────────────────────────────────────────────────
      const images = [];
      const imgEls = document.querySelectorAll(
        '.product-images img, [class*="gallery"] img, [class*="product-image"] img'
      );
      imgEls.forEach((img) => {
        const src = img.getAttribute("data-src") || img.getAttribute("src") || "";
        if (src && !src.includes("placeholder") && !src.includes("1x1")) {
          try {
            images.push(new URL(src, "https://geizhals.eu").href);
          } catch {}
        }
      });

      return { title, price, specs, images, url: productUrl };
    }, url);

    return res.json(product);
  } catch (err) {
    console.error("[Proxy] /scrape-product hatası:", err);
    return res
      .status(500)
      .json({ error: "internal_error", message: err.message });
  }
});

// ─── Sunucuyu başlat ────────────────────────────────────────────────────────

app.listen(PORT, () => {
  console.log(`[Proxy] Geizhals Stealth Proxy çalışıyor → http://localhost:${PORT}`);
  console.log(`[Proxy] Sağlık kontrolü: http://localhost:${PORT}/health`);
});
