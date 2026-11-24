import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Barre de pagination simple (précédent / suivant + compteur). */
@Component({
  selector: 'app-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalElements() > 0) {
      <div class="pagination">
        <span class="muted">{{ totalElements() }} élément(s) · page {{ page() + 1 }} / {{ totalPages() || 1 }}</span>
        <div class="pagination__buttons">
          <button type="button" class="btn btn--ghost btn--sm" [disabled]="page() === 0"
                  (click)="pageChange.emit(page() - 1)">‹ Précédent</button>
          <button type="button" class="btn btn--ghost btn--sm" [disabled]="page() + 1 >= totalPages()"
                  (click)="pageChange.emit(page() + 1)">Suivant ›</button>
        </div>
      </div>
    }
  `
})
export class Pagination {
  readonly page = input(0);
  readonly totalPages = input(0);
  readonly totalElements = input(0);
  readonly pageChange = output<number>();
}
