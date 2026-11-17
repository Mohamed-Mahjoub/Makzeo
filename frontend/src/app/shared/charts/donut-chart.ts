import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface Slice {
  label: string;
  value: number;
  color: string;
}

const R = 60;
const C = 2 * Math.PI * R;

/** Graphique en anneau (répartition) avec total au centre et légende. */
@Component({
  selector: 'app-donut-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="donut">
      <svg viewBox="0 0 160 160" class="donut__svg" role="img" [attr.aria-label]="centerLabel()">
        <circle cx="80" cy="80" [attr.r]="radius" fill="none" stroke="#eef2f7" stroke-width="22" />
        @for (s of arcs(); track s.label) {
          <circle cx="80" cy="80" [attr.r]="radius" fill="none" [attr.stroke]="s.color" stroke-width="22"
                  [attr.stroke-dasharray]="s.dash" [attr.stroke-dashoffset]="s.offset"
                  transform="rotate(-90 80 80)">
            <title>{{ s.label }} : {{ s.value }} ({{ s.percent }} %)</title>
          </circle>
        }
        <text x="80" y="78" text-anchor="middle" class="donut__total">{{ total() }}</text>
        <text x="80" y="96" text-anchor="middle" class="donut__caption">{{ centerLabel() }}</text>
      </svg>
      <ul class="donut__legend">
        @for (s of arcs(); track s.label) {
          <li><i [style.background]="s.color"></i><span>{{ s.label }}</span><strong>{{ s.value }}</strong>
            <em>{{ s.percent }} %</em></li>
        }
      </ul>
    </div>
  `
})
export class DonutChart {
  readonly slices = input.required<Slice[]>();
  readonly centerLabel = input('Total');

  protected readonly radius = R;
  protected readonly total = computed(() => this.slices().reduce((sum, s) => sum + s.value, 0));

  protected readonly arcs = computed(() => {
    const total = this.total() || 1;
    let offset = 0;
    return this.slices().map((s) => {
      const length = (s.value / total) * C;
      const arc = {
        ...s,
        dash: `${length} ${C - length}`,
        offset: -offset,
        percent: Math.round((s.value / total) * 100)
      };
      offset += length;
      return arc;
    });
  });
}
