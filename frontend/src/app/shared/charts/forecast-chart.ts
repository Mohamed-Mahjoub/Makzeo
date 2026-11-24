import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { compact } from './chart-utils';
import { money } from '../format';

export interface ForecastPoint {
  /** libellé de l'axe (ex. 11/2025) */
  label: string;
  value: number;
  /** détail affiché au survol */
  detail?: string;
}

export interface ForecastTarget {
  label: string;
  value: number;
  low: number;
  high: number;
}

const W = 760;
const H = 300;
const M = { left: 64, right: 22, top: 18, bottom: 34 };

/**
 * Historique des prix (moyennes mensuelles) prolongé par la prévision : segment en pointillés,
 * intervalle d'incertitude, axe vertical ajusté aux valeurs et info-bulle au survol.
 */
@Component({
  selector: 'app-forecast-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chart-legend">
      <span><i style="background:#2563eb"></i>Prix payé (moyenne mensuelle)</span>
      <span><i class="dash"></i>Prévision</span>
      <span><i style="background:rgba(217,119,6,.25)"></i>Intervalle d'incertitude</span>
    </div>
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" class="chart-svg fc" role="img" [attr.aria-label]="ariaLabel()"
         (mouseleave)="hover.set(null)">
      @for (t of yTicks(); track t.y) {
        <line [attr.x1]="m.left" [attr.x2]="width - m.right" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
        <text [attr.x]="m.left - 8" [attr.y]="t.y + 4" text-anchor="end" class="axis-label">{{ t.label }}</text>
      }
      @if (band(); as b) {
        <polygon [attr.points]="b" fill="rgba(217,119,6,.18)" />
      }
      <polyline [attr.points]="observedLine()" fill="none" stroke="#2563eb" stroke-width="2.4" stroke-linejoin="round" />
      @if (forecastLine(); as f) {
        <polyline [attr.points]="f" fill="none" stroke="#d97706" stroke-width="2.4" stroke-dasharray="6 5" />
      }
      @for (p of dots(); track p.i) {
        <circle [attr.cx]="p.x" [attr.cy]="p.y" [attr.r]="hover() === p.i ? 5 : 3" [attr.fill]="p.forecast ? '#d97706' : '#2563eb'"
                class="fc__dot" stroke-width="1.5" />
      }
      @for (x of xLabels(); track x.i) {
        <text [attr.x]="x.x" [attr.y]="height - 12" text-anchor="middle" class="axis-label">{{ x.label }}</text>
      }
      <line [attr.x1]="m.left" [attr.x2]="width - m.right" [attr.y1]="height - m.bottom" [attr.y2]="height - m.bottom" class="axis" />
      @if (tooltip(); as t) {
        <line [attr.x1]="t.x" [attr.x2]="t.x" [attr.y1]="m.top" [attr.y2]="height - m.bottom" class="fc__cursor" />
        <g [attr.transform]="'translate(' + t.boxX + ',' + m.top + ')'">
          <rect width="200" [attr.height]="t.lines.length * 16 + 12" rx="6" class="fc__tip" />
          @for (line of t.lines; track $index) {
            <text x="10" [attr.y]="18 + $index * 16" [class.fc__tip-title]="$index === 0" class="fc__tip-text">{{ line }}</text>
          }
        </g>
      }
      <!-- zones de survol (une par point) -->
      @for (z of zones(); track z.i) {
        <rect [attr.x]="z.x" [attr.y]="m.top" [attr.width]="z.w" [attr.height]="height - m.top - m.bottom"
              fill="transparent" (mouseenter)="hover.set(z.i)" />
      }
    </svg>
  `,
  styles: [`
    .dash { background: repeating-linear-gradient(90deg, #d97706 0 6px, transparent 6px 10px) !important; height: 3px !important; }
    .fc__dot { stroke: var(--surface); }
    .fc__cursor { stroke: #94a3b8; stroke-dasharray: 3 3; }
    .fc__tip { fill: #0f172a; opacity: .92; }
    .fc__tip-text { fill: #fff; font-size: 11px; }
    .fc__tip-title { font-weight: 700; }
  `]
})
export class ForecastChart {
  readonly points = input.required<ForecastPoint[]>();
  readonly target = input<ForecastTarget | null>(null);
  readonly ariaLabel = input('Historique et prévision des prix');

  protected readonly width = W;
  protected readonly height = H;
  protected readonly m = M;
  protected readonly hover = signal<number | null>(null);

  private readonly count = computed(() => this.points().length + (this.target() ? 1 : 0));

  private readonly range = computed(() => {
    const t = this.target();
    const values = [...this.points().map((p) => p.value), ...(t ? [t.low, t.high, t.value] : [])];
    let min = Math.min(...values);
    let max = Math.max(...values);
    if (!isFinite(min)) { min = 0; max = 1; }
    const pad = Math.max((max - min) * 0.12, max * 0.02, 0.01);
    return { min: Math.max(0, min - pad), max: max + pad };
  });

  private x(i: number): number {
    const n = Math.max(1, this.count() - 1);
    return M.left + (i / n) * (W - M.left - M.right);
  }

  private y(v: number): number {
    const { min, max } = this.range();
    return H - M.bottom - ((v - min) / (max - min || 1)) * (H - M.top - M.bottom);
  }

  protected readonly yTicks = computed(() => {
    const { min, max } = this.range();
    return Array.from({ length: 5 }, (_, k) => {
      const v = min + ((max - min) / 4) * k;
      return { y: this.y(v), label: compact(Math.round(v * 1000) / 1000) };
    });
  });

  protected readonly observedLine = computed(() =>
    this.points().map((p, i) => `${this.x(i)},${this.y(p.value)}`).join(' '));

  protected readonly forecastLine = computed(() => {
    const t = this.target();
    const pts = this.points();
    if (!t || pts.length === 0) { return null; }
    const last = pts.length - 1;
    return `${this.x(last)},${this.y(pts[last].value)} ${this.x(last + 1)},${this.y(t.value)}`;
  });

  protected readonly band = computed(() => {
    const t = this.target();
    const pts = this.points();
    if (!t || pts.length === 0) { return null; }
    const last = pts.length - 1;
    const lx = this.x(last);
    const ly = this.y(pts[last].value);
    return `${lx},${ly} ${this.x(last + 1)},${this.y(t.high)} ${this.x(last + 1)},${this.y(t.low)}`;
  });

  protected readonly dots = computed(() => {
    const t = this.target();
    const list = this.points().map((p, i) => ({ i, x: this.x(i), y: this.y(p.value), forecast: false }));
    if (t) {
      const i = this.points().length;
      list.push({ i, x: this.x(i), y: this.y(t.value), forecast: true });
    }
    return list;
  });

  protected readonly xLabels = computed(() => {
    const labels = [...this.points().map((p) => p.label), ...(this.target() ? [this.target()!.label] : [])];
    const step = Math.max(1, Math.ceil(labels.length / 9));
    return labels.map((label, i) => ({ i, label, x: this.x(i) }))
      .filter((l) => l.i % step === 0 || l.i === labels.length - 1);
  });

  protected readonly zones = computed(() => {
    const n = this.count();
    const slot = (W - M.left - M.right) / Math.max(1, n - 1);
    return Array.from({ length: n }, (_, i) => ({ i, x: this.x(i) - slot / 2, w: slot }));
  });

  protected readonly tooltip = computed(() => {
    const i = this.hover();
    if (i === null) { return null; }
    const pts = this.points();
    const t = this.target();
    const x = this.x(i);
    const boxX = x + 210 > W - M.right ? x - 210 : x + 10;
    if (i < pts.length) {
      const p = pts[i];
      return { x, boxX, lines: [p.label, money(p.value), ...(p.detail ? [p.detail] : [])] };
    }
    if (t) {
      return { x, boxX, lines: [`Prévision ${t.label}`, money(t.value), `Intervalle : ${money(t.low)} – ${money(t.high)}`] };
    }
    return null;
  });
}
