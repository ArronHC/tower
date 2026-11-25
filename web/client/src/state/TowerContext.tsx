import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { api, ApiError } from '@/api/client';
import {
  DEFAULT_APP_SETTINGS,
  type AppSettings,
  type AppStateResponse,
  type AppTab,
  type CountryEntry,
  type NodeLatencyTestMode,
  type ThemePreference,
  type WorldDotMapData,
} from '@shared/index';

export type ToastTone = 'neutral' | 'success' | 'warning' | 'danger';

export interface ToastMessage {
  id: string;
  text: string;
  tone: ToastTone;
  spinning?: boolean;
}

const WELCOME_STORAGE_KEY = 'tower.web.welcome.dismissed.v1';

interface TowerContextValue {
  state: AppStateResponse | null;
  worldMap: WorldDotMapData | null;
  countries: CountryEntry[];
  activeTab: AppTab;
  setActiveTab: (tab: AppTab) => void;
  selectedCountryCode: string | null;
  setSelectedCountryCode: (code: string | null) => void;
  loading: boolean;
  loadError: string | null;
  refreshingAll: boolean;
  refreshingSubIds: Set<string>;
  testingLatency: boolean;
  testingNodeIds: Set<string>;
  toasts: ToastMessage[];
  showToast: (text: string, tone?: ToastTone, durationMs?: number) => string;
  dismissToast: (id: string) => void;
  showWelcome: boolean;
  setShowWelcome: (open: boolean) => void;
  dismissWelcomePermanently: () => void;
  reloadState: () => Promise<AppStateResponse | null>;
  applyStateFromMutation: (maybeState?: AppStateResponse) => Promise<AppStateResponse | null>;
  refreshAllSubscriptions: () => Promise<void>;
  refreshSingleSubscription: (id: string, name: string) => Promise<void>;
  testLatency: (nodeIds?: string[], mode?: NodeLatencyTestMode) => Promise<void>;
  updateTheme: (theme: ThemePreference) => Promise<void>;
  updateSettings: (patch: Partial<AppSettings>) => Promise<void>;
  resolvedDark: boolean;
}

const TowerContext = createContext<TowerContextValue | null>(null);

export function TowerProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AppStateResponse | null>(null);
  const [worldMap, setWorldMap] = useState<WorldDotMapData | null>(null);
  const [countries, setCountries] = useState<CountryEntry[]>([]);
  const [activeTab, setActiveTab] = useState<AppTab>('subscriptions');
  const [selectedCountryCode, setSelectedCountryCode] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshingAll, setRefreshingAll] = useState(false);
  const [refreshingSubIds, setRefreshingSubIds] = useState<Set<string>>(new Set());
  const [testingLatency, setTestingLatency] = useState(false);
  const [testingNodeIds, setTestingNodeIds] = useState<Set<string>>(new Set());
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [showWelcome, setShowWelcome] = useState<boolean>(() => {
    try {
      return localStorage.getItem(WELCOME_STORAGE_KEY) !== '1';
    } catch {
      return false;
    }
  });
  const [systemDark, setSystemDark] = useState<boolean>(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  });

  const autoRefreshedRef = useRef(false);
  const toastTimersRef = useRef<Map<string, number>>(new Map());

  const dismissToast = useCallback((id: string) => {
    const timer = toastTimersRef.current.get(id);
    if (timer) {
      window.clearTimeout(timer);
      toastTimersRef.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    (text: string, tone: ToastTone = 'neutral', durationMs = 3200): string => {
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      setToasts((prev) => [...prev.slice(-3), { id, text, tone }]);
      if (durationMs > 0) {
        const timer = window.setTimeout(() => {
          dismissToast(id);
        }, durationMs);
        toastTimersRef.current.set(id, timer);
      }
      return id;
    },
    [dismissToast]
  );

  const dismissWelcomePermanently = useCallback(() => {
    try {
      localStorage.setItem(WELCOME_STORAGE_KEY, '1');
    } catch {
      // ignore storage errors
    }
    setShowWelcome(false);
  }, []);

  const reloadState = useCallback(async (): Promise<AppStateResponse | null> => {
    try {
      const next = await api.getState();
      setState(next);
      setLoadError(null);
      return next;
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : '无法连接到塔台本地服务';
      setLoadError(msg);
      return null;
    }
  }, []);

  const applyStateFromMutation = useCallback(
    async (maybeState?: AppStateResponse): Promise<AppStateResponse | null> => {
      if (maybeState) {
        setState(maybeState);
        return maybeState;
      }
      return reloadState();
    },
    [reloadState]
  );

  useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      setLoading(true);
      try {
        const [appState, mapData, countryList] = await Promise.all([
          api.getState(),
          api.getWorldMap().catch(() => null),
          api.getCountries().catch(() => []),
        ]);
        if (cancelled) return;
        setState(appState);
        if (mapData) setWorldMap(mapData);
        setCountries(countryList);
        setLoadError(null);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : '加载塔台数据失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, []);

  // Listen to OS dark mode
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  const themePreference: ThemePreference = state?.snapshot.settings.theme ?? DEFAULT_APP_SETTINGS.theme;
  const resolvedDark =
    themePreference === 'dark' || (themePreference === 'system' && systemDark);

  useEffect(() => {
    const root = document.documentElement;
    if (resolvedDark) root.classList.add('dark');
    else root.classList.remove('dark');
  }, [resolvedDark]);

  const refreshAllSubscriptions = useCallback(async () => {
    if (refreshingAll) return;
    setRefreshingAll(true);
    const toastId = `refresh-all-${Date.now()}`;
    setToasts((prev) => [
      ...prev.filter((t) => !t.id.startsWith('refresh-all-')),
      { id: toastId, text: '正在刷新全部已启用订阅…', tone: 'neutral', spinning: true },
    ]);
    try {
      const res = await api.refreshAllSubscriptions();
      await applyStateFromMutation(res.state);
      dismissToast(toastId);
      const failures = res.results?.filter((r) => r.error) ?? [];
      if (failures.length > 0) {
        showToast(
          `刷新完成，${failures.length} 个订阅失败：${failures[0]?.name || failures[0]?.error}`,
          'warning',
          4500
        );
      } else {
        showToast('全部订阅已更新', 'success');
      }
    } catch (err) {
      dismissToast(toastId);
      showToast(err instanceof Error ? err.message : '刷新订阅失败', 'danger', 4500);
    } finally {
      setRefreshingAll(false);
    }
  }, [applyStateFromMutation, dismissToast, refreshingAll, showToast]);

  // Auto refresh on open if enabled
  useEffect(() => {
    if (
      state &&
      !autoRefreshedRef.current &&
      state.snapshot.settings.autoRefreshOnOpen &&
      state.snapshot.subscriptions.some((s) => s.isEnabled)
    ) {
      autoRefreshedRef.current = true;
      void refreshAllSubscriptions();
    }
  }, [refreshAllSubscriptions, state]);

  const refreshSingleSubscription = useCallback(
    async (id: string, name: string) => {
      setRefreshingSubIds((prev) => {
        const next = new Set(prev);
        next.add(id);
        return next;
      });
      try {
        const res = await api.refreshSubscription(id);
        await applyStateFromMutation(res.state);
        showToast(`「${name}」已刷新（${res.addedNodeCount} 个节点）`, 'success');
      } catch (err) {
        showToast(
          err instanceof Error ? `刷新「${name}」失败：${err.message}` : `刷新「${name}」失败`,
          'danger',
          4500
        );
      } finally {
        setRefreshingSubIds((prev) => {
          const next = new Set(prev);
          next.delete(id);
          return next;
        });
      }
    },
    [applyStateFromMutation, showToast]
  );

  const testLatency = useCallback(
    async (nodeIds?: string[], mode?: NodeLatencyTestMode) => {
      if (nodeIds && nodeIds.length > 0) {
        setTestingNodeIds((prev) => {
          const next = new Set(prev);
          nodeIds.forEach((id) => next.add(id));
          return next;
        });
      } else {
        setTestingLatency(true);
      }
      try {
        const effectiveMode = mode ?? state?.snapshot.settings.latencyTestMode ?? 'automatic';
        const res = await api.testNodeLatency({ nodeIds, mode: effectiveMode });
        setState((prev) => {
          if (!prev) return prev;
          const nextRuntime = { ...prev.runtime };
          for (const [nodeId, measurement] of Object.entries(res.results)) {
            const existing = nextRuntime[nodeId];
            if (existing) {
              nextRuntime[nodeId] = { ...existing, latency: measurement };
            } else {
              nextRuntime[nodeId] = {
                nodeId,
                countryCode: null,
                countryName: null,
                countryEnglishName: null,
                flagEmoji: null,
                latitude: null,
                longitude: null,
                regionSource: 'unknown',
                latency: measurement,
              };
            }
          }
          return { ...prev, runtime: nextRuntime };
        });
        if (!nodeIds || nodeIds.length > 1) {
          const count = Object.keys(res.results).length;
          showToast(`已完成 ${count} 个节点测速`, 'success');
        }
      } catch (err) {
        showToast(err instanceof Error ? err.message : '测速失败', 'danger');
      } finally {
        if (nodeIds && nodeIds.length > 0) {
          setTestingNodeIds((prev) => {
            const next = new Set(prev);
            nodeIds.forEach((id) => next.delete(id));
            return next;
          });
        } else {
          setTestingLatency(false);
        }
      }
    },
    [showToast, state?.snapshot.settings.latencyTestMode]
  );

  const updateSettings = useCallback(
    async (patch: Partial<AppSettings>) => {
      try {
        const res = await api.updateSettings(patch);
        if (res.state) {
          setState(res.state);
        } else if (res.settings) {
          setState((prev) =>
            prev ? { ...prev, snapshot: { ...prev.snapshot, settings: res.settings! } } : prev
          );
        } else {
          await reloadState();
        }
      } catch (err) {
        showToast(err instanceof Error ? err.message : '保存设置失败', 'danger');
        throw err;
      }
    },
    [reloadState, showToast]
  );

  const updateTheme = useCallback(
    async (theme: ThemePreference) => {
      // Optimistically update theme locally
      setState((prev) =>
        prev
          ? {
              ...prev,
              snapshot: {
                ...prev.snapshot,
                settings: { ...prev.snapshot.settings, theme },
              },
            }
          : prev
      );
      try {
        await updateSettings({ theme });
      } catch {
        // already toasted
      }
    },
    [updateSettings]
  );

  const value = useMemo<TowerContextValue>(
    () => ({
      state,
      worldMap,
      countries,
      activeTab,
      setActiveTab,
      selectedCountryCode,
      setSelectedCountryCode,
      loading,
      loadError,
      refreshingAll,
      refreshingSubIds,
      testingLatency,
      testingNodeIds,
      toasts,
      showToast,
      dismissToast,
      showWelcome,
      setShowWelcome,
      dismissWelcomePermanently,
      reloadState,
      applyStateFromMutation,
      refreshAllSubscriptions,
      refreshSingleSubscription,
      testLatency,
      updateTheme,
      updateSettings,
      resolvedDark,
    }),
    [
      activeTab,
      applyStateFromMutation,
      countries,
      dismissToast,
      dismissWelcomePermanently,
      loadError,
      loading,
      refreshAllSubscriptions,
      refreshSingleSubscription,
      refreshingAll,
      refreshingSubIds,
      reloadState,
      resolvedDark,
      selectedCountryCode,
      showToast,
      showWelcome,
      state,
      testLatency,
      testingLatency,
      testingNodeIds,
      toasts,
      updateSettings,
      updateTheme,
      worldMap,
    ]
  );

  return <TowerContext.Provider value={value}>{children}</TowerContext.Provider>;
}

export function useTower(): TowerContextValue {
  const ctx = useContext(TowerContext);
  if (!ctx) throw new Error('useTower must be used within TowerProvider');
  return ctx;
}
