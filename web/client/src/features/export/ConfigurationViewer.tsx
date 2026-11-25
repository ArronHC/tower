import React, { useMemo, useState } from 'react';
import { EyeOff, Search, WrapText, X } from 'lucide-react';

interface ConfigurationViewerProps {
  content: string;
  fileExtension: string;
  hasMaskedSecrets?: boolean;
}

function renderHighlightedLine(line: string, ext: string, query: string): React.ReactNode {
  const trimmed = line.trim();

  // Comments
  if (trimmed.startsWith('#') || trimmed.startsWith(';') || trimmed.startsWith('//')) {
    return <span className="text-[var(--tower-text-tertiary)] italic">{highlightSearch(line, query)}</span>;
  }

  // INI Section Header [Proxy], [Rule]
  if (ext === 'conf' && /^\[[^\]]+\]$/.test(trimmed)) {
    return <span className="text-[var(--tower-accent)] font-semibold">{highlightSearch(line, query)}</span>;
  }

  // JSON key: value
  if (ext === 'json') {
    const jsonKeyMatch = line.match(/^(\s*"[^"]+"\s*:\s*)(.*)$/);
    if (jsonKeyMatch) {
      return (
        <>
          <span className="text-sky-600 dark:text-sky-400">{highlightSearch(jsonKeyMatch[1]!, query)}</span>
          <span className="text-[var(--tower-text)]">{highlightSearch(jsonKeyMatch[2]!, query)}</span>
        </>
      );
    }
  }

  // YAML key: value
  if (ext === 'yaml' || ext === 'yml') {
    const yamlMatch = line.match(/^(\s*-?\s*[\w-]+:\s*)(.*)$/);
    if (yamlMatch) {
      return (
        <>
          <span className="text-sky-600 dark:text-sky-400">{highlightSearch(yamlMatch[1]!, query)}</span>
          <span className="text-emerald-700 dark:text-emerald-300">{highlightSearch(yamlMatch[2]!, query)}</span>
        </>
      );
    }
  }

  // INI Key = Value
  if (ext === 'conf') {
    const eqIdx = line.indexOf('=');
    if (eqIdx > 0) {
      const lhs = line.slice(0, eqIdx);
      const rhs = line.slice(eqIdx);
      return (
        <>
          <span className="text-indigo-600 dark:text-indigo-400">{highlightSearch(lhs, query)}</span>
          <span className="text-[var(--tower-text)]">{highlightSearch(rhs, query)}</span>
        </>
      );
    }
  }

  return <span>{highlightSearch(line, query)}</span>;
}

function highlightSearch(text: string, query: string): React.ReactNode {
  if (!query) return text;
  const lower = text.toLowerCase();
  const q = query.toLowerCase();
  const idx = lower.indexOf(q);
  if (idx < 0) return text;

  const parts: React.ReactNode[] = [];
  let cursor = 0;
  let matchIdx = idx;
  let key = 0;
  while (matchIdx >= 0) {
    if (matchIdx > cursor) {
      parts.push(text.slice(cursor, matchIdx));
    }
    parts.push(
      <mark
        key={key++}
        className="bg-amber-300/60 dark:bg-amber-500/40 text-inherit rounded-[2px] px-0.5"
      >
        {text.slice(matchIdx, matchIdx + query.length)}
      </mark>
    );
    cursor = matchIdx + query.length;
    matchIdx = lower.indexOf(q, cursor);
  }
  if (cursor < text.length) parts.push(text.slice(cursor));
  return <>{parts}</>;
}

export function ConfigurationViewer({
  content,
  fileExtension,
  hasMaskedSecrets = false,
}: ConfigurationViewerProps) {
  const [search, setSearch] = useState('');
  const [wrap, setWrap] = useState(false);

  const lines = useMemo(() => content.split('\n'), [content]);

  const matchCount = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return 0;
    let count = 0;
    for (const line of lines) {
      if (line.toLowerCase().includes(q)) count++;
    }
    return count;
  }, [lines, search]);

  const visibleLines = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return lines.map((text, idx) => ({ lineNumber: idx + 1, text }));
    return lines
      .map((text, idx) => ({ lineNumber: idx + 1, text }))
      .filter((item) => item.text.toLowerCase().includes(q));
  }, [lines, search]);

  const showsMaskBadge =
    hasMaskedSecrets || content.includes('tskey-***') || content.includes('••••');

  return (
    <div className="tower-card overflow-hidden">
      {/* Viewer Toolbar */}
      <div className="px-4 py-2.5 border-b border-[var(--tower-border)] bg-[var(--tower-surface-subtle)]/70 flex flex-wrap items-center justify-between gap-2.5">
        <div className="flex items-center gap-2.5 flex-1 min-w-[200px]">
          <div className="relative flex-1 max-w-xs">
            <Search className="w-3.5 h-3.5 text-[var(--tower-text-tertiary)] absolute left-2.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="在配置预览中筛选行…"
              className="w-full pl-8 pr-7 py-1.5 text-xs tower-input"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--tower-text-tertiary)] hover:text-[var(--tower-text)] cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
          {search.trim() && (
            <span className="text-[11px] text-[var(--tower-text-secondary)] tabular-nums">
              命中 {matchCount} 行
            </span>
          )}
        </div>

        <div className="flex items-center gap-2">
          {showsMaskBadge && (
            <span
              title="Tailscale Auth Key 在预览中已自动打码脱敏，下载或订阅导出时会按客户端规则处理"
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-500/12 text-amber-700 dark:text-amber-300 text-[11px] font-medium"
            >
              <EyeOff className="w-3 h-3" />
              <span>密钥已脱敏</span>
            </span>
          )}
          <span className="text-[11px] text-[var(--tower-text-tertiary)] tabular-nums">
            共 {lines.length} 行
          </span>
          <button
            type="button"
            onClick={() => setWrap((v) => !v)}
            className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-[9px] text-xs font-medium border transition-colors cursor-pointer ${
              wrap
                ? 'bg-[var(--tower-accent-soft)] border-[var(--tower-accent)]/30 text-[var(--tower-accent)]'
                : 'bg-[var(--tower-surface)] border-[var(--tower-border)] text-[var(--tower-text-secondary)]'
            }`}
          >
            <WrapText className="w-3.5 h-3.5" />
            <span>自动换行</span>
          </button>
        </div>
      </div>

      {/* Code Body */}
      <div className="max-h-[480px] overflow-auto bg-[var(--tower-surface)] font-mono text-[12px] leading-[1.6]">
        {visibleLines.length === 0 ? (
          <div className="py-12 text-center text-xs text-[var(--tower-text-secondary)]">
            没有匹配「{search}」的配置行
          </div>
        ) : (
          <table className="w-full border-collapse">
            <tbody>
              {visibleLines.slice(0, 2500).map((row) => (
                <tr
                  key={row.lineNumber}
                  className="hover:bg-[var(--tower-surface-subtle)]/50 transition-colors"
                >
                  <td className="select-none text-right pr-3 pl-3 py-0 text-[11px] text-[var(--tower-text-tertiary)] tabular-nums w-12 align-top border-r border-[var(--tower-border)]/60">
                    {row.lineNumber}
                  </td>
                  <td
                    className={`pl-3 pr-4 py-0 align-top ${
                      wrap ? 'whitespace-pre-wrap break-all' : 'whitespace-pre'
                    }`}
                  >
                    {renderHighlightedLine(row.text, fileExtension, search.trim())}
                  </td>
                </tr>
              ))}
              {visibleLines.length > 2500 && (
                <tr>
                  <td colSpan={2} className="py-3 text-center text-xs text-[var(--tower-text-secondary)]">
                    预览仅展示前 2,500 行，完整内容请直接下载或复制
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
