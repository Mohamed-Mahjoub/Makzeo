import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon } from './icon';

/** Tuile d'indicateur clé (KPI) du tableau de bord. */
@Component({
  selector: 'app-kpi-card',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kpi" [class]="'kpi kpi--' + tone()">
      <div class="kpi__icon"><app-icon [name]="icon()" [size]="20" /></div>
      <div class="kpi__body">
        <div class="kpi__label">{{ label() }}</div>
        <div class="kpi__value">{{ value() }}</div>
        @if (hint()) {
          <div class="kpi__hint">{{ hint() }}</div>
        }
      </div>
    </div>
  `
})
export class KpiCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly hint = input<string | null>(null);
  readonly icon = input('box');
  readonly tone = input<'default' | 'success' | 'warning' | 'danger' | 'info'>('default');
}
