import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { Icon } from './icon';

/** Tuile d'indicateur clé (KPI) ; les valeurs numériques s'affichent avec un compteur animé. */
@Component({
  selector: 'app-kpi-card',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kpi" [class]="'kpi kpi--' + tone()">
      <div class="kpi__icon"><app-icon [name]="icon()" [size]="20" /></div>
      <div class="kpi__body">
        <div class="kpi__label">{{ label() }}</div>
        <div class="kpi__value">{{ shown() }}</div>
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

  protected readonly shown = signal<string | number>('');
  private frame = 0;
  private last = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
    effect(() => {
      const target = this.value();
      cancelAnimationFrame(this.frame);
      const reduced = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (typeof target !== 'number' || !Number.isFinite(target) || reduced || typeof requestAnimationFrame === 'undefined') {
        this.shown.set(typeof target === 'number' ? target.toLocaleString('fr-FR') : target);
        if (typeof target === 'number') { this.last = target; }
        return;
      }
      const from = this.last;
      const start = performance.now();
      const duration = 700;
      const integer = Number.isInteger(target);
      const step = (now: number) => {
        const t = Math.min(1, (now - start) / duration);
        const eased = 1 - Math.pow(1 - t, 3);
        const v = from + (target - from) * eased;
        this.shown.set((integer ? Math.round(v) : Math.round(v * 10) / 10).toLocaleString('fr-FR'));
        if (t < 1) { this.frame = requestAnimationFrame(step); } else { this.last = target; }
      };
      this.frame = requestAnimationFrame(step);
    });
  }
}
