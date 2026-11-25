import { useState } from 'react';
import { FileCode2, Plus, Trash2 } from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { useTower } from '@/state/TowerContext';
import type { CustomRuleFlow, LocalRuleSet } from '@shared/index';

interface LocalRuleSetsModalProps {
  open: boolean;
  schemeId: string;
  onClose: () => void;
}

export function LocalRuleSetsModal({ open, schemeId, onClose }: LocalRuleSetsModalProps) {
  const { state, applyStateFromMutation, showToast } = useTower();
  const [tab, setTab] = useState<'flows' | 'sets'>('flows');

  // Create Local Rule Set state
  const [editingSet, setEditingSet] = useState<LocalRuleSet | null>(null);
  const [setName, setSetName] = useState('');
  const [setRules, setSetRules] = useState('');

  // Create Rule Flow state
  const [flowName, setFlowName] = useState('');
  const [flowPolicy, setFlowPolicy] = useState('');
  const [flowRules, setFlowRules] = useState('');
  const [saving, setSaving] = useState(false);

  const localSets = state?.snapshot.localRuleSets ?? [];
  const flows = (state?.snapshot.customRuleFlows ?? []).filter(
    (f) => f.schemeID === schemeId && !f.isGroupSupplement
  );
  const routingTargets = state?.activeSchemeRoutingTargets ?? ['节点选择', 'DIRECT', 'REJECT'];

  const handleSaveSet = async () => {
    if (!setName.trim() || !setRules.trim()) {
      showToast('请填写规则集名称与规则文本', 'warning');
      return;
    }
    setSaving(true);
    try {
      if (editingSet) {
        const res = await api.updateLocalRuleSet(editingSet.id, {
          name: setName.trim(),
          rulesText: setRules,
        });
        await applyStateFromMutation(res.state);
        showToast('已更新本地规则集', 'success');
      } else {
        const res = await api.createLocalRuleSet({
          name: setName.trim(),
          rulesText: setRules,
        });
        await applyStateFromMutation(res.state);
        showToast('已新建本地规则集', 'success');
      }
      setEditingSet(null);
      setSetName('');
      setSetRules('');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存本地规则集失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteSet = async (setItem: LocalRuleSet) => {
    try {
      const res = await api.deleteLocalRuleSet(setItem.id);
      await applyStateFromMutation(res.state);
      showToast(`已删除规则集「${setItem.name}」`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除失败', 'danger');
    }
  };

  const handleCreateFlow = async () => {
    if (!flowName.trim() || !flowRules.trim()) {
      showToast('请填写规则流名称与规则内容', 'warning');
      return;
    }
    setSaving(true);
    try {
      const res = await api.createRuleFlow({
        schemeID: schemeId,
        name: flowName.trim(),
        policyName: flowPolicy || routingTargets[0] || '节点选择',
        rulesText: flowRules,
        isEnabled: true,
      });
      await applyStateFromMutation(res.state);
      setFlowName('');
      setFlowRules('');
      showToast('已添加自定义规则流', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '创建规则流失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  const toggleFlow = async (flow: CustomRuleFlow) => {
    try {
      const res = await api.updateRuleFlow(flow.id, { isEnabled: !flow.isEnabled });
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '切换状态失败', 'danger');
    }
  };

  const deleteFlow = async (flow: CustomRuleFlow) => {
    try {
      const res = await api.deleteRuleFlow(flow.id);
      await applyStateFromMutation(res.state);
      showToast(`已移除规则流「${flow.name}」`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除失败', 'danger');
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="自定义规则流与本地规则集"
      subtitle="管理本机自定义规则与跨方案复用的规则集合"
      maxWidthClass="max-w-2xl"
    >
      <div className="space-y-4">
        <div className="flex p-1 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]">
          <button
            type="button"
            onClick={() => setTab('flows')}
            className={`flex-1 py-1.5 rounded-[9px] text-xs font-semibold cursor-pointer ${
              tab === 'flows'
                ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                : 'text-[var(--tower-text-secondary)]'
            }`}
          >
            当前方案规则流 ({flows.length})
          </button>
          <button
            type="button"
            onClick={() => setTab('sets')}
            className={`flex-1 py-1.5 rounded-[9px] text-xs font-semibold cursor-pointer ${
              tab === 'sets'
                ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                : 'text-[var(--tower-text-secondary)]'
            }`}
          >
            本地规则集 ({localSets.length})
          </button>
        </div>

        {tab === 'flows' ? (
          <div className="space-y-4">
            {flows.length > 0 && (
              <div className="divide-y divide-[var(--tower-border)] rounded-[14px] border border-[var(--tower-border)]">
                {flows.map((flow) => (
                  <div key={flow.id} className="flex items-center gap-3 px-3.5 py-2.5">
                    <SelectionIndicator
                      selected={flow.isEnabled}
                      onToggle={() => void toggleFlow(flow)}
                      size="sm"
                      label={flow.name}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-[var(--tower-text)] truncate">
                        {flow.name}
                      </div>
                      <div className="text-xs text-[var(--tower-text-secondary)] truncate">
                        策略目标：{flow.policyName}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => void deleteFlow(flow)}
                      className="p-1.5 rounded-full text-[var(--tower-text-secondary)] hover:text-red-500 hover:bg-red-500/10 cursor-pointer"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="p-4 rounded-[16px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] space-y-3">
              <div className="text-xs font-semibold text-[var(--tower-text)] flex items-center gap-1.5">
                <Plus className="w-3.5 h-3.5 text-[var(--tower-accent)]" />
                <span>新增自定义规则流</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <input
                  type="text"
                  value={flowName}
                  onChange={(e) => setFlowName(e.target.value)}
                  placeholder="规则流名称（如：公司内网）"
                  className="px-3 py-2 text-sm tower-input"
                />
                <select
                  value={flowPolicy || routingTargets[0] || ''}
                  onChange={(e) => setFlowPolicy(e.target.value)}
                  className="px-3 py-2 text-sm tower-input"
                >
                  {routingTargets.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
              <textarea
                rows={3}
                value={flowRules}
                onChange={(e) => setFlowRules(e.target.value)}
                placeholder={'DOMAIN-SUFFIX,corp.example.com\nIP-CIDR,10.0.0.0/8,no-resolve'}
                className="w-full p-3 text-xs tower-input font-mono"
              />
              <button
                type="button"
                onClick={() => void handleCreateFlow()}
                disabled={saving}
                className="px-4 py-2 rounded-[11px] bg-[var(--tower-accent)] text-white text-xs font-semibold hover:opacity-92 disabled:opacity-50 cursor-pointer"
              >
                添加规则流
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            {localSets.length > 0 && (
              <div className="divide-y divide-[var(--tower-border)] rounded-[14px] border border-[var(--tower-border)]">
                {localSets.map((item) => (
                  <div key={item.id} className="flex items-center justify-between gap-3 px-3.5 py-2.5">
                    <button
                      type="button"
                      onClick={() => {
                        setEditingSet(item);
                        setSetName(item.name);
                        setSetRules(item.rulesText);
                      }}
                      className="flex items-center gap-2.5 min-w-0 text-left cursor-pointer"
                    >
                      <FileCode2 className="w-4 h-4 text-[var(--tower-accent)] shrink-0" />
                      <div className="min-w-0">
                        <div className="text-sm font-medium text-[var(--tower-text)] truncate">
                          {item.name}
                        </div>
                        <div className="text-xs text-[var(--tower-text-secondary)]">
                          {item.rulesText.split('\n').filter((l) => l.trim()).length} 条规则（点击编辑）
                        </div>
                      </div>
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleDeleteSet(item)}
                      className="p-1.5 rounded-full text-[var(--tower-text-secondary)] hover:text-red-500 hover:bg-red-500/10 cursor-pointer"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="p-4 rounded-[16px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] space-y-3">
              <div className="text-xs font-semibold text-[var(--tower-text)]">
                {editingSet ? `编辑规则集：${editingSet.name}` : '新建本地规则集'}
              </div>
              <input
                type="text"
                value={setName}
                onChange={(e) => setSetName(e.target.value)}
                placeholder="规则集名称"
                className="w-full px-3 py-2 text-sm tower-input"
              />
              <textarea
                rows={4}
                value={setRules}
                onChange={(e) => setSetRules(e.target.value)}
                placeholder={'DOMAIN-SUFFIX,example.org\nDOMAIN,api.example.org'}
                className="w-full p-3 text-xs tower-input font-mono"
              />
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void handleSaveSet()}
                  disabled={saving}
                  className="px-4 py-2 rounded-[11px] bg-[var(--tower-accent)] text-white text-xs font-semibold hover:opacity-92 disabled:opacity-50 cursor-pointer"
                >
                  {editingSet ? '保存更改' : '创建规则集'}
                </button>
                {editingSet && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditingSet(null);
                      setSetName('');
                      setSetRules('');
                    }}
                    className="px-3 py-2 text-xs text-[var(--tower-text-secondary)] cursor-pointer"
                  >
                    取消编辑
                  </button>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
