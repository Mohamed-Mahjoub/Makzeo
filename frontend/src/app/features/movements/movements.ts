import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Movement, MovementType, PageResult, Product, StockLevel, Supplier } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { StockService } from '../../core/services/stock.service';
import { SupplierService } from '../../core/services/supplier.service';
import { today } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';
import { ProductPicker } from '../../shared/product-picker';

/** Entrées / sorties de stock (avec scan de code-barres) et historique des mouvements. */
@Component({
  selector: 'app-movements',
  imports: [FormsModule, DatePipe, PageHeader, Pagination, Icon, ProductPicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movements.html'
})
export class MovementsPage {
  private readonly stock = inject(StockService);
  private readonly catalog = inject(CatalogService);
  private readonly suppliersApi = inject(SupplierService);
  private readonly notify = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  protected readonly auth = inject(AuthService);

  // --- saisie
  protected readonly type = signal<'IN' | 'OUT'>('IN');
  protected readonly product = signal<Product | null>(null);
  protected readonly level = signal<StockLevel | null>(null);
  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly saving = signal(false);
  protected quantity: number | null = null;
  protected supplierId: number | null = null;
  protected documentReference = '';
  protected reason = '';

  // --- historique
  protected filterType: MovementType | null = null;
  protected from: string | null = today(-30);
  protected to: string | null = today();
  protected user = '';
  protected readonly productFilter = signal<{ id: number; reference: string } | null>(null);
  protected readonly page = signal(0);
  protected readonly history = signal<PageResult<Movement> | null>(null);

  constructor() {
    this.suppliersApi.search({ active: true, size: 500 }).subscribe((p) => this.suppliers.set(p.content));
    const params = this.route.snapshot.queryParamMap;
    const productId = params.get('productId');
    if (params.get('type') === 'OUT') {
      this.type.set('OUT');
    }
    if (params.get('quantity')) {
      this.quantity = Number(params.get('quantity'));
    }
    if (productId) {
      this.catalog.product(Number(productId)).subscribe((p) => this.select(p));
    }
    this.load();
  }

  select(p: Product): void {
    this.product.set(p);
    if (this.type() === 'IN' && p.supplierId) {
      this.supplierId = p.supplierId;
    }
    this.stock.level(p.id).subscribe({ next: (l) => this.level.set(l), error: () => this.level.set(null) });
  }

  submit(): void {
    const p = this.product();
    if (!p || !this.quantity || this.quantity <= 0) {
      this.notify.error('Sélectionnez un produit et une quantité strictement positive.');
      return;
    }
    this.saving.set(true);
    this.stock.recordMovement({
      productId: p.id,
      type: this.type(),
      quantity: this.quantity,
      supplierId: this.type() === 'IN' ? this.supplierId : null,
      documentReference: this.documentReference || null,
      reason: this.reason || null
    }).subscribe({
      next: (m) => {
        this.notify.success(`${m.typeLabel} enregistrée : ${p.name} : stock ${m.quantityBefore} → ${m.quantityAfter}`);
        this.saving.set(false);
        this.quantity = null;
        this.documentReference = '';
        this.reason = '';
        this.select(p);
        this.load();
      },
      error: (err) => {
        this.notify.error(errorMessage(err));
        this.saving.set(false);
      }
    });
  }

  load(page = 0): void {
    this.page.set(page);
    this.stock.movements({
      productId: this.productFilter()?.id ?? null, type: this.filterType, from: this.from, to: this.to,
      user: this.user || null, page, size: 15
    }).subscribe({
      next: (r) => this.history.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  filterOnProduct(p: { id: number; reference: string } | null): void {
    this.productFilter.set(p);
    this.load();
  }
}
