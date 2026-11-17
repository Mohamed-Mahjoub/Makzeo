import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Product } from '../core/models/models';
import { CatalogService } from '../core/services/catalog.service';
import { Icon } from './icon';

/**
 * Sélection d'un produit par scan de code-barres (la douchette envoie le code puis "Entrée")
 * ou par recherche textuelle (nom / référence).
 */
@Component({
  selector: 'app-product-picker',
  imports: [FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="picker">
      <div class="search search--lg">
        <app-icon name="barcode" [size]="18" />
        <input [ngModel]="term" (ngModelChange)="onType($event)" (keydown.enter)="onEnter($event)"
               [placeholder]="placeholder()" autocomplete="off" />
      </div>
      @if (message()) {
        <div class="picker__message">{{ message() }}</div>
      }
      @if (suggestions().length > 0) {
        <ul class="picker__list">
          @for (p of suggestions(); track p.id) {
            <li><button type="button" (click)="choose(p)">
              <strong>{{ p.name }}</strong><small>{{ p.reference }} · {{ p.barcode }}</small>
            </button></li>
          }
        </ul>
      }
    </div>
  `
})
export class ProductPicker {
  private readonly catalog = inject(CatalogService);

  readonly placeholder = input('Scanner un code-barres ou rechercher un produit…');
  readonly picked = output<Product>();

  protected term = '';
  protected readonly suggestions = signal<Product[]>([]);
  protected readonly message = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;

  onType(value: string): void {
    this.term = value;
    this.message.set(null);
    if (this.timer) {
      clearTimeout(this.timer);
    }
    if (value.trim().length < 2) {
      this.suggestions.set([]);
      return;
    }
    this.timer = setTimeout(() => this.search(value), 250);
  }

  onEnter(event: Event): void {
    event.preventDefault();
    const code = this.term.trim();
    if (!code) {
      return;
    }
    // Un scan de douchette se termine par "Entrée" : on tente d'abord une correspondance exacte du code-barres
    this.catalog.productByBarcode(code).subscribe({
      next: (p) => this.choose(p),
      error: () => this.search(code)
    });
  }

  choose(p: Product): void {
    this.picked.emit(p);
    this.term = '';
    this.suggestions.set([]);
    this.message.set(null);
  }

  private search(term: string): void {
    this.catalog.products({ q: term.trim(), active: true, size: 8 }).subscribe((page) => {
      this.suggestions.set(page.content);
      this.message.set(page.content.length === 0 ? 'Aucun produit ne correspond à « ' + term + ' »' : null);
    });
  }
}
