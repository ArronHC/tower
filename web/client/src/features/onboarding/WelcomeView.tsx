import { ArrowRight, Globe, Layers, Lock, Share2, ShieldCheck } from 'lucide-react';
import { Modal } from '@/components/Modal';
import { useTower } from '@/state/TowerContext';

export function WelcomeView() {
  const { showWelcome, dismissWelcomePermanently, setActiveTab } = useTower();

  const steps = [
    {
      num: '01',
      title: '添加订阅或自有节点',
      desc: '粘贴订阅链接、Clash / Surge 配置或代理 URI，本机自动识别协议、测速并定位离线国家库。',
      icon: Globe,
    },
    {
      num: '02',
      title: '选择并微调分流规则',
      desc: '内置 ACL4SSR 方案或导入任意规则，拖拽即可调整策略组顺序与匹配优先级，支持冲突即时撤销。',
      icon: Layers,
    },
    {
      num: '03',
      title: '一键导出或局域网订阅',
      desc: '覆盖 18 款主流客户端，按客户端能力保真输出与过滤；支持生成局域网二维码直接扫码导入。',
      icon: Share2,
    },
  ];

  const handleStart = () => {
    dismissWelcomePermanently();
    setActiveTab('subscriptions');
  };

  return (
    <Modal
      open={showWelcome}
      onClose={dismissWelcomePermanently}
      title="欢迎使用塔台 · Tower"
      subtitle="本机优先的跨客户端代理配置工作台"
      maxWidthClass="max-w-xl"
    >
      <div className="space-y-5">
        <div className="flex items-start gap-3.5 p-4 rounded-[18px] bg-emerald-500/10 border border-emerald-500/20">
          <div className="w-10 h-10 rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div className="space-y-1">
            <div className="text-sm font-semibold text-[var(--tower-text)] flex items-center gap-1.5">
              <Lock className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
              <span>全部在本机处理，绝不上传第三方</span>
            </div>
            <p className="text-xs leading-relaxed text-[var(--tower-text-secondary)]">
              订阅拉取、离线 IP/ASN 查询、分流规则重构与配置生成均在您的本地服务内完成，不经过任何外部转换服务器，凭据与密钥以受限权限独立存储。
            </p>
          </div>
        </div>

        <div className="space-y-3">
          {steps.map((step) => {
            const Icon = step.icon;
            return (
              <div
                key={step.num}
                className="flex items-start gap-3.5 p-3.5 rounded-[16px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]"
              >
                <div className="w-9 h-9 rounded-[11px] bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] flex items-center justify-center shrink-0">
                  <Icon className="w-4.5 h-4.5" />
                </div>
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-[11px] font-bold tabular-nums text-[var(--tower-accent)]">
                      {step.num}
                    </span>
                    <h3 className="text-sm font-semibold text-[var(--tower-text)]">
                      {step.title}
                    </h3>
                  </div>
                  <p className="mt-1 text-xs leading-relaxed text-[var(--tower-text-secondary)]">
                    {step.desc}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        <button
          type="button"
          onClick={handleStart}
          className="w-full h-12 rounded-[16px] bg-[var(--tower-accent)] text-white font-semibold text-sm flex items-center justify-center gap-2 hover:opacity-92 active:scale-[0.99] transition-all cursor-pointer"
        >
          <span>开始配置</span>
          <ArrowRight className="w-4 h-4" />
        </button>
      </div>
    </Modal>
  );
}
