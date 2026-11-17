import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface BarItem {
  label: string;
  value: number;
  /** texte affiché à droite (valeur formatée) */
  display: string;
  sublabel?: string;
}

/** Barres horizontales (classements : top produits, valeur par catégorie...). */
@Component({
  selector: 'app-bar-list-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="bar-list">
      @for (item of rows(); track item.label) {
        <li>
          <div class="bar-list__head">
            <span class="bar-list__label" [title]="item.label">{{ item.label }}
              @if (item.sublabel) { <small>{{ item.sublabel }}</small> }
            </span>
            <strong>{{ item.display }}</strong>
          </div>
          <div class="bar-list__track"><div class="bar-list__fill" [style.width.%]="item.percent"
               [style.background]="color()"></div></div>
        </li>
      } @empty {
        <li class="muted">Aucune donnée sur la période</li>
      }
    </ul>
  `
})
export class BarListChart {
  readonly items = input.required<BarItem[]>();
  readonly color = input('#2563eb');

  protected readonly rows = computed(() => {
    const max = Math.max(1, ...this.items().map((i) => i.value));
    return this.items().map((i) => ({ ...i, percent: Math.max(1.5, (i.value / max) * 100) }));
  });
}
