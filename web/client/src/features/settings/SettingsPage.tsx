import React, { useRef, useState } from 'react';
import {
  AlertTriangle,
  Download,
  HelpCircle,
  Lock,
  RotateCcw,
  ShieldCheck,
  Upload,
} from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { TailnetSettings } from '@/features/settings/TailnetSettings';
import { useTower } from '@/state/TowerContext';
import {
  DEFAULT_APP_SETTINGS,
  type AppSnapshot,
  type DNSProtectionMode,
  type NodeLatencyTestMode,
  type ThemePreference,
} from '@shared/index';

export function SettingsPage() {
  const {
    state,
    updateSettings,
    applyStateFromMutation,
    setShowWelcome,
    showToast,
  } = useTower();

  const settings = state?.snapshot.settings ?? DEFAULT_APP_SETTINGS;
  const [configName, setConfigName] = useState(settings.configurationName);
  const [resetConfirmOpen, setResetConfirmOpen] = useState(false);
  const [resetting, setResetting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleToggle = (key: keyof typeof settings, value: boolean) => {
    void updateSettings({ [key]: value });
  };

  const handleSaveConfigName = () => {
    const trimmed = configName.trim() || 'Tower';
    setConfigName(trimmed);
    if (trimmed !== settings.configurationName) {
      void updateSettings({ configurationName: trimmed }).then(() => {
        showToast('已保存配置名称', 'success');
      });
    }
  };

  const handleExportSnapshot = async () => {
    try {
      const snapshot = await api.exportSnapshot();
      const blob = new Blob([JSON.stringify(snapshot, null, 2)], {
        type: 'application/json',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `tower-backup-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      showToast('已导出不含密钥的本地备份快照', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '导出备份失败', 'danger');
    }
  };

  const handleImportFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      const text = await file.text();
      const parsed = JSON.parse(text) as AppSnapshot;
      const res = await api.importSnapshot(parsed);
      await applyStateFromMutation(res.state);
      showToast('已从备份文件恢复塔台数据', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '备份文件格式无效或导入失败', 'danger');
    }
  };

  const handleResetAll = async () => {
    setResetting(true);
    try {
      const res = await api.resetSnapshot();
      await applyStateFromMutation(res.state);
      setResetConfirmOpen(false);
      showToast('已重置全部本地数据', 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '重置失败', 'danger');
    } finally {
      setResetting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Privacy Status Card */}
      <section className="tower-card p-5 flex items-start gap-4 bg-gradient-to-br from-emerald-500/[0.06] to-transparent">
        <div className="w-11 h-11 rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0">
          <ShieldCheck className="w-5.5 h-5.5" />
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="text-base font-semibold text-[var(--tower-text)]">
              全部在本机处理
            </h1>
            <Lock className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
          </div>
          <p className="text-xs text-[var(--tower-text-secondary)] leading-relaxed">
            全部在本机处理，不上传第三方转换或 IP 查询服务。所有订阅链接、节点配置、内置 IP/ASN 国家库与导出结果仅在您的本地 Node.js 服务中运行，Tailscale Auth Key 独立存放在受限文件内。
          </p>
        </div>
      </section>

      {/* General & Network Settings */}
      <section className="tower-card p-5 space-y-5">
        <h2 className="text-base font-semibold text-[var(--tower-text)]">常规与导出设置</h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {/* Theme */}
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              界面外观
            </label>
            <div className="flex p-1 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]">
              {(
                [
                  { value: 'system', label: '跟随系统' },
                  { value: 'light', label: '浅色' },
                  { value: 'dark', label: '深色' },
                ] as Array<{ value: ThemePreference; label: string }>
              ).map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => void updateSettings({ theme: opt.value })}
                  className={`flex-1 py-1.5 rounded-[9px] text-xs font-semibold transition-colors cursor-pointer ${
                    settings.theme === opt.value
                      ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                      : 'text-[var(--tower-text-secondary)]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Configuration Name */}
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              导出配置名称
            </label>
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={configName}
                onChange={(e) => setConfigName(e.target.value)}
                onBlur={handleSaveConfigName}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleSaveConfigName();
                }}
                className="flex-1 px-3.5 py-2 text-sm tower-input"
                placeholder="Tower"
              />
            </div>
          </div>

          {/* DNS Protection Mode */}
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              DNS 防泄漏保护模式
            </label>
            <select
              value={settings.dnsProtectionMode}
              onChange={(e) =>
                void updateSettings({ dnsProtectionMode: e.target.value as DNSProtectionMode })
              }
              className="w-full px-3.5 py-2 text-sm tower-input"
            >
              <option value="standard">标准保护（推荐，兼顾国内直连解析）</option>
              <option value="strict">严格保护（避免未命中域名泄露至本地 DNS）</option>
              <option value="followScheme">跟随规则方案原配置</option>
            </select>
          </div>

          {/* Latency Test Mode */}
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              节点测速方式
            </label>
            <select
              value={settings.latencyTestMode}
              onChange={(e) =>
                void updateSettings({ latencyTestMode: e.target.value as NodeLatencyTestMode })
              }
              className="w-full px-3.5 py-2 text-sm tower-input"
            >
              <option value="automatic">自动选择（优先 ICMP，回退 TCP）</option>
              <option value="icmp">仅 ICMP (Ping)</option>
              <option value="tcp">仅 TCP 握手探测</option>
            </select>
          </div>
        </div>

        {/* Toggle Rows */}
        <div className="divide-y divide-[var(--tower-border)] rounded-[14px] border border-[var(--tower-border)]">
          {[
            {
              key: 'appendSubscriptionNameToNodes' as const,
              title: '节点名称附加订阅来源',
              desc: '在导出与展示时为同名机场节点添加订阅名称前缀，避免重名冲突',
            },
            {
              key: 'filterSubscriptionInfoNodes' as const,
              title: '自动过滤流量/到期提示伪节点',
              desc: '拉取订阅时识别剩余流量、过期时间等提示行并转为配额信息',
            },
            {
              key: 'autoRefreshOnOpen' as const,
              title: '打开页面时自动刷新已启用订阅',
              desc: '仅刷新启用的订阅源，保留您的排除与手动地区设置',
            },
            {
              key: 'preferRuleSets' as const,
              title: '优先使用远程规则集引用',
              desc: '在支持的客户端中以 RULE-SET / rule-providers 引用规则，减小配置文件体积',
            },
            {
              key: 'embedRemoteSubscriptionLinks' as const,
              title: '在配置中内嵌远端订阅链接',
              desc: '允许支持 proxy-provider / server-remote 的客户端直接引用原始订阅地址',
            },
          ].map((item) => (
            <div key={item.key} className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="min-w-0">
                <div className="text-sm font-medium text-[var(--tower-text)]">{item.title}</div>
                <div className="text-xs text-[var(--tower-text-secondary)]">{item.desc}</div>
              </div>
              <SelectionIndicator
                selected={Boolean(settings[item.key])}
                onToggle={() => handleToggle(item.key, !settings[item.key])}
                label={item.title}
              />
            </div>
          ))}
        </div>
      </section>

      {/* Tailscale Management */}
      <TailnetSettings />

      {/* Backup, Onboarding & Danger Zone */}
      <section className="tower-card p-5 space-y-4">
        <h2 className="text-base font-semibold text-[var(--tower-text)]">备份、引导与数据管理</h2>

        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={() => void handleExportSnapshot()}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-[13px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-sm font-medium text-[var(--tower-text)] hover:bg-[var(--tower-border)] cursor-pointer"
          >
            <Download className="w-4 h-4 text-[var(--tower-accent)]" />
            <span>导出备份 JSON</span>
          </button>

          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-[13px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-sm font-medium text-[var(--tower-text)] hover:bg-[var(--tower-border)] cursor-pointer"
          >
            <Upload className="w-4 h-4 text-emerald-500" />
            <span>导入备份 JSON</span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json,.json"
            onChange={(e) => void handleImportFile(e)}
            className="hidden"
          />

          <button
            type="button"
            onClick={() => setShowWelcome(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-[13px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-sm font-medium text-[var(--tower-text)] hover:bg-[var(--tower-border)] cursor-pointer"
          >
            <HelpCircle className="w-4 h-4 text-indigo-500" />
            <span>重新查看欢迎引导</span>
          </button>

          <button
            type="button"
            onClick={() => setResetConfirmOpen(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-[13px] bg-red-500/10 border border-red-500/20 text-sm font-semibold text-red-600 dark:text-red-400 hover:bg-red-500/15 cursor-pointer ml-auto"
          >
            <RotateCcw className="w-4 h-4" />
            <span>重置全部本地数据</span>
          </button>
        </div>
      </section>

      {/* Reset Confirmation Modal */}
      <Modal
        open={resetConfirmOpen}
        onClose={() => setResetConfirmOpen(false)}
        title="确认重置全部本地数据"
        maxWidthClass="max-w-sm"
        footer={
          <>
            <button
              type="button"
              onClick={() => setResetConfirmOpen(false)}
              className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] cursor-pointer"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void handleResetAll()}
              disabled={resetting}
              className="px-4 py-2 rounded-[12px] bg-red-500 text-white text-sm font-semibold hover:bg-red-600 cursor-pointer"
            >
              {resetting ? '重置中…' : '确认重置'}
            </button>
          </>
        }
      >
        <div className="flex items-start gap-2.5 text-sm text-[var(--tower-text-secondary)]">
          <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
          <p className="leading-relaxed">
            此操作将永久清除所有订阅、节点、导入的规则方案、自定义规则与 Tailscale 密钥，恢复为初始状态。建议先导出备份。
          </p>
        </div>
      </Modal>
    </div>
  );
}
