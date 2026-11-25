import { useState } from 'react';
import { Edit3, KeyRound, Network, Plus, ShieldAlert, Trash2 } from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { useTower } from '@/state/TowerContext';
import type { TailnetConnection } from '@shared/index';

export function TailnetSettings() {
  const { state, applyStateFromMutation, showToast } = useTower();
  const tailnets = state?.snapshot.tailnets ?? [];

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<TailnetConnection | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<TailnetConnection | null>(null);
  const [saving, setSaving] = useState(false);

  // Form fields
  const [name, setName] = useState('');
  const [controlURL, setControlURL] = useState('');
  const [deviceName, setDeviceName] = useState('');
  const [subnetsText, setSubnetsText] = useState('');
  const [magicDNS, setMagicDNS] = useState('');
  const [authKeyInput, setAuthKeyInput] = useState('');
  const [clearStoredKey, setClearStoredKey] = useState(false);

  const openNew = () => {
    setEditing(null);
    setName('');
    setControlURL('');
    setDeviceName('');
    setSubnetsText('');
    setMagicDNS('');
    setAuthKeyInput('');
    setClearStoredKey(false);
    setModalOpen(true);
  };

  const openEdit = (conn: TailnetConnection) => {
    setEditing(conn);
    setName(conn.name);
    setControlURL(conn.controlURLString ?? '');
    setDeviceName(conn.deviceName ?? '');
    setSubnetsText(conn.subnets.join(', '));
    setMagicDNS(conn.magicDNSSuffix ?? '');
    setAuthKeyInput('');
    setClearStoredKey(false);
    setModalOpen(true);
  };

  const toggleEnabled = async (conn: TailnetConnection) => {
    try {
      const res = await api.updateTailnet(conn.id, { isEnabled: !conn.isEnabled });
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '更新 Tailscale 状态失败', 'danger');
    }
  };

  const handleSave = async () => {
    if (!name.trim()) {
      showToast('请输入内网连接名称', 'warning');
      return;
    }
    const subnets = subnetsText
      .split(/[,，\n]/)
      .map((s) => s.trim())
      .filter(Boolean);

    setSaving(true);
    try {
      if (editing) {
        const authKeyPatch = clearStoredKey
          ? null
          : authKeyInput.trim()
            ? authKeyInput.trim()
            : undefined;
        const res = await api.updateTailnet(editing.id, {
          name: name.trim(),
          controlURLString: controlURL.trim() || null,
          deviceName: deviceName.trim() || null,
          subnets,
          magicDNSSuffix: magicDNS.trim() || null,
          ...(authKeyPatch !== undefined ? { authKey: authKeyPatch } : {}),
        });
        await applyStateFromMutation(res.state);
        showToast(`已更新 Tailscale 内网「${name.trim()}」`, 'success');
      } else {
        const res = await api.createTailnet({
          name: name.trim(),
          controlURLString: controlURL.trim() || null,
          deviceName: deviceName.trim() || null,
          subnets,
          magicDNSSuffix: magicDNS.trim() || null,
          isEnabled: true,
          ...(authKeyInput.trim() ? { authKey: authKeyInput.trim() } : {}),
        });
        await applyStateFromMutation(res.state);
        showToast(`已添加 Tailscale 内网「${name.trim()}」`, 'success');
      }
      setModalOpen(false);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!confirmDelete) return;
    setSaving(true);
    try {
      const res = await api.deleteTailnet(confirmDelete.id);
      await applyStateFromMutation(res.state);
      setConfirmDelete(null);
      showToast(`已删除 Tailscale 内网「${confirmDelete.name}」`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="tower-card p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-[11px] bg-indigo-500/12 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0">
            <Network className="w-4.5 h-4.5" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-[var(--tower-text)]">
              Tailscale 内网管理
            </h2>
            <p className="text-xs text-[var(--tower-text-secondary)]">
              为 Surge / Stash / mihomo / sing-box 生成置顶内网路由与 MagicDNS 规则
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={openNew}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-xs font-semibold hover:opacity-92 cursor-pointer"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>添加内网</span>
        </button>
      </div>

      <div className="flex items-start gap-2.5 p-3 rounded-[13px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-xs text-[var(--tower-text-secondary)] leading-relaxed">
        <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5 text-[var(--tower-accent)]" />
        <span>
          Tailscale 内网不会进入任何策略组或地区组；Auth Key 单独保存在服务端受限密钥文件中，不随备份导出，预览时自动打码。Surge 使用交互式登录（<code className="font-mono">interactive-login</code>），不写入 Auth Key。
        </span>
      </div>

      {tailnets.length === 0 ? (
        <div className="py-6 text-center text-xs text-[var(--tower-text-secondary)]">
          尚未配置 Tailscale / Headscale 内网连接
        </div>
      ) : (
        <div className="divide-y divide-[var(--tower-border)] rounded-[14px] border border-[var(--tower-border)]">
          {tailnets.map((conn) => (
            <div
              key={conn.id}
              className={`flex items-center gap-3 px-4 py-3 ${
                conn.isEnabled ? '' : 'opacity-60'
              }`}
            >
              <SelectionIndicator
                selected={conn.isEnabled}
                onToggle={() => void toggleEnabled(conn)}
                size="sm"
                label={conn.name}
              />

              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-semibold text-[var(--tower-text)] truncate">
                    {conn.name}
                  </span>
                  {conn.hasAuthKey && (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/12 text-emerald-600 dark:text-emerald-400 text-[10px] font-semibold">
                      <KeyRound className="w-3 h-3" />
                      <span>已保存密钥</span>
                    </span>
                  )}
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-2 text-[11px] text-[var(--tower-text-secondary)]">
                  {conn.magicDNSSuffix && <span className="font-mono">{conn.magicDNSSuffix}</span>}
                  {conn.subnets.length > 0 && (
                    <span className="font-mono">子网: {conn.subnets.join(', ')}</span>
                  )}
                  {conn.controlURLString && (
                    <span className="truncate max-w-[220px]">{conn.controlURLString}</span>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-1 shrink-0">
                <button
                  type="button"
                  onClick={() => openEdit(conn)}
                  aria-label="编辑内网"
                  className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] hover:bg-[var(--tower-surface-subtle)] cursor-pointer"
                >
                  <Edit3 className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmDelete(conn)}
                  aria-label="删除内网"
                  className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-red-500 hover:bg-red-500/10 cursor-pointer"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create/Edit Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? '编辑 Tailscale 内网' : '添加 Tailscale 内网'}
        subtitle="支持官方 Tailscale 与自建 Headscale 控制面"
        footer={
          <>
            <button
              type="button"
              onClick={() => setModalOpen(false)}
              className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={saving || !name.trim()}
              className="px-5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 disabled:opacity-50 cursor-pointer"
            >
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="space-y-3.5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1">
                连接名称 *
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="例如：家庭内网"
                className="w-full px-3 py-2 text-sm tower-input"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1">
                设备名称（可选）
              </label>
              <input
                type="text"
                value={deviceName}
                onChange={(e) => setDeviceName(e.target.value)}
                placeholder="例如：tower-iphone"
                className="w-full px-3 py-2 text-sm tower-input font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1">
              控制服务器 URL（官方 Tailscale 留空，Headscale 填域名）
            </label>
            <input
              type="url"
              value={controlURL}
              onChange={(e) => setControlURL(e.target.value)}
              placeholder="https://headscale.example.com"
              className="w-full px-3 py-2 text-sm tower-input font-mono"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1">
              MagicDNS 后缀（可选）
            </label>
            <input
              type="text"
              value={magicDNS}
              onChange={(e) => setMagicDNS(e.target.value)}
              placeholder="例如：tail12345.ts.net"
              className="w-full px-3 py-2 text-sm tower-input font-mono"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1">
              内网路由子网（逗号或换行分隔 CIDR）
            </label>
            <input
              type="text"
              value={subnetsText}
              onChange={(e) => setSubnetsText(e.target.value)}
              placeholder="100.64.0.0/10, 192.168.10.0/24"
              className="w-full px-3 py-2 text-sm tower-input font-mono"
            />
          </div>

          <div className="pt-1 border-t border-[var(--tower-border)] space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-[var(--tower-text-secondary)]">
                Auth Key（用于 Stash / mihomo / sing-box；Surge 忽略）
              </label>
              {editing?.hasAuthKey && (
                <span className="text-[11px] font-semibold text-emerald-600 dark:text-emerald-400">
                  已保存密钥
                </span>
              )}
            </div>
            <input
              type="password"
              value={authKeyInput}
              disabled={clearStoredKey}
              onChange={(e) => setAuthKeyInput(e.target.value)}
              placeholder={
                editing?.hasAuthKey
                  ? '留空则保持当前已保存的 Auth Key 不变'
                  : 'tskey-auth-...'
              }
              className="w-full px-3 py-2 text-sm tower-input font-mono disabled:opacity-50"
            />
            {editing?.hasAuthKey && (
              <label className="flex items-center gap-2 text-xs text-red-500 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={clearStoredKey}
                  onChange={(e) => setClearStoredKey(e.target.checked)}
                  className="accent-red-500"
                />
                <span>清除已保存的 Auth Key</span>
              </label>
            )}
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        open={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        title="确认删除 Tailscale 内网"
        maxWidthClass="max-w-sm"
        footer={
          <>
            <button
              type="button"
              onClick={() => setConfirmDelete(null)}
              className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] cursor-pointer"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void handleDelete()}
              disabled={saving}
              className="px-4 py-2 rounded-[12px] bg-red-500 text-white text-sm font-semibold hover:bg-red-600 cursor-pointer"
            >
              {saving ? '删除中…' : '确认删除'}
            </button>
          </>
        }
      >
        <p className="text-sm text-[var(--tower-text-secondary)]">
          确定要删除 <strong className="text-[var(--tower-text)]">「{confirmDelete?.name}」</strong> 及其保存的密钥吗？
        </p>
      </Modal>
    </section>
  );
}
