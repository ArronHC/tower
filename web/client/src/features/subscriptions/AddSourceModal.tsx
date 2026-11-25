import { useEffect, useState } from 'react';
import { Clipboard, Link2, Loader2, Server, Sparkles } from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { useTower } from '@/state/TowerContext';
import type { SourceDetectionResult } from '@shared/index';

interface AddSourceModalProps {
  open: boolean;
  onClose: () => void;
}

const KIND_LABELS: Record<SourceDetectionResult['kind'], string> = {
  subscriptionURL: '订阅链接',
  proxyNodes: '节点分享链接',
  clashYAML: 'Clash YAML 配置',
  surgeConfig: 'Surge 配置',
  ruleScheme: '规则方案',
  unknown: '待识别文本',
};

export function AddSourceModal({ open, onClose }: AddSourceModalProps) {
  const { applyStateFromMutation, showToast, setActiveTab } = useTower();
  const [input, setInput] = useState('');
  const [customName, setCustomName] = useState('');
  const [userAgent, setUserAgent] = useState('');
  const [dohUrl, setDohUrl] = useState('');
  const [detection, setDetection] = useState<SourceDetectionResult | null>(null);
  const [detecting, setDetecting] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Reset form when closing
  useEffect(() => {
    if (!open) {
      setInput('');
      setCustomName('');
      setUserAgent('');
      setDohUrl('');
      setDetection(null);
      setDetecting(false);
    }
  }, [open]);

  // Debounced smart source detection
  useEffect(() => {
    const trimmed = input.trim();
    if (!open || !trimmed) {
      setDetection(null);
      setDetecting(false);
      return;
    }

    let cancelled = false;
    setDetecting(true);
    const timer = window.setTimeout(async () => {
      try {
        const res = await api.detectSource(trimmed);
        if (!cancelled) setDetection(res);
      } catch {
        if (!cancelled) setDetection(null);
      } finally {
        if (!cancelled) setDetecting(false);
      }
    }, 320);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [input, open]);

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        setInput(text);
      } else {
        showToast('剪贴板为空', 'neutral');
      }
    } catch {
      showToast('无法读取剪贴板，请手动粘贴 (Ctrl/Cmd+V)', 'warning');
    }
  };

  const isUrlSource =
    detection?.kind === 'subscriptionURL' || /^https?:\/\/\S+$/i.test(input.trim());

  const handleSubmit = async () => {
    const trimmed = input.trim();
    if (!trimmed) return;

    setSubmitting(true);
    try {
      if (detection?.kind === 'ruleScheme') {
        const res = await api.importRuleScheme(
          /^https?:\/\/\S+$/i.test(trimmed)
            ? { name: customName.trim() || undefined, url: trimmed }
            : { name: customName.trim() || undefined, content: trimmed }
        );
        await applyStateFromMutation(res.state);
        onClose();
        setActiveTab('rules');
        showToast('已识别并导入为规则方案', 'success');
        return;
      }

      if (isUrlSource) {
        const res = await api.addSubscription({
          name: customName.trim() || undefined,
          urlString: detection?.normalizedURL || trimmed,
          requestOptions:
            userAgent.trim() || dohUrl.trim()
              ? {
                  userAgent: userAgent.trim() || null,
                  dnsOverHTTPSURL: dohUrl.trim() || null,
                }
              : null,
        });
        await applyStateFromMutation(res.state);
        onClose();
        const skipNote = res.skippedCount > 0 ? `，已过滤 ${res.skippedCount} 个不支持节点` : '';
        showToast(
          `已添加「${res.subscription.name}」（${res.addedNodeCount} 个节点${skipNote}）`,
          'success'
        );
      } else {
        const res = await api.importNodes(trimmed);
        await applyStateFromMutation(res.state);
        onClose();
        const skipNote = res.skippedCount > 0 ? `，跳过 ${res.skippedCount} 个` : '';
        showToast(`已导入 ${res.addedNodes.length} 个自有节点${skipNote}`, 'success');
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : '导入失败', 'danger', 4500);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="添加订阅或自有节点"
      subtitle="自动识别订阅链接、节点 URI、Clash YAML 与 Surge 配置"
      maxWidthClass="max-w-xl"
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
          >
            取消
          </button>
          <button
            type="button"
            onClick={() => void handleSubmit()}
            disabled={submitting || !input.trim()}
            className="inline-flex items-center gap-1.5 px-5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 disabled:opacity-45 cursor-pointer"
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
            <span>
              {submitting
                ? '正在处理…'
                : detection?.kind === 'ruleScheme'
                  ? '导入规则方案'
                  : isUrlSource
                    ? '拉取并添加订阅'
                    : '导入节点'}
            </span>
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-[var(--tower-text-secondary)]">
            来源内容
          </span>
          <button
            type="button"
            onClick={() => void handlePaste()}
            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] text-xs font-semibold hover:opacity-85 cursor-pointer"
          >
            <Clipboard className="w-3.5 h-3.5" />
            <span>从剪贴板粘贴</span>
          </button>
        </div>

        <textarea
          rows={5}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="粘贴 https:// 订阅链接、多行 ss:// / vless:// / hysteria2:// 节点链接，或 Clash / Surge 配置文本…"
          className="w-full p-3.5 text-sm tower-input font-mono resize-y"
        />

        {/* Smart Recognition Badge */}
        {(detecting || detection) && (
          <div className="flex items-center gap-2 px-3.5 py-2 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-xs">
            {detecting ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin text-[var(--tower-accent)] shrink-0" />
                <span className="text-[var(--tower-text-secondary)]">正在智能识别输入格式…</span>
              </>
            ) : detection ? (
              <>
                <Sparkles className="w-3.5 h-3.5 text-[var(--tower-accent)] shrink-0" />
                <span className="font-semibold text-[var(--tower-text)]">
                  {KIND_LABELS[detection.kind]}
                </span>
                <span className="text-[var(--tower-text-secondary)] truncate">
                  {detection.summary}
                  {detection.detectedNodeCount !== undefined
                    ? ` (${detection.detectedNodeCount} 个节点)`
                    : ''}
                </span>
              </>
            ) : null}
          </div>
        )}

        {/* Optional Subscription Options */}
        {isUrlSource && (
          <div className="space-y-3 pt-2 border-t border-[var(--tower-border)]">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-[var(--tower-text-secondary)]">
              <Link2 className="w-3.5 h-3.5" />
              <span>订阅请求选项（可选）</span>
            </div>
            <div>
              <label className="block text-xs text-[var(--tower-text-secondary)] mb-1">
                自定义名称
              </label>
              <input
                type="text"
                value={customName}
                onChange={(e) => setCustomName(e.target.value)}
                placeholder="留空则从响应头或域名自动提取"
                className="w-full px-3 py-2 text-sm tower-input"
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-[var(--tower-text-secondary)] mb-1">
                  自定义 User-Agent
                </label>
                <input
                  type="text"
                  value={userAgent}
                  onChange={(e) => setUserAgent(e.target.value)}
                  placeholder="可选"
                  className="w-full px-3 py-2 text-sm tower-input font-mono"
                />
              </div>
              <div>
                <label className="block text-xs text-[var(--tower-text-secondary)] mb-1">
                  DoH 解析地址
                </label>
                <input
                  type="url"
                  value={dohUrl}
                  onChange={(e) => setDohUrl(e.target.value)}
                  placeholder="https://..."
                  className="w-full px-3 py-2 text-sm tower-input font-mono"
                />
              </div>
            </div>
          </div>
        )}

        {!isUrlSource && input.trim().length > 0 && (
          <div className="flex items-center gap-2 text-xs text-[var(--tower-text-secondary)]">
            <Server className="w-3.5 h-3.5 shrink-0" />
            <span>自有节点将独立保存，并参与离线地区识别、测速与策略分组。</span>
          </div>
        )}
      </div>
    </Modal>
  );
}
