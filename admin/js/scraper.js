/**
 * admin/js/scraper.js
 * Geizhals.eu Admin Panel — UI ve Döngü Yöneticisi
 *
 * Bağımlılık: scraper-proxy.js'in http://localhost:3333 üzerinde çalışıyor olması gerekir.
 * Bu dosya, admin/index.html içindeki ilgili HTML elemanlarını kullanır.
 *
 * Beklenen HTML yapısı (admin/index.html):
 *   #category-url      → Geizhals kategori URL giriş kutusu
 *   #max-pages         → Kaç sayfa (pagination) taranacak
 *   #delay-ms          → İstekler arası bekleme (ms)
 *   #btn-collect       → "Linkleri Topla" butonu
 *   #btn-scrape        → "Ürünleri Çek" butonu
 *   #btn-export        → "JSON İndir" butonu
 *   #btn-clear         → "Temizle" butonu
 *   #log-output        → Log alanı (div/pre)
 *   #link-count        → Toplanan link sayısı
 *   #product-count     → Çekilen ürün sayısı
 *   #results-table     → Sonuç tablosu tbody
 *   #progress-bar      → İlerleme çubuğu
 *   #progress-text     → İlerleme metni
 */

"use strict";

// ─── Yapılandırma ───────────────────────────────────────────────────────────

const PROXY_BASE = "http://localhost:3333";

// ─── Durum ──────────────────────────────────────────────────────────────────

let collectedLinks = [];   // Toplanan ürün URL listesi
let scrapedProducts = [];  // Çekilen ürün verisi
let isStopped = false;     // Acil durdurma bayrağı

// ─── DOM Yardımcıları ────────────────────────────────────────────────────────

function $(id) {
  return document.getElementById(id);
}

function log(message, type = "info") {
  const logEl = $("log-output");
  if (!logEl) return;

  const line = document.createElement("div");
  line.className = `log-line log-${type}`;

  const timeStr = new Date().toLocaleTimeString("tr-TR");
  line.innerHTML = `<span class="log-time">[${timeStr}]</span> ${message}`;
  logEl.appendChild(line);
  logEl.scrollTop = logEl.scrollHeight;
}

function setButtonState(disabled) {
  const btns = ["btn-collect", "btn-scrape"];
  btns.forEach((id) => {
    const el = $(id);
    if (el) el.disabled = disabled;
  });
}

function updateLinkCount() {
  const el = $("link-count");
  if (el) el.textContent = collectedLinks.length;
}

function updateProductCount() {
  const el = $("product-count");
  if (el) el.textContent = scrapedProducts.length;
}

function setProgress(current, total) {
  const bar = $("progress-bar");
  const text = $("progress-text");
  const pct = total > 0 ? Math.round((current / total) * 100) : 0;
  if (bar) bar.style.width = `${pct}%`;
  if (text) text.textContent = `${current} / ${total} (${pct}%)`;
}

// ─── Gecikme ────────────────────────────────────────────────────────────────

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ─── Proxy İstekleri ────────────────────────────────────────────────────────

/**
 * Proxy'e istek atar.
 * Dönüş: { ok: boolean, data?: any, cloudflare?: boolean, error?: string }
 *
 * KESİN KURAL: Promise.all KULLANILMIYOR.
 * Her istek sırayla (for...of) yapılır.
 */
async function proxyPost(endpoint, body) {
  let response;
  let rawText;

  try {
    response = await fetch(`${PROXY_BASE}${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (networkErr) {
    return {
      ok: false,
      error: `Ağ hatası: Proxy'e bağlanılamadı. (${networkErr.message})`,
    };
  }

  // Ham metni al — JSON parse'dan ÖNCE HTML kontrolü yap
  try {
    rawText = await response.text();
  } catch {
    return { ok: false, error: "Proxy yanıtı okunamadı." };
  }

  // Cloudflare HTML yanıt kontrolü (503 veya DOCTYPE / <html> içerik)
  if (
    response.status === 503 ||
    rawText.trimStart().startsWith("<!") ||
    rawText.trimStart().toLowerCase().startsWith("<html")
  ) {
    return { ok: false, cloudflare: true };
  }

  // JSON parse
  let data;
  try {
    data = JSON.parse(rawText);
  } catch {
    return {
      ok: false,
      error: `Proxy beklenmeyen yanıt döndü (JSON değil): ${rawText.slice(0, 120)}`,
    };
  }

  // Cloudflare hata kodu JSON içinde
  if (data && data.error === "cloudflare_challenge") {
    return { ok: false, cloudflare: true };
  }

  if (!response.ok) {
    return { ok: false, error: data?.message || `HTTP ${response.status}` };
  }

  return { ok: true, data };
}

// ─── Geizhals Sayfalama URL'si ───────────────────────────────────────────────

/**
 * Geizhals kategori URL'sine sayfa parametresi ekler.
 * Örnek: https://geizhals.eu/?cat=gra16_512 → &pg=2
 */
function buildPageUrl(baseUrl, pageNum) {
  if (pageNum === 1) return baseUrl;
  try {
    const u = new URL(baseUrl);
    u.searchParams.set("pg", pageNum);
    return u.href;
  } catch {
    const sep = baseUrl.includes("?") ? "&" : "?";
    return `${baseUrl}${sep}pg=${pageNum}`;
  }
}

// ─── BÖLÜM 1: Linkleri Topla ────────────────────────────────────────────────

async function collectLinks() {
  const categoryUrl = ($("category-url")?.value || "").trim();
  const maxPages = parseInt($("max-pages")?.value || "1", 10) || 1;
  const delayMs = parseInt($("delay-ms")?.value || "2000", 10) || 2000;

  if (!categoryUrl) {
    log("⚠️ Kategori URL giriniz.", "warn");
    return;
  }

  collectedLinks = [];
  isStopped = false;
  updateLinkCount();
  setButtonState(true);
  setProgress(0, maxPages);

  log(`🔍 Link toplama başladı — ${maxPages} sayfa, ${delayMs}ms gecikme`, "info");

  for (let page = 1; page <= maxPages; page++) {
    if (isStopped) break;

    const pageUrl = buildPageUrl(categoryUrl, page);
    log(`📄 Sayfa ${page}/${maxPages}: ${pageUrl}`, "info");

    const result = await proxyPost("/category-links", { url: pageUrl });

    // ── FAIL-FAST: Cloudflare veya hata ─────────────────────────────────────
    if (!result.ok) {
      if (result.cloudflare) {
        log(
          "🛑 Hata: Cloudflare güvenlik duvarı geçilemedi. İşlem durduruldu.",
          "error"
        );
      } else {
        log(`🛑 Hata: ${result.error || "Bilinmeyen hata"}. İşlem durduruldu.`, "error");
      }
      isStopped = true;
      break; // ANINDA KIR
    }

    const newLinks = result.data?.links || [];
    if (newLinks.length === 0) {
      log(`⚠️ Sayfa ${page}: Hiç ürün linki bulunamadı. Sonraki sayfa yok.`, "warn");
      break;
    }

    // Tekrar filtrele
    const before = collectedLinks.length;
    newLinks.forEach((link) => {
      if (!collectedLinks.includes(link)) collectedLinks.push(link);
    });
    const added = collectedLinks.length - before;

    log(`✅ Sayfa ${page}: ${added} yeni link eklendi (toplam: ${collectedLinks.length})`, "success");
    updateLinkCount();
    setProgress(page, maxPages);

    // Son sayfa değilse bekle
    if (page < maxPages && !isStopped) {
      await delay(delayMs);
    }
  }

  if (!isStopped) {
    log(`🎯 Link toplama tamamlandı. Toplam: ${collectedLinks.length} ürün linki.`, "success");
  }

  setButtonState(false);
  setProgress(maxPages, maxPages);
}

// ─── BÖLÜM 2: Ürünleri Çek ──────────────────────────────────────────────────

async function scrapeProducts() {
  if (collectedLinks.length === 0) {
    log("⚠️ Önce ürün linklerini toplayınız.", "warn");
    return;
  }

  const delayMs = parseInt($("delay-ms")?.value || "3000", 10) || 3000;
  isStopped = false;
  setButtonState(true);
  setProgress(0, collectedLinks.length);

  log(`🚀 Ürün çekme başladı — ${collectedLinks.length} ürün, ${delayMs}ms gecikme`, "info");

  const total = collectedLinks.length;

  // ── PARALEL YASAK: for...of ile sıralı işlem ────────────────────────────
  for (let i = 0; i < collectedLinks.length; i++) {
    if (isStopped) break;

    const url = collectedLinks[i];
    log(`📦 [${i + 1}/${total}] ${url}`, "info");

    const result = await proxyPost("/scrape-product", { url });

    // ── FAIL-FAST ────────────────────────────────────────────────────────────
    if (!result.ok) {
      if (result.cloudflare) {
        log(
          "🛑 Hata: Cloudflare güvenlik duvarı geçilemedi. İşlem durduruldu.",
          "error"
        );
      } else {
        log(`🛑 Hata: ${result.error || "Bilinmeyen hata"}. İşlem durduruldu.`, "error");
      }
      isStopped = true;
      break; // ANINDA KIR
    }

    const product = result.data;

    // Tabloya ekle
    addProductToTable(product, i + 1);
    scrapedProducts.push(product);
    updateProductCount();
    setProgress(i + 1, total);

    log(`✅ [${i + 1}/${total}] "${product.title || url}" çekildi.`, "success");

    // Son ürün değilse bekle
    if (i < collectedLinks.length - 1 && !isStopped) {
      await delay(delayMs);
    }
  }

  if (!isStopped) {
    log(`🎉 Tüm ürünler çekildi. Toplam: ${scrapedProducts.length}`, "success");
  } else {
    log(`ℹ️ İşlem durduruldu. Mevcut veriler korunuyor (${scrapedProducts.length} ürün).`, "warn");
  }

  setButtonState(false);
}

// ─── Tablo Güncelle ─────────────────────────────────────────────────────────

function addProductToTable(product, index) {
  const tbody = $("results-table");
  if (!tbody) return;

  const tr = document.createElement("tr");

  const specSummary = Object.entries(product.specs || {})
    .slice(0, 3)
    .map(([k, v]) => `<b>${k}:</b> ${v}`)
    .join(" | ");

  tr.innerHTML = `
    <td>${index}</td>
    <td><a href="${product.url || ""}" target="_blank" rel="noopener">${escapeHtml(product.title || "-")}</a></td>
    <td>${escapeHtml(product.price || "-")}</td>
    <td class="spec-cell">${specSummary || "-"}</td>
    <td>${(product.images || []).length}</td>
  `;
  tbody.appendChild(tr);
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

// ─── JSON Dışa Aktar ─────────────────────────────────────────────────────────

function exportJSON() {
  if (scrapedProducts.length === 0) {
    log("⚠️ Dışa aktarılacak ürün yok.", "warn");
    return;
  }

  const data = JSON.stringify(scrapedProducts, null, 2);
  const blob = new Blob([data], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `geizhals-products-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);

  log(`💾 ${scrapedProducts.length} ürün JSON olarak indirildi.`, "success");
}

// ─── Temizle ─────────────────────────────────────────────────────────────────

function clearAll() {
  collectedLinks = [];
  scrapedProducts = [];
  isStopped = false;

  updateLinkCount();
  updateProductCount();
  setProgress(0, 0);

  const logEl = $("log-output");
  if (logEl) logEl.innerHTML = "";

  const tbody = $("results-table");
  if (tbody) tbody.innerHTML = "";

  log("🗑️ Tüm veriler temizlendi.", "info");
}

// ─── Acil Durdur ─────────────────────────────────────────────────────────────

function stopNow() {
  isStopped = true;
  log("⏹️ Durdurma isteği alındı. Mevcut istek tamamlandıktan sonra duracak.", "warn");
}

// ─── Event Listeners ─────────────────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", () => {
  const btnCollect = $("btn-collect");
  const btnScrape = $("btn-scrape");
  const btnExport = $("btn-export");
  const btnClear = $("btn-clear");
  const btnStop = $("btn-stop");

  if (btnCollect) btnCollect.addEventListener("click", collectLinks);
  if (btnScrape) btnScrape.addEventListener("click", scrapeProducts);
  if (btnExport) btnExport.addEventListener("click", exportJSON);
  if (btnClear) btnClear.addEventListener("click", clearAll);
  if (btnStop) btnStop.addEventListener("click", stopNow);

  // Başlangıç logu
  log("⚙️ Geizhals Scraper hazır. Proxy: " + PROXY_BASE, "info");

  // Proxy sağlık kontrolü
  fetch(`${PROXY_BASE}/health`)
    .then((r) => r.json())
    .then((d) => {
      if (d.status === "ok") {
        log("✅ Proxy bağlantısı başarılı.", "success");
      } else {
        log("⚠️ Proxy bağlı ama beklenmeyen yanıt.", "warn");
      }
    })
    .catch(() => {
      log("❌ Proxy'e bağlanılamadı! `node scripts/scraper-proxy.js` komutunu çalıştırın.", "error");
    });
});
