import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ErrorBoundary } from './ErrorBoundary'

// The inline script in index.html handles JS-load failure (30s timeout + service worker cleanup).
// ErrorBoundary wraps App to catch render-time errors.
// At this point the DOM is guaranteed ready (script runs after #root).
const rootElement = document.getElementById('root');
if (!rootElement) {
  console.error('[FATAL] Root element #root not found in DOM');
} else {
  createRoot(rootElement).render(
    <StrictMode>
      <ErrorBoundary>
        <App />
      </ErrorBoundary>
    </StrictMode>,
  );
}
