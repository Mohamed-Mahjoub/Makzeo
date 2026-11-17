import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, switchMap } from 'rxjs';
import {
  Category, PageResult, Product, ProductRequest, STOCK_STATUS_LABELS, StockLevel, StockStatus, Supplier, UNIT_LABELS, UnitOfMeasure
} from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { StockService } from '../../core/services/stock.service';
import { SupplierService } from '../../core/services/supplier.service';
import { Barcode } from '../../shared/barcode';
import { amount, currentCurrency, priceStep } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

function emptyForm(): ProductRequest {
  return {
    reference: '', name: '', description: null, categoryId: null, barcode: null, unit: 'PIECE',
    purchasePrice: 0, salePrice: 0, minStock: 0, maxStock: null, supplierId: null
  };
}

/** Gestion des produits : recherche, création, modification, activation, code-barres. */
@Component({
  selector: 'app-product-list',
  imports: [FormsModule, PageHeader, Pagination, Modal, Icon, Barcode],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.html'
})
export class ProductListPage {
  private readonly catalog = inject(CatalogService);
  private readonly stock = inject(StockService);
  private readonly suppliersApi = inject(SupplierService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  protected readonly units = Object.keys(UNIT_LABELS) as UnitOfMeasure[];
  protected readonly unitLabels = UNIT_LABELS;
  protected readonly statusLabels = STOCK_STATUS_LABELS;
  protected readonly amount = amount;
  protected readonly priceStep = priceStep;
  protected readonly currency = currentCurrency;

  protected q = '';
  protected categoryFilter: number | null = null;
  protected activeFilter: boolean | null = true;
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<Product> | null>(null);
  protected readonly levels = signal<Partial<Record<number, StockLevel>>>({});
  protected readonly categories = signal<Category[]>([]);
  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly loading = signal(false);

  protected readonly editing = signal<Product | null>(null);
  protected readonly formOpen = signal(false);
  protected form: ProductRequest = emptyForm();
  protected readonly saving = signal(false);
  protected readonly barcodeProduct = signal<Product | null>(null);

  constructor() {
    this.catalog.categories().subscribe((c) => this.categories.set(c));
    this.suppliersApi.search({ active: true, size: 500 }).subscribe((p) => this.suppliers.set(p.content));
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.loading.set(true);
    this.catalog.products({ q: this.q, categoryId: this.categoryFilter, active: this.activeFilter, page, size: 15 })
      .pipe(switchMap((result) => {
        this.result.set(result);
        const ids = result.content.map((p) => p.id);
        return ids.length ? this.stock.levelsBatch(ids) : of([] as StockLevel[]);
      }))
      .subscribe({
        next: (levels) => {
          this.levels.set(Object.fromEntries(levels.map((l) => [l.productId, l])));
          this.loading.set(false);
        },
        error: (err) => {
          this.notify.error(errorMessage(err));
          this.loading.set(false);
        }
      });
  }

  quantityOf(p: Product): number {
    return this.levels()[p.id]?.quantity ?? 0;
  }

  /** Un produit sans mouvement n'a pas encore de ligne de stock : il est considéré en rupture. */
  statusOf(p: Product): StockStatus {
    return this.levels()[p.id]?.status ?? 'OUT_OF_STOCK';
  }

  supplierName(id: number | null): string {
    return id === null ? '—' : this.suppliers().find((s) => s.id === id)?.name ?? '#' + id;
  }

  openCreate(): void {
    this.editing.set(null);
    this.form = emptyForm();
    this.formOpen.set(true);
  }

  openEdit(p: Product): void {
    this.editing.set(p);
    this.form = {
      reference: p.reference, name: p.name, description: p.description, categoryId: p.categoryId, barcode: p.barcode,
      unit: p.unit, purchasePrice: p.purchasePrice, salePrice: p.salePrice, minStock: p.minStock, maxStock: p.maxStock,
      supplierId: p.supplierId
    };
    this.formOpen.set(true);
  }

  save(): void {
    const body: ProductRequest = { ...this.form, barcode: this.form.barcode?.trim() || null };
    const editing = this.editing();
    this.saving.set(true);
    const request = editing ? this.catalog.updateProduct(editing.id, body) : this.catalog.createProduct(body);
    request.subscribe({
      next: (p) => {
        this.notify.success(editing ? 'Produit mis à jour.' : `Produit créé (code-barres ${p.barcode}).`);
        this.formOpen.set(false);
        this.saving.set(false);
        this.load(this.page());
      },
      error: (err) => {
        this.notify.error(errorMessage(err));
        this.saving.set(false);
      }
    });
  }

  toggleActive(p: Product): void {
    this.catalog.setProductActive(p.id, !p.active).subscribe({
      next: () => {
        this.notify.success(p.active ? 'Produit désactivé.' : 'Produit réactivé.');
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  print(): void {
    window.print();
  }
}
