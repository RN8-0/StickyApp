import { Component, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: any) {
    console.error('[ErrorBoundary]', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          minHeight: '100vh', background: '#0a0a0f', color: '#e2e8f0',
          fontFamily: 'system-ui, sans-serif', padding: '2rem', textAlign: 'center'
        }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>⚠️</div>
          <h1 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '0.5rem' }}>Something went wrong</h1>
          <p style={{ color: '#94a3b8', fontSize: '0.875rem', marginBottom: '1.5rem', maxWidth: 480 }}>
            {this.state.error?.message || 'An unexpected error occurred.'}
          </p>
          <button
            onClick={() => { this.setState({ hasError: false, error: null }); window.location.reload(); }}
            style={{
              padding: '0.625rem 1.5rem', borderRadius: '0.75rem', border: 'none',
              background: '#7c3aed', color: '#fff', fontWeight: 600, cursor: 'pointer',
              fontSize: '0.875rem',
            }}
          >
            Reload Page
          </button>
          {import.meta.env.DEV && this.state.error && (
            <pre style={{ marginTop: '2rem', fontSize: '0.75rem', color: '#64748b', textAlign: 'left', maxWidth: '100%', overflow: 'auto' }}>
              {this.state.error.stack}
            </pre>
          )}
        </div>
      );
    }
    return this.props.children;
  }
}
