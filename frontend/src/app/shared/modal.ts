import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icon } from './icon';

/** Fenêtre modale générique (le contenu est projeté). */
@Component({
  selector: 'app-modal',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-backdrop" (click)="closed.emit()"></div>
    <div class="modal" [class.modal--wide]="wide()" role="dialog" aria-modal="true">
      <header class="modal__header">
        <h2>{{ title() }}</h2>
        <button type="button" class="icon-btn" (click)="closed.emit()" aria-label="Fermer">
          <app-icon name="x" />
        </button>
      </header>
      <div class="modal__body">
        <ng-content />
      </div>
    </div>
  `
})
export class Modal {
  readonly title = input.required<string>();
  readonly wide = input(false);
  readonly closed = output<void>();
}
