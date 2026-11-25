import { useEffect, useMemo, useState } from 'react';
import QRCode from 'qrcode';
import { Copy, RefreshCw, Wifi } from 'lucide-react';
import { api } from '@/api/client';
import { ClientIcon } from '@/components/ClientIcon';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { useTower } from '@/state/TowerContext';
import {
  LAN_FORMAT_OPTIONS,
  type ExportContentMode,
  type LANSubscriptionFormat,
} from '@shared/index';

export function LanSharingPanel() {
  const { state, reloadState, showToast } = useTower();
  const lan = state?.lanStatus;

  const [format, setFormat] = useState<LANSubscriptionFormat>('auto');
  const [contentMode, setContentMode] = useState<ExportContentMode>('fullConfiguration');
  const [selectedBaseUrl, setSelectedBaseUrl] = useState<string>('');
  const [qrDataUrl, setQrDataUrl] = useState<string>('');
  const [toggling, setToggling] = useState(false);
  const [rotating, setRotating] = useState(false);

  useEffect(() => {
    if (lan?.primaryURL && !selectedBaseUrl) {
      setSelectedBaseUrl(lan.primaryURL);
    } else if (lan && lan.urls.length > 0 && !lan.urls.includes(selectedBaseUrl)) {
      setSelectedBaseUrl(lan.primaryURL || lan.urls[0]!);
    }
  }, [lan, selectedBaseUrl]);

  const subscriptionUrl = useMemo(() => {
    if (!lan) return '';
    const base = selectedBaseUrl || lan.primaryURL || lan.urls[0] || '';
    if (!base) return '';
    try {
      const url = new URL(base);
      if (format !== 'auto') url.searchParams.set('target', format);
      if (contentMode !== 'fullConfiguration') url.searchParams.set('content', contentMode);
      return url.toString();
    } catch {
      const params = new URLSearchParams();
      if (format !== 'auto') params.set('target', format);
      if (contentMode !== 'fullConfiguration') params.set('content', contentMode);
      const q = params.toString();
      return q ? `${base}${base.includes('?') ? '&' : '?'}${q}` : base;
    }
  }, [contentMode, format, lan, selectedBaseUrl]);

  // Render QR cleanly with `qrcode` library (instant & crisp on client, works offline)
  useEffect(() => {
    if (!lan?.enabled || !subscriptionUrl) {
      setQrDataUrl('');
      return;
    }
    let cancelled = false;
    QRCode.toDataURL(subscriptionUrl, {
      width: 240,
      margin: 2,
      color: { dark: '#111827', light: '#FFFFFF' },
    })
      .then((dataUrl) => {
        if (!cancelled) setQrDataUrl(dataUrl);
      })
      .catch(async () => {
        try {
          const res = await api.getLanQr({
            format,
            content: contentMode,
            baseUrl: selectedBaseUrl || undefined,
          });
          if (!cancelled) setQrDataUrl(res.qrDataUrl);
        } catch {
          if (!cancelled) setQrDataUrl('');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [contentMode, format, lan?.enabled, selectedBaseUrl, subscriptionUrl]);

  const handleToggle = async () => {
    if (!lan) return;
    setToggling(true);
    try {
      await api.toggleLan(!lan.enabled);
      await reloadState();
      showToast(!lan.enabled ? '已开启局域网订阅共享' : '已关闭局域网订阅共享', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '切换失败', 'danger');
    } finally {
      setToggling(false);
    }
  };

  const handleRotateToken = async () => {
    setRotating(true);
    try {
      await api.rotateLanToken();
      await reloadState();
      showToast('已重置随机访问 Token，旧订阅链接已失效', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '轮换 Token 失败', 'danger');
    } finally {
      setRotating(false);
    }
  };

  const selectedOption = LAN_FORMAT_OPTIONS.find((f) => f.id === format);

  return (
    <div className="tower-card p-5 space-y-5">
      {/* Enable Switch Header */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-[13px] bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] flex items-center justify-center shrink-0">
            <Wifi className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-[var(--tower-text)]">
              局域网 / Web 订阅共享
            </h2>
            <p className="text-xs text-[var(--tower-text-secondary)]">
              由本机 `/sub/:token` 提供订阅端点，同局域网设备可直接扫码拉取配置
            </p>
          </div>
        </div>

        <SelectionIndicator
          selected={Boolean(lan?.enabled)}
          onToggle={() => void handleToggle()}
          disabled={toggling}
          label="开启局域网订阅共享"
        />
      </div>

      {!lan?.enabled ? (
        <div className="p-6 rounded-[16px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-center space-y-2">
          <p className="text-sm font-medium text-[var(--tower-text)]">局域网订阅服务未开启</p>
          <p className="text-xs text-[var(--tower-text-secondary)]">
            开启后将生成带随机访问 Token 的本地订阅链接与二维码，支持自动识别客户端 User-Agent。
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-5 items-start">
          <div className="space-y-4 min-w-0">
            {/* Format & Content Mode Selectors */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
                  订阅格式
                </label>
                <div className="flex items-center gap-2">
                  {selectedOption?.iconFile && (
                    <ClientIcon
                      iconFile={selectedOption.iconFile}
                      name={selectedOption.label}
                      className="w-8 h-8"
                    />
                  )}
                  <select
                    value={format}
                    onChange={(e) => setFormat(e.target.value as LANSubscriptionFormat)}
                    className="flex-1 px-3 py-2 text-sm tower-input"
                  >
                    {LAN_FORMAT_OPTIONS.map((opt) => (
                      <option key={opt.id} value={opt.id}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>
                {selectedOption && (
                  <p className="mt-1 text-[11px] text-[var(--tower-text-tertiary)]">
                    {selectedOption.subtitle}
                  </p>
                )}
              </div>

              <div>
                <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
                  输出内容
                </label>
                <div className="flex p-1 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]">
                  <button
                    type="button"
                    onClick={() => setContentMode('fullConfiguration')}
                    className={`flex-1 py-1.5 rounded-[9px] text-xs font-semibold cursor-pointer ${
                      contentMode === 'fullConfiguration'
                        ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                        : 'text-[var(--tower-text-secondary)]'
                    }`}
                  >
                    完整配置
                  </button>
                  <button
                    type="button"
                    onClick={() => setContentMode('nodesOnly')}
                    className={`flex-1 py-1.5 rounded-[9px] text-xs font-semibold cursor-pointer ${
                      contentMode === 'nodesOnly'
                        ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                        : 'text-[var(--tower-text-secondary)]'
                    }`}
                  >
                    仅节点
                  </button>
                </div>
              </div>
            </div>

            {/* Available Base URLs */}
            {lan.urls.length > 1 && (
              <div>
                <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
                  选择网卡 / 地址
                </label>
                <div className="flex flex-wrap gap-1.5">
                  {lan.urls.map((u) => (
                    <button
                      key={u}
                      type="button"
                      onClick={() => setSelectedBaseUrl(u)}
                      className={`px-2.5 py-1 rounded-full text-xs font-mono transition-colors cursor-pointer ${
                        selectedBaseUrl === u
                          ? 'bg-[var(--tower-accent)] text-white'
                          : 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
                      }`}
                    >
                      {new URL(u).host}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Final Subscription URL */}
            <div>
              <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
                订阅地址
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  readOnly
                  value={subscriptionUrl}
                  className="flex-1 px-3 py-2 text-xs tower-input font-mono select-all"
                />
                <button
                  type="button"
                  onClick={() => {
                    void navigator.clipboard.writeText(subscriptionUrl);
                    showToast('已复制局域网订阅地址', 'success');
                  }}
                  className="shrink-0 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-xs font-semibold hover:opacity-92 cursor-pointer"
                >
                  <Copy className="w-3.5 h-3.5" />
                  <span>复制</span>
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between pt-1">
              <span className="text-[11px] text-[var(--tower-text-tertiary)] font-mono">
                Token: {lan.token.slice(0, 8)}••••
              </span>
              <button
                type="button"
                onClick={() => void handleRotateToken()}
                disabled={rotating}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[10px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-xs font-medium text-[var(--tower-text)] hover:bg-[var(--tower-border)] disabled:opacity-50 cursor-pointer"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${rotating ? 'animate-spin' : ''}`} />
                <span>轮换访问 Token</span>
              </button>
            </div>
          </div>

          {/* QR Code Card */}
          <div className="flex flex-col items-center p-3.5 rounded-[18px] bg-white border border-[var(--tower-border)] shadow-xs mx-auto">
            {qrDataUrl ? (
              <img src={qrDataUrl} alt="订阅二维码" className="w-44 h-44 object-contain" />
            ) : (
              <div className="w-44 h-44 flex items-center justify-center text-xs text-gray-400">
                生成二维码中…
              </div>
            )}
            <span className="mt-2 text-[11px] font-medium text-gray-500">
              使用客户端扫码订阅
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
