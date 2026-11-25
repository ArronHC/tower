import { AlertCircle, Loader2, RefreshCw } from 'lucide-react';
import { AppShell } from '@/components/AppShell';
import { ToastOverlay } from '@/components/ToastOverlay';
import { ExportPage } from '@/features/export/ExportPage';
import { WelcomeView } from '@/features/onboarding/WelcomeView';
import { RulesPage } from '@/features/rules/RulesPage';
import { SettingsPage } from '@/features/settings/SettingsPage';
import { SubscriptionsPage } from '@/features/subscriptions/SubscriptionsPage';
import { TowerProvider, useTower } from '@/state/TowerContext';

function TowerWorkspace() {
  const { activeTab, loading, loadError, reloadState } = useTower();

  return (
    <AppShell>
      <ToastOverlay />
      <WelcomeView />

      {loading ? (
        <div className="tower-card py-24 flex flex-col items-center justify-center gap-3 text-[var(--tower-text-secondary)]">
          <Loader2 className="w-7 h-7 animate-spin text-[var(--tower-accent)]" />
          <p className="text-sm font-medium">正在连接本地塔台服务…</p>
        </div>
      ) : loadError ? (
        <div className="tower-card p-8 max-w-md mx-auto mt-10 text-center space-y-4">
          <div className="w-12 h-12 mx-auto rounded-full bg-red-500/12 text-red-500 flex items-center justify-center">
            <AlertCircle className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-[var(--tower-text)]">无法连接本地后端</h2>
            <p className="mt-1 text-xs text-[var(--tower-text-secondary)] leading-relaxed">
              {loadError}
            </p>
          </div>
          <button
            type="button"
            onClick={() => void reloadState()}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 cursor-pointer"
          >
            <RefreshCw className="w-4 h-4" />
            <span>重试连接</span>
          </button>
        </div>
      ) : (
        <>
          {activeTab === 'subscriptions' && <SubscriptionsPage />}
          {activeTab === 'rules' && <RulesPage />}
          {activeTab === 'export' && <ExportPage />}
          {activeTab === 'settings' && <SettingsPage />}
        </>
      )}
    </AppShell>
  );
}

export function App() {
  return (
    <TowerProvider>
      <TowerWorkspace />
    </TowerProvider>
  );
}

export default App;
