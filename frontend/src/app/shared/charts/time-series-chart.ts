import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { compact, niceMax, ticks } from './chart-utils';

export interface Series {
  name: string;
  color: string;
  values: number[];
  /** 'bar' : barres groupées ; 'line' : courbe */
  kind: 'bar' | 'line';
}

const W = 760;
const H = 270;
const M = { left: 48, right: 14, top: 14, bottom: 30 };

/**
 * Graphique temporel SVG : barres groupées et/ou courbes sur un axe de dates.
 * Utilisé pour le suivi des entrées / sorties quotidiennes.
 */
@Component({
  selector: 'app-time-series-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chart-legend">
      @for (s of series(); track s.name) {
        <span><i [style.background]="s.color"></i>{{ s.name }}</span>
      }
    </div>
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" class="chart-svg" role="img" [attr.aria-label]="ariaLabel()">
      @for (t of yTicks(); track t.value) {
        <line [attr.x1]="margin.left" [attr.x2]="width - margin.right" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
        <text [attr.x]="margin.left - 8" [attr.y]="t.y + 4" text-anchor="end" class="axis-label">{{ t.label }}</text>
      }
      @for (b of bars(); track b.key) {
        <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" [attr.fill]="b.color" rx="1.5">
          <title>{{ b.title }}</title>
        </rect>
      }
      @for (l of lines(); track l.name) {
        <polyline [attr.points]="l.points" fill="none" [attr.stroke]="l.color" stroke-width="2.2"
                  stroke-linejoin="round" stroke-linecap="round" />
      }
      @for (x of xLabels(); track x.key) {
        <text [attr.x]="x.x" [attr.y]="height - 10" text-anchor="middle" class="axis-label">{{ x.label }}</text>
      }
      <line [attr.x1]="margin.left" [attr.x2]="width - margin.right" [attr.y1]="height - margin.bottom"
            [attr.y2]="height - margin.bottom" class="axis" />
    </svg>
  `
})
export class TimeSeriesChart {
  readonly labels = input.required<string[]>();
  readonly series = input.required<Series[]>();
  readonly ariaLabel = input('Graphique');

  protected readonly width = W;
  protected readonly height = H;
  protected readonly margin = M;

  private readonly max = computed(() =>
    niceMax(Math.max(1, ...this.series().flatMap((s) => s.values.map((v) => Math.abs(v))))));

  private readonly slot = computed(() => (W - M.left - M.right) / Math.max(1, this.labels().length));

  private y(value: number): number {
    const plot = H - M.top - M.bottom;
    return H - M.bottom - (Math.max(0, value) / this.max()) * plot;
  }

  protected readonly yTicks = computed(() =>
    ticks(this.max()).map((value) => ({ value, y: this.y(value), label: compact(value) })));

  protected readonly bars = computed(() => {
    const barSeries = this.series().filter((s) => s.kind === 'bar');
    if (barSeries.length === 0) {
      return [];
    }
    const slot = this.slot();
    const groupWidth = slot * 0.78;
    const w = groupWidth / barSeries.length;
    const labels = this.labels();
    return barSeries.flatMap((s, si) => s.values.map((v, i) => {
      const y = this.y(v);
      return {
        key: s.name + i,
        x: M.left + i * slot + (slot - groupWidth) / 2 + si * w,
        y,
        w: Math.max(1, w - 0.8),
        h: Math.max(0, H - M.bottom - y),
        color: s.color,
        title: `${labels[i]} — ${s.name} : ${v.toLocaleString('fr-FR')}`
      };
    }));
  });

  protected readonly lines = computed(() => {
    const slot = this.slot();
    return this.series().filter((s) => s.kind === 'line').map((s) => ({
      name: s.name,
      color: s.color,
      points: s.values.map((v, i) => `${M.left + i * slot + slot / 2},${this.y(v)}`).join(' ')
    }));
  });

  protected readonly xLabels = computed(() => {
    const labels = this.labels();
    const slot = this.slot();
    const step = Math.max(1, Math.ceil(labels.length / 10));
    return labels
      .map((label, i) => ({ key: i, label, x: M.left + i * slot + slot / 2 }))
      .filter((item) => item.key % step === 0 || item.key === labels.length - 1);
  });
}
