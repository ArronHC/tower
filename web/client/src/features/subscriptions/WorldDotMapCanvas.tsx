import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Minus, Plus, RotateCcw } from 'lucide-react';
import { useTower } from '@/state/TowerContext';
import type { ProxyNode } from '@shared/index';

type LatencyBand = 'fast' | 'medium' | 'slow' | 'unreachable' | 'untested';

interface CountryAggregate {
  code: string;
  name: string;
  flag: string;
  latitude: number;
  longitude: number;
  nodeCount: number;
  medianMs: number | null;
  band: LatencyBand;
  cells: number[];
}

function clamp(v: number, min: number, max: number): number {
  return Math.min(Math.max(v, min), max);
}

function mercatorY(latitude: number): number {
  const clamped = clamp(latitude, -85, 85);
  return Math.log(Math.tan(Math.PI / 4 + (clamped * Math.PI) / 360));
}

function unitPoint(
  latitude: number,
  longitude: number,
  bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }
): { x: number; y: number } {
  const x = (longitude - bounds.minLon) / (bounds.maxLon - bounds.minLon);
  const top = mercatorY(bounds.maxLat);
  const bottom = mercatorY(bounds.minLat);
  const y = (top - mercatorY(latitude)) / (top - bottom);
  return { x: clamp(x, 0, 1), y: clamp(y, 0, 1) };
}

function bandForCountry(nodes: ProxyNode[], runtimeMap: Record<string, { latency?: { milliseconds: number | null; isApplicable: boolean } | null }>): {
  band: LatencyBand;
  medianMs: number | null;
} {
  const values: number[] = [];
  let anyTested = false;
  let allFailed = true;

  for (const node of nodes) {
    const lat = runtimeMap[node.id]?.latency;
    if (!lat) {
      allFailed = false;
      continue;
    }
    anyTested = true;
    if (lat.milliseconds != null) {
      values.push(lat.milliseconds);
      allFailed = false;
    } else if (!lat.isApplicable) {
      allFailed = false;
    }
  }

  if (values.length > 0) {
    values.sort((a, b) => a - b);
    const mid = Math.floor(values.length / 2);
    const median =
      values.length % 2 === 0
        ? Math.round((values[mid - 1]! + values[mid]!) / 2)
        : values[mid]!;
    if (median < 80) return { band: 'fast', medianMs: median };
    if (median < 180) return { band: 'medium', medianMs: median };
    return { band: 'slow', medianMs: median };
  }

  if (anyTested && allFailed) {
    return { band: 'unreachable', medianMs: null };
  }
  return { band: 'untested', medianMs: null };
}

function dotFillColor(
  band: LatencyBand,
  emphasized: boolean,
  dark: boolean
): string {
  // Exact spec: <80ms emerald, <180ms amber, >=180ms coral/red, untested steel-blue #6488B4
  // Hovered/selected gets darker/richer tint in the same hue
  switch (band) {
    case 'fast':
      if (emphasized) return dark ? '#059669' : '#047857';
      return dark ? '#34D399' : '#10B981';
    case 'medium':
      if (emphasized) return dark ? '#D97706' : '#B45309';
      return dark ? '#FBBF24' : '#F59E0B';
    case 'slow':
      if (emphasized) return dark ? '#DC2626' : '#B91C1C';
      return dark ? '#F87171' : '#EF4444';
    case 'unreachable':
      if (emphasized) return dark ? '#9D174D' : '#831843';
      return dark ? '#F472B6' : '#BE185D';
    case 'untested':
    default:
      if (emphasized) return dark ? '#3B6291' : '#3E5F8A';
      return dark ? '#7DA0C9' : '#6488B4';
  }
}

export function WorldDotMapCanvas() {
  const {
    state,
    worldMap,
    countries,
    selectedCountryCode,
    setSelectedCountryCode,
    resolvedDark,
  } = useTower();

  const containerRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [size, setSize] = useState<{ width: number; height: number }>({ width: 800, height: 520 });
  const [scale, setScale] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [hoveredCode, setHoveredCode] = useState<string | null>(null);

  const dragRef = useRef<{
    active: boolean;
    moved: boolean;
    startX: number;
    startY: number;
    origOffsetX: number;
    origOffsetY: number;
  }>({
    active: false,
    moved: false,
    startX: 0,
    startY: 0,
    origOffsetX: 0,
    origOffsetY: 0,
  });

  const countryByCode = useMemo(() => {
    const map = new Map<string, (typeof countries)[number]>();
    for (const c of countries) {
      map.set(c.code.toUpperCase(), c);
    }
    return map;
  }, [countries]);

  // Build covered countries list from non-excluded, non-metadata nodes
  const coveredCountries = useMemo<CountryAggregate[]>(() => {
    if (!state) return [];
    const excludedSet = new Set(state.snapshot.excludedNodeIDs);
    const enabledSubs = new Set(
      state.snapshot.subscriptions.filter((s) => s.isEnabled).map((s) => s.id)
    );

    const grouped = new Map<string, ProxyNode[]>();
    for (const node of state.snapshot.nodes) {
      if (node.isSubscriptionMetadata) continue;
      if (node.sourceID && !enabledSubs.has(node.sourceID)) continue;
      if (excludedSet.has(node.id)) continue;
      const rt = state.runtime[node.id];
      const code = (node.countryOverride || rt?.countryCode || '').toUpperCase();
      if (!code || code.length !== 2) continue;
      const list = grouped.get(code) ?? [];
      list.push(node);
      grouped.set(code, list);
    }

    const result: CountryAggregate[] = [];
    for (const [code, nodes] of grouped.entries()) {
      const meta = countryByCode.get(code);
      const firstRt = state.runtime[nodes[0]!.id];
      const lat = meta?.latitude ?? firstRt?.latitude ?? 0;
      const lon = meta?.longitude ?? firstRt?.longitude ?? 0;
      const name = meta?.chineseName ?? firstRt?.countryName ?? code;
      const flag = meta?.flag ?? firstRt?.flagEmoji ?? '';
      const { band, medianMs } = bandForCountry(nodes, state.runtime);

      let cells = worldMap?.countryCells[code] ?? [];
      if (cells.length === 0 && worldMap && worldMap.columns > 0 && worldMap.rows > 0) {
        // Nearest land cell fallback for microstates (SG, HK, MO, etc.) without separate pins
        const pt = unitPoint(lat, lon, worldMap.bounds);
        const targetCol = pt.x * worldMap.columns - 0.5;
        const targetRow = pt.y * worldMap.rows - 0.5;
        let bestIdx = -1;
        let bestDist = Infinity;
        for (let r = 0; r < worldMap.rows; r++) {
          const rowStr = worldMap.landRows[r] ?? '';
          for (let c = 0; c < worldMap.columns; c++) {
            if (rowStr[c] !== '#') continue;
            const d = (c - targetCol) ** 2 + (r - targetRow) ** 2;
            if (d < bestDist) {
              bestDist = d;
              bestIdx = r * worldMap.columns + c;
            }
          }
        }
        if (bestIdx >= 0) cells = [bestIdx];
      }

      result.push({
        code,
        name,
        flag,
        latitude: lat,
        longitude: lon,
        nodeCount: nodes.length,
        medianMs,
        band,
        cells,
      });
    }

    return result.sort((a, b) => b.nodeCount - a.nodeCount || a.code.localeCompare(b.code));
  }, [countryByCode, state, worldMap]);

  const cellOwnerMap = useMemo(() => {
    const map = new Map<number, CountryAggregate>();
    for (const country of coveredCountries) {
      for (const cell of country.cells) {
        map.set(cell, country);
      }
    }
    return map;
  }, [coveredCountries]);

  // Precompute land cell coordinates once per worldMap
  const landCells = useMemo(() => {
    if (!worldMap) return [];
    const list: number[] = [];
    for (let r = 0; r < worldMap.rows; r++) {
      const rowStr = worldMap.landRows[r] ?? '';
      for (let c = 0; c < worldMap.columns; c++) {
        if (rowStr[c] === '#') {
          list.push(r * worldMap.columns + c);
        }
      }
    }
    return list;
  }, [worldMap]);

  // Observe container resize
  useEffect(() => {
    const el = containerRef.current;
    if (!el || !worldMap) return;
    const ratio = worldMap.columns / Math.max(worldMap.rows, 1);

    const updateSize = () => {
      const w = el.clientWidth;
      if (w > 0) {
        setSize({ width: w, height: Math.round(w / ratio) });
      }
    };
    updateSize();
    const ro = new ResizeObserver(updateSize);
    ro.observe(el);
    return () => ro.disconnect();
  }, [worldMap]);

  const clampOffset = useCallback(
    (nextX: number, nextY: number, nextScale: number) => {
      if (nextScale <= 1.001) return { x: 0, y: 0 };
      const maxX = (size.width * nextScale) / 2;
      const maxY = (size.height * nextScale) / 2;
      return {
        x: clamp(nextX, -maxX, maxX),
        y: clamp(nextY, -maxY, maxY),
      };
    },
    [size.height, size.width]
  );

  // Wheel zoom with non-passive listener
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const rect = canvas.getBoundingClientRect();
      const px = e.clientX - rect.left;
      const py = e.clientY - rect.top;
      const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;

      setScale((prevScale) => {
        const nextScale = clamp(prevScale * factor, 1, 4.5);
        const ratio = nextScale / prevScale;
        const cx = size.width / 2;
        const cy = size.height / 2;
        setOffset((prevOffset) =>
          clampOffset(
            (px - cx) * (1 - ratio) + prevOffset.x * ratio,
            (py - cy) * (1 - ratio) + prevOffset.y * ratio,
            nextScale
          )
        );
        return nextScale;
      });
    };
    canvas.addEventListener('wheel', onWheel, { passive: false });
    return () => canvas.removeEventListener('wheel', onWheel);
  }, [clampOffset, size.height, size.width]);

  const layout = useMemo(() => {
    if (!worldMap || worldMap.columns === 0 || worldMap.rows === 0) {
      return { cell: 0, originX: 0, originY: 0 };
    }
    const cell = Math.min(size.width / worldMap.columns, size.height / worldMap.rows);
    const originX = (size.width - cell * worldMap.columns) / 2;
    const originY = (size.height - cell * worldMap.rows) / 2;
    return { cell, originX, originY };
  }, [size.height, size.width, worldMap]);

  // Draw HiDPI canvas
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !worldMap || layout.cell <= 0) return;

    const dpr = Math.min(window.devicePixelRatio || 1, 3);
    canvas.width = Math.max(1, Math.round(size.width * dpr));
    canvas.height = Math.max(1, Math.round(size.height * dpr));

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, size.width, size.height);

    const cx = size.width / 2;
    const cy = size.height / 2;
    ctx.translate(cx + offset.x, cy + offset.y);
    ctx.scale(scale, scale);
    ctx.translate(-cx, -cy);

    const cols = worldMap.columns;
    const baseCell = layout.cell;
    const uncoveredRadius = Math.max((baseCell * 0.56) / 2, 0.55);
    const coveredRadius = Math.max((baseCell * 0.74) / 2, 0.75);
    const selectedRadius = Math.max((baseCell * 0.92) / 2, 0.95);

    // 1. Uncovered land dots
    ctx.fillStyle = resolvedDark ? 'rgba(255, 255, 255, 0.20)' : 'rgba(17, 24, 39, 0.14)';
    ctx.beginPath();
    for (const idx of landCells) {
      if (cellOwnerMap.has(idx)) continue;
      const col = idx % cols;
      const row = Math.floor(idx / cols);
      const x = layout.originX + (col + 0.5) * baseCell;
      const y = layout.originY + (row + 0.5) * baseCell;
      ctx.moveTo(x + uncoveredRadius, y);
      ctx.arc(x, y, uncoveredRadius, 0, Math.PI * 2);
    }
    ctx.fill();

    // 2. Covered countries grouped by fill color
    const activeUpper = selectedCountryCode?.toUpperCase() ?? null;
    const hoverUpper = hoveredCode?.toUpperCase() ?? null;
    const buckets = new Map<string, { radius: number; cells: number[] }>();

    for (const country of coveredCountries) {
      const isSelected = country.code === activeUpper;
      const isHovered = country.code === hoverUpper;
      const emphasized = isSelected || isHovered;
      const color = dotFillColor(country.band, emphasized, resolvedDark);
      const radius = isSelected ? selectedRadius : isHovered ? selectedRadius * 0.95 : coveredRadius;
      const key = `${color}|${radius.toFixed(2)}`;
      let bucket = buckets.get(key);
      if (!bucket) {
        bucket = { radius, cells: [] };
        buckets.set(key, bucket);
      }
      for (const idx of country.cells) {
        bucket.cells.push(idx);
      }
    }

    for (const [key, bucket] of buckets.entries()) {
      const color = key.split('|')[0]!;
      ctx.fillStyle = color;
      ctx.beginPath();
      for (const idx of bucket.cells) {
        const col = idx % cols;
        const row = Math.floor(idx / cols);
        const x = layout.originX + (col + 0.5) * baseCell;
        const y = layout.originY + (row + 0.5) * baseCell;
        ctx.moveTo(x + bucket.radius, y);
        ctx.arc(x, y, bucket.radius, 0, Math.PI * 2);
      }
      ctx.fill();
    }

    ctx.restore();
  }, [
    cellOwnerMap,
    coveredCountries,
    hoveredCode,
    landCells,
    layout.cell,
    layout.originX,
    layout.originY,
    offset.x,
    offset.y,
    resolvedDark,
    scale,
    selectedCountryCode,
    size.height,
    size.width,
    worldMap,
  ]);

  const hitTestCountry = useCallback(
    (clientX: number, clientY: number): CountryAggregate | null => {
      const canvas = canvasRef.current;
      if (!canvas || !worldMap || layout.cell <= 0) return null;
      const rect = canvas.getBoundingClientRect();
      const vx = clientX - rect.left;
      const vy = clientY - rect.top;

      const cx = size.width / 2;
      const cy = size.height / 2;
      const mx = cx + (vx - cx - offset.x) / scale;
      const my = cy + (vy - cy - offset.y) / scale;

      const col = Math.floor((mx - layout.originX) / layout.cell);
      const row = Math.floor((my - layout.originY) / layout.cell);
      if (col >= 0 && col < worldMap.columns && row >= 0 && row < worldMap.rows) {
        const idx = row * worldMap.columns + col;
        const direct = cellOwnerMap.get(idx);
        if (direct) return direct;

        // Check if tap landed on an uncovered country cell: do not steal to neighbor
        const rowStr = worldMap.landRows[row] ?? '';
        if (rowStr[col] === '#') {
          // Check small 1-cell neighborhood in case microstate sits on boundary
          for (let dr = -1; dr <= 1; dr++) {
            for (let dc = -1; dc <= 1; dc++) {
              const nr = row + dr;
              const nc = col + dc;
              if (nr >= 0 && nr < worldMap.rows && nc >= 0 && nc < worldMap.columns) {
                const hit = cellOwnerMap.get(nr * worldMap.columns + nc);
                if (hit && hit.cells.length <= 4) return hit;
              }
            }
          }
          return null;
        }
      }

      // Water fallback for tiny island regions (e.g. Singapore, HK, TW, Japan islands)
      let best: CountryAggregate | null = null;
      let bestDist = 22;
      for (const country of coveredCountries) {
        const pt = unitPoint(country.latitude, country.longitude, worldMap.bounds);
        const bx = layout.originX + pt.x * layout.cell * worldMap.columns;
        const by = layout.originY + pt.y * layout.cell * worldMap.rows;
        const sx = cx + (bx - cx) * scale + offset.x;
        const sy = cy + (by - cy) * scale + offset.y;
        const dist = Math.hypot(sx - vx, sy - vy);
        if (dist <= bestDist) {
          bestDist = dist;
          best = country;
        }
      }
      return best;
    },
    [
      cellOwnerMap,
      coveredCountries,
      layout.cell,
      layout.originX,
      layout.originY,
      offset.x,
      offset.y,
      scale,
      size.height,
      size.width,
      worldMap,
    ]
  );

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    dragRef.current = {
      active: true,
      moved: false,
      startX: e.clientX,
      startY: e.clientY,
      origOffsetX: offset.x,
      origOffsetY: offset.y,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (dragRef.current.active) {
      const dx = e.clientX - dragRef.current.startX;
      const dy = e.clientY - dragRef.current.startY;
      if (Math.hypot(dx, dy) > 4) {
        dragRef.current.moved = true;
      }
      if (dragRef.current.moved && scale > 1.001) {
        setOffset(clampOffset(dragRef.current.origOffsetX + dx, dragRef.current.origOffsetY + dy, scale));
      }
      return;
    }

    const hit = hitTestCountry(e.clientX, e.clientY);
    setHoveredCode(hit?.code ?? null);
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const wasMoved = dragRef.current.moved;
    dragRef.current.active = false;
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId);
    }
    if (!wasMoved) {
      const hit = hitTestCountry(e.clientX, e.clientY);
      if (hit) {
        setSelectedCountryCode(selectedCountryCode === hit.code ? null : hit.code);
      }
    }
  };

  const zoomStep = (multiplier: number) => {
    setScale((prev) => {
      const next = clamp(prev * multiplier, 1, 4.5);
      setOffset((o) => clampOffset(o.x * (next / prev), o.y * (next / prev), next));
      return next;
    });
  };

  const resetView = () => {
    setScale(1);
    setOffset({ x: 0, y: 0 });
  };

  // Compute screen position for active/hovered country label pill
  const labelCountries = useMemo(() => {
    if (!worldMap || layout.cell <= 0) return [];
    const list: Array<{ country: CountryAggregate; x: number; y: number; isSelected: boolean }> = [];
    const cx = size.width / 2;
    const cy = size.height / 2;

    const activeUpper = selectedCountryCode?.toUpperCase() ?? null;
    const hoverUpper = hoveredCode?.toUpperCase() ?? null;

    for (const country of coveredCountries) {
      const isSelected = country.code === activeUpper;
      const isHovered = country.code === hoverUpper;
      if (!isSelected && !isHovered) continue;
      const pt = unitPoint(country.latitude, country.longitude, worldMap.bounds);
      const bx = layout.originX + pt.x * layout.cell * worldMap.columns;
      const by = layout.originY + pt.y * layout.cell * worldMap.rows;
      const sx = cx + (bx - cx) * scale + offset.x;
      const sy = cy + (by - cy) * scale + offset.y;
      list.push({
        country,
        x: clamp(sx, 54, size.width - 54),
        y: clamp(sy - 16, 20, size.height - 20),
        isSelected,
      });
    }
    return list;
  }, [
    coveredCountries,
    hoveredCode,
    layout.cell,
    layout.originX,
    layout.originY,
    offset.x,
    offset.y,
    scale,
    selectedCountryCode,
    size.height,
    size.width,
    worldMap,
  ]);

  const isModified = scale > 1.01 || Math.abs(offset.x) > 1 || Math.abs(offset.y) > 1;

  return (
    <div className="tower-card p-4 sm:p-5 space-y-3.5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <h2 className="text-[15px] font-semibold text-[var(--tower-text)]">节点分布</h2>
          {selectedCountryCode && (
            <button
              type="button"
              onClick={() => setSelectedCountryCode(null)}
              className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] text-xs font-medium hover:opacity-85 cursor-pointer"
            >
              <span>
                {coveredCountries.find((c) => c.code === selectedCountryCode)?.flag}{' '}
                {coveredCountries.find((c) => c.code === selectedCountryCode)?.name ?? selectedCountryCode}
              </span>
              <span className="opacity-70">×</span>
            </button>
          )}
        </div>

        {/* Latency Legend */}
        <div className="flex items-center gap-3 text-[11px] text-[var(--tower-text-secondary)]">
          <span className="inline-flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            <span>&lt;80ms</span>
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            <span>&lt;180ms</span>
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-red-500" />
            <span>≥180ms</span>
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-[#6488B4]" />
            <span>未测速</span>
          </span>
        </div>
      </div>

      {/* Canvas Container */}
      <div
        ref={containerRef}
        className="relative w-full overflow-hidden rounded-[16px] bg-[var(--tower-surface-subtle)]/65 border border-[var(--tower-border)] select-none"
      >
        {!worldMap ? (
          <div className="h-[280px] flex items-center justify-center text-sm text-[var(--tower-text-secondary)]">
            正在加载世界点阵地图…
          </div>
        ) : (
          <div className="relative" style={{ width: size.width, height: size.height }}>
            <canvas
              ref={canvasRef}
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={handlePointerUp}
              onPointerLeave={() => {
                dragRef.current.active = false;
                setHoveredCode(null);
              }}
              style={{ width: size.width, height: size.height }}
              className={`block touch-none ${hoveredCode ? 'cursor-pointer' : scale > 1.01 ? 'cursor-grab active:cursor-grabbing' : 'cursor-default'}`}
            />

            {/* Floating country label pills (only on hover/select) */}
            {labelCountries.map(({ country, x, y, isSelected }) => (
              <div
                key={country.code}
                style={{ left: x, top: y }}
                className={`pointer-events-none absolute -translate-x-1/2 -translate-y-1/2 px-2.5 py-1 rounded-full text-xs font-semibold whitespace-nowrap border backdrop-blur-md shadow-sm transition-opacity ${
                  isSelected
                    ? 'bg-[var(--tower-surface)] border-[var(--tower-border-strong)] text-[var(--tower-text)] z-10'
                    : 'bg-[var(--tower-surface)]/90 border-[var(--tower-border)] text-[var(--tower-text)]'
                }`}
              >
                <span className="mr-1">{country.flag}</span>
                <span>{country.name}</span>
                <span className="ml-1.5 text-[11px] tabular-nums text-[var(--tower-text-secondary)]">
                  {country.nodeCount}
                  {country.medianMs != null ? ` · ${country.medianMs}ms` : ''}
                </span>
              </div>
            ))}

            {/* Zoom & Reset Controls */}
            <div className="absolute bottom-2.5 right-2.5 flex items-center gap-1 p-1 rounded-full tower-glass border border-[var(--tower-border)] shadow-sm">
              <button
                type="button"
                onClick={() => zoomStep(1.3)}
                aria-label="放大地图"
                className="w-7 h-7 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] hover:bg-[var(--tower-surface-subtle)] transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => zoomStep(1 / 1.3)}
                aria-label="缩小地图"
                className="w-7 h-7 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] hover:bg-[var(--tower-surface-subtle)] transition-colors cursor-pointer"
              >
                <Minus className="w-3.5 h-3.5" />
              </button>
              {isModified && (
                <button
                  type="button"
                  onClick={resetView}
                  aria-label="重置视图"
                  title="恢复默认视图"
                  className="w-7 h-7 rounded-full flex items-center justify-center text-[var(--tower-accent)] hover:bg-[var(--tower-accent-soft)] transition-colors cursor-pointer"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Quick Covered Countries Pills */}
      {coveredCountries.length > 0 && (
        <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
          <button
            type="button"
            onClick={() => setSelectedCountryCode(null)}
            className={`shrink-0 px-2.5 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${
              selectedCountryCode === null
                ? 'bg-[var(--tower-accent)] text-white'
                : 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
            }`}
          >
            全部国家 ({coveredCountries.length})
          </button>
          {coveredCountries.map((c) => {
            const active = selectedCountryCode === c.code;
            return (
              <button
                key={c.code}
                type="button"
                onClick={() => setSelectedCountryCode(active ? null : c.code)}
                className={`shrink-0 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${
                  active
                    ? 'bg-[var(--tower-accent)] text-white'
                    : 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text)] hover:bg-[var(--tower-border)]'
                }`}
              >
                <span>{c.flag}</span>
                <span>{c.name}</span>
                <span
                  className={`tabular-nums text-[11px] ${
                    active ? 'text-white/80' : 'text-[var(--tower-text-secondary)]'
                  }`}
                >
                  {c.nodeCount}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
