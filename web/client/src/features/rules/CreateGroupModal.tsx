import { useState } from 'react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { useTower } from '@/state/TowerContext';
import type { RuleSchemeGroupKind } from '@shared/index';

interface CreateGroupModalProps {
  open: boolean;
  schemeId: string;
  onClose: () => void;
}

export function CreateGroupModal({ open, schemeId, onClose }: CreateGroupModalProps) {
  const { applyStateFromMutation, showToast } = useTower();
  const [name, setName] = useState('');
  const [kind, setKind] = useState<RuleSchemeGroupKind>('url-test');
  const [pattern, setPattern] = useState('');
  const [saving, setSaving] = useState(false);

  const handleCreate = async () => {
    if (!name.trim()) {
      showToast('请输入分组名称', 'warning');
      return;
    }
    setSaving(true);
    try {
      const res = await api.createRuleGroup(schemeId, {
        name: name.trim(),
        kind,
        isCustomNodeFilter: true,
        members: pattern.trim() ? [{ type: 'nodePattern', pattern: pattern.trim() }] : [],
      });
      await applyStateFromMutation(res.state);
      setName('');
      setPattern('');
      onClose();
      showToast(`已创建节点过滤分组「${name.trim()}」`, 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '创建分组失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="新建节点过滤分组"
      subtitle="按正则匹配节点生成自定义地区或专用策略组"
      maxWidthClass="max-w-md"
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
            onClick={() => void handleCreate()}
            disabled={saving || !name.trim()}
            className="px-5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 disabled:opacity-50 cursor-pointer"
          >
            {saving ? '创建中…' : '创建分组'}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
            分组名称
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="例如：🇸🇬 新加坡专线"
            className="w-full px-3.5 py-2 text-sm tower-input"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
            策略类型
          </label>
          <select
            value={kind}
            onChange={(e) => setKind(e.target.value as RuleSchemeGroupKind)}
            className="w-full px-3.5 py-2 text-sm tower-input"
          >
            <option value="url-test">自动测速 (url-test)</option>
            <option value="select">手动选择 (select)</option>
            <option value="fallback">故障转移 (fallback)</option>
            <option value="load-balance">负载均衡 (load-balance)</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
            节点匹配正则
          </label>
          <input
            type="text"
            value={pattern}
            onChange={(e) => setPattern(e.target.value)}
            placeholder="例如：(新加坡|狮城|SG|Singapore)"
            className="w-full px-3.5 py-2 text-sm tower-input font-mono"
          />
        </div>
      </div>
    </Modal>
  );
}
