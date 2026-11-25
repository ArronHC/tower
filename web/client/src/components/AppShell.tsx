import React from 'react';
import {
  Globe,
  Layers,
  Lock,
  Moon,
  Settings,
  Share2,
  Sun,
  Monitor,
} from 'lucide-react';
import { useTower } from '@/state/TowerContext';
import type { AppTab, ThemePreference } from '@shared/index';

const TABS: Array<{ id: AppTab; label: string; icon: React.ComponentType<{ className?: string }> }> = [
  { id: 'subscriptions', label: '订阅', icon: Globe },
  { id: 'rules', label: '规则', icon: Layers },
  { id: 'export', label: '导出', icon: Share2 },
  { id: 'settings', label: '设置', icon: Settings },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const { activeTab, setActiveTab, state, updateTheme, resolvedDark } = useTower();
  const theme: ThemePreference = state?.snapshot.settings.theme ?? 'system';

  const cycleTheme = () => {
    const order: ThemePreference[] = ['system', 'light', 'dark'];
    const next = order[(order.indexOf(theme) + 1) % order.length] ?? 'system';
    void updateTheme(next);
  };

  const themeTitle =
    theme === 'system' ? '外观：跟随系统' : theme === 'dark' ? '外观：深色' : '外观：浅色';

  return (
    <div className="min-h-screen flex flex-col">
      {/* Desktop & Mobile Top Bar */}
      <header className="sticky top-0 z-30 border-b border-[var(--tower-border)] tower-glass">
        <div className="max-w-[1120px] mx-auto px-4 sm:px-6 h-15 flex items-center justify-between gap-4">
          {/* Brand */}
          <div className="flex items-center gap-2.5 shrink-0">
            <img
              src="/assets/tower-icon.png"
              alt="塔台 Tower"
              className="w-8 h-8 rounded-[9px] shadow-xs object-cover bg-[var(--tower-accent-soft)]"
            />
            <div className="flex items-baseline gap-1.5">
              <span className="text-[17px] font-bold tracking-tight text-[var(--tower-text)]">
                塔台
              </span>
              <span className="text-xs font-medium text-[var(--tower-text-secondary)] hidden sm:inline">
                Tower
              </span>
            </div>
          </div>

          {/* Desktop Segmented Navigation */}
          <nav
            aria-label="主导航"
            className="hidden md:flex items-center p-1 rounded-full bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]"
          >
            {TABS.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-medium transition-all cursor-pointer ${
                    isActive
                      ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                      : 'text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </nav>

          {/* Right Utilities */}
          <div className="flex items-center gap-2 shrink-0">
            <div
              className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-xs font-semibold"
              title="所有订阅解析、规则处理与配置生成均在本机运行，绝不上传第三方服务"
            >
              <Lock className="w-3 h-3" />
              <span>全部在本机处理</span>
            </div>

            <button
              type="button"
              onClick={cycleTheme}
              title={themeTitle}
              aria-label={themeTitle}
              className="w-9 h-9 rounded-full flex items-center justify-center bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] transition-colors cursor-pointer"
            >
              {theme === 'system' ? (
                <Monitor className="w-4 h-4" />
              ) : resolvedDark ? (
                <Moon className="w-4 h-4" />
              ) : (
                <Sun className="w-4 h-4" />
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 w-full max-w-[1120px] mx-auto px-4 sm:px-6 pt-5 pb-24 md:pb-12">
        {children}
      </main>

      {/* Mobile iOS-style Bottom Tab Bar */}
      <nav
        aria-label="移动端导航"
        className="md:hidden fixed bottom-0 inset-x-0 z-30 border-t border-[var(--tower-border)] tower-glass pb-[env(safe-area-inset-bottom)]"
      >
        <div className="grid grid-cols-4 h-14">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={`flex flex-col items-center justify-center gap-0.5 text-[11px] font-medium transition-colors cursor-pointer ${
                  isActive
                    ? 'text-[var(--tower-accent)]'
                    : 'text-[var(--tower-text-secondary)]'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
