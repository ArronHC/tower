import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { useTower } from '@/state/TowerContext';

interface ImportSchemeModalProps {
  open: boolean;
  onClose: () => void;
}

export function ImportSchemeModal({ open, onClose }: ImportSchemeModalProps) {
  const { applyStateFromMutation, showToast } = useTower();
  const [name, setName] = useState('');
  const [mode, setMode] = useState<'url' | 'content'>('url');
  const [url, setUrl] = useState('');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleImport = async () => {
    if (mode === 'url' && !url.trim()) {
      showToast('请输入规则方案 HTTPS 地址', 'warning');
      return;
    }
    if (mode === 'content' && !content.trim()) {
      showToast('请粘贴规则方案文本', 'warning');
      return;
    }
    setSubmitting(true);
    try {
      const res = await api.importRuleScheme({
        name: name.trim() || undefined,
        url: mode === 'url' ? url.trim() : undefined,
        content: mode === 'content' ? content.trim() : undefined,
      });
      await applyStateFromMutation(res.state);
      setName('');
      setUrl('');
      setContent('');
      onClose();
      showToast(`已导入规则方案「${res.scheme?.name || name || '自定义方案'}」`, 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '导入规则方案失败', 'danger', 4500);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="导入规则方案"
      subtitle="支持 Subconverter INI、ACL4SSR、Clash YAML 与 Surge 规则配置"
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
            onClick={() => void handleImport()}
            disabled={submitting}
            className="inline-flex items-center gap-1.5 px-5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 disabled:opacity-50 cursor-pointer"
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
            <span>{submitting ? '导入中…' : '导入方案'}</span>
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex p-1 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]">
          <button
            type="button"
            onClick={() => setMode('url')}
            className={`flex-1 py-1.5 rounded-[9px] text-xs font-semibold transition-colors cursor-pointer ${
              mode === 'url'
                ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                : 'text-[var(--tower-text-secondary)]'
            }`}
          >
            从远程 URL 导入
          </button>
          <button
            type="button"
            onClick={() => setMode('content')}
            className={`flex-1 py-1.5 rounded-[9px] text-xs font-semibold transition-colors cursor-pointer ${
              mode === 'content'
                ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                : 'text-[var(--tower-text-secondary)]'
            }`}
          >
            粘贴配置文本
          </button>
        </div>

        <div>
          <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
            方案名称（可选）
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="留空则从文件名或配置自动提取"
            className="w-full px-3.5 py-2 text-sm tower-input"
          />
        </div>

        {mode === 'url' ? (
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              规则方案 URL（仅接受 HTTPS）
            </label>
            <input
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://raw.githubusercontent.com/..."
              className="w-full px-3.5 py-2.5 text-sm tower-input font-mono"
            />
          </div>
        ) : (
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              方案内容
            </label>
            <textarea
              rows={7}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="粘贴 INI / Clash YAML / Surge 配置…"
              className="w-full p-3.5 text-xs tower-input font-mono"
            />
          </div>
        )}
      </div>
    </Modal>
  );
}
