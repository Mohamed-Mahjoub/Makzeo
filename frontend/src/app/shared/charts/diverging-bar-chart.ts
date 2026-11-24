import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export interface DivergingItem {
  id: number;
  label: string;
  sublabel?: string;
  /** variation en % (positive : hausse) */
  value: number;
  selected?: boolean;
}

/** Barres de variation centrées sur zéro : hausses à droite (rouge), baisses à gauche (vert). Cliquables. */
@Component({
  selector: 'app-diverging-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="dv">
      @for (r of rows(); track r.id) {
        <li [class.is-selected]="r.selected">
          <button type="button" class="dv__row" (click)="picked.emit(r.id)" [title]="r.label + ' : ' + r.text">
            <span class="dv__label">{{ r.label }}@if (r.sublabel) { <small>{{ r.sublabel }}</small> }</span>
            <span class="dv__track">
              <span class="dv__axis"></span>
              <span class="dv__bar" [class.dv__bar--up]="r.value > 0" [class.dv__bar--down]="r.value < 0"
                    [style.left.%]="r.left" [style.width.%]="r.width"></span>
            </span>
            <strong class="dv__value" [class.up]="r.value > 0" [class.down]="r.value < 0">{{ r.text }}</strong>
          </button>
        </li>
      } @empty {
        <li class="muted">Aucune prédiction pour ces filtres.</li>
      }
    </ul>
  `,
  styles: [`
    .dv { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
    .dv__row { width: 100%; display: grid; grid-template-columns: minmax(140px, 34%) 1fr 64px; gap: 10px; align-items: center;
      background: none; border: 0; padding: 5px 6px; border-radius: 6px; cursor: pointer; font: inherit; text-align: left; }
    .dv__row:hover, li.is-selected .dv__row { background: var(--primary-50); }
    .dv__label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: .84rem; }
    .dv__label small { color: var(--muted); margin-left: 6px; }
    .dv__track { position: relative; height: 14px; background: var(--surface-2, #f8fafc); border-radius: 4px; }
    .dv__axis { position: absolute; left: 50%; top: -3px; bottom: -3px; width: 1px; background: #94a3b8; }
    .dv__bar { position: absolute; top: 2px; height: 10px; border-radius: 3px; }
    .dv__bar--up { background: #ef4444; }
    .dv__bar--down { background: #22c55e; }
    .dv__value { text-align: right; font-size: .84rem; }
    .up { color: var(--danger); }
    .down { color: var(--success); }
  `]
})
export class DivergingBarChart {
  readonly items = input.required<DivergingItem[]>();
  readonly picked = output<number>();

  protected readonly rows = computed(() => {
    const max = Math.max(1, ...this.items().map((i) => Math.abs(i.value)));
    return this.items().map((i) => {
      const half = (Math.abs(i.value) / max) * 50;
      return {
        ...i,
        width: Math.max(0.8, half),
        left: i.value >= 0 ? 50 : 50 - half,
        text: `${i.value > 0 ? '+' : ''}${i.value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`
      };
    });
  });
}
