import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ErrorBoundary } from './ErrorBoundary'

const rootElement = document.getElementById('root');
if (!rootElement) {
  console.error('[FATAL] Root element #root not found in DOM');
  const errorDiv = document.getElementById('js-error');
  if (errorDiv) errorDiv.style.display = 'flex';
} else {
  try {
    createRoot(rootElement).render(
      <StrictMode>
        <ErrorBoundary>
          <App />
        </ErrorBoundary>
      </StrictMode>,
    );
  } catch (err) {
    console.error('[FATAL] React initialization error:', err);
    const errorDiv = document.getElementById('js-error');
    if (errorDiv) errorDiv.style.display = 'flex';
  }
}
