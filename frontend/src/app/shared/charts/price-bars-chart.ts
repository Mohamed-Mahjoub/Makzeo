import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export interface PriceBar {
  id: number;
  label: string;
  sublabel?: string;
  value: number;
  display: string;
  tone: 'best' | 'identical' | 'equivalent' | 'excluded';
}

/**
 * Prix des offres en barres horizontales, avec repère vertical du dernier prix payé :
 * on voit d'un coup d'œil quelles offres sont sous le prix actuel.
 */
@Component({
  selector: 'app-price-bars-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pb-legend">
      <span><i class="t-best"></i>Recommandée</span><span><i class="t-identical"></i>Article identique</span>
      <span><i class="t-equivalent"></i>Article équivalent</span><span><i class="t-excluded"></i>Exclue</span>
      @if (reference() !== null) { <span><i class="t-ref"></i>{{ referenceLabel() }}</span> }
    </div>
    <div class="pb">
      @if (refLeft() !== null) {
        <div class="pb__ref" [style.left]="'calc(' + labelWidth + ' + (100% - ' + labelWidth + ' - 96px) * ' + refLeft()! / 100 + ')'"
             [title]="referenceLabel()"></div>
      }
      @for (b of rows(); track b.id) {
        <button type="button" class="pb__row" [class.is-selected]="b.id === selectedId()" (click)="picked.emit(b.id)">
          <span class="pb__label">{{ b.label }}@if (b.sublabel) { <small>{{ b.sublabel }}</small> }</span>
          <span class="pb__track"><span class="pb__bar" [class]="'pb__bar t-' + b.tone" [style.width.%]="b.width"></span></span>
          <strong class="pb__value">{{ b.display }}</strong>
        </button>
      }
    </div>
  `,
  styles: [`
    .pb { position: relative; display: flex; flex-direction: column; gap: 6px; }
    .pb__row { display: grid; grid-template-columns: 34% 1fr 96px; align-items: center; gap: 0; background: none; border: 0;
      padding: 4px 0; border-radius: 6px; cursor: pointer; font: inherit; text-align: left; }
    .pb__row:hover, .pb__row.is-selected { background: var(--primary-50); }
    .pb__label { padding: 0 10px 0 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: .84rem; }
    .pb__label small { display: block; color: var(--muted); font-size: .74rem; overflow: hidden; text-overflow: ellipsis; }
    .pb__track { height: 16px; background: var(--surface-2, #f8fafc); border-radius: 4px; }
    .pb__bar { display: block; height: 100%; border-radius: 4px; }
    .pb__value { text-align: right; padding-right: 6px; font-size: .84rem; }
    .pb__ref { position: absolute; top: -4px; bottom: -4px; width: 0; border-left: 2px dashed #0f172a; z-index: 1; pointer-events: none; }
    .pb-legend { display: flex; flex-wrap: wrap; gap: 14px; font-size: .78rem; color: var(--muted); margin-bottom: 10px; }
    .pb-legend i { display: inline-block; width: 12px; height: 10px; border-radius: 2px; margin-right: 5px; vertical-align: -1px; }
    .t-best { background: #16a34a; }
    .t-identical { background: #2563eb; }
    .t-equivalent { background: #7c3aed; }
    .t-excluded { background: #cbd5e1; }
    .t-ref { background: none; border-left: 2px dashed #0f172a; width: 0 !important; height: 12px !important; }
  `]
})
export class PriceBarsChart {
  readonly bars = input.required<PriceBar[]>();
  readonly reference = input<number | null>(null);
  readonly referenceLabel = input('Dernier prix payé');
  readonly selectedId = input<number | null>(null);
  readonly picked = output<number>();

  protected readonly labelWidth = '34%';

  private readonly max = computed(() =>
    Math.max(1e-9, ...this.bars().map((b) => b.value), this.reference() ?? 0) * 1.05);

  protected readonly rows = computed(() =>
    this.bars().map((b) => ({ ...b, width: Math.max(1, (b.value / this.max()) * 100) })));

  protected readonly refLeft = computed(() => {
    const r = this.reference();
    return r === null || r <= 0 ? null : (r / this.max()) * 100;
  });
}
