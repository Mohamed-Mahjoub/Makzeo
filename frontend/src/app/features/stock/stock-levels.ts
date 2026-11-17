import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PageResult, STOCK_STATUS_LABELS, StockDashboard, StockLevel, StockStatus } from '../../core/models/models';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { StockService } from '../../core/services/stock.service';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

/** Consultation des niveaux de stock par produit. */
@Component({
  selector: 'app-stock-levels',
  imports: [FormsModule, DatePipe, RouterLink, PageHeader, Pagination, KpiCard, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Niveaux de stock" subtitle="Quantités disponibles par produit (produits ayant au moins un mouvement)" />
    @if (summary(); as s) {
      <div class="kpi-grid kpi-grid--4">
        <app-kpi-card label="Produits suivis" [value]="s.trackedProducts" icon="box" />
        <app-kpi-card label="Unités en stock" [value]="s.totalQuantity" icon="layers" tone="info" />
        <app-kpi-card label="Sous le seuil" [value]="s.lowStockProducts" icon="bell" [tone]="s.lowStockProducts ? 'warning' : 'success'" />
        <app-kpi-card label="En rupture" [value]="s.outOfStockProducts" icon="alert" [tone]="s.outOfStockProducts ? 'danger' : 'success'" />
      </div>
    }
    <section class="card">
      <form class="toolbar" (ngSubmit)="load()">
        <div class="search"><app-icon name="search" [size]="16" />
          <input name="q" [(ngModel)]="q" placeholder="Référence ou désignation…" /></div>
        <div class="chips">
          <button type="button" class="chip" [class.is-active]="status() === null" (click)="setStatus(null)">Tous</button>
          <button type="button" class="chip chip--success" [class.is-active]="status() === 'OK'" (click)="setStatus('OK')">Normal</button>
          <button type="button" class="chip chip--warning" [class.is-active]="status() === 'LOW'" (click)="setStatus('LOW')">Sous le seuil</button>
          <button type="button" class="chip chip--danger" [class.is-active]="status() === 'OUT_OF_STOCK'" (click)="setStatus('OUT_OF_STOCK')">Rupture</button>
        </div>
      </form>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Référence</th><th>Désignation</th><th class="num">Quantité</th><th class="num">Seuil min.</th>
            <th>Niveau</th><th>État</th><th>Dernier mouvement</th><th></th></tr></thead>
          <tbody>
            @for (l of result()?.content ?? []; track l.productId) {
              <tr>
                <td><strong>{{ l.productReference }}</strong></td>
                <td>{{ l.productName }}</td>
                <td class="num"><strong>{{ l.quantity }}</strong></td>
                <td class="num">{{ l.minStock }}</td>
                <td><div class="gauge"><div class="gauge__fill" [style.width.%]="gauge(l)"
                     [class.gauge__fill--danger]="l.status === 'OUT_OF_STOCK'" [class.gauge__fill--warning]="l.status === 'LOW'"></div></div></td>
                <td><span class="badge" [class.badge--success]="l.status === 'OK'" [class.badge--warning]="l.status === 'LOW'"
                          [class.badge--danger]="l.status === 'OUT_OF_STOCK'">{{ labels[l.status] }}</span></td>
                <td>{{ l.lastMovementAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td><a class="link" routerLink="/movements" [queryParams]="{ productId: l.productId }">Mouvement</a></td>
              </tr>
            } @empty {
              <tr><td colspan="8" class="center muted">Aucun résultat.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>
  `
})
export class StockLevelsPage {
  private readonly stock = inject(StockService);
  private readonly notify = inject(NotificationService);

  protected readonly labels = STOCK_STATUS_LABELS;
  protected q = '';
  protected readonly status = signal<StockStatus | null>(null);
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<StockLevel> | null>(null);
  protected readonly summary = signal<StockDashboard | null>(null);

  constructor() {
    this.stock.dashboard().subscribe((d) => this.summary.set(d));
    this.load();
  }

  setStatus(status: StockStatus | null): void {
    this.status.set(status);
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.stock.levels({ q: this.q, status: this.status(), page, size: 20 }).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  /** Jauge : 100 % = 3 fois le seuil minimum. */
  gauge(l: StockLevel): number {
    const reference = Math.max(1, l.minStock * 3);
    return Math.min(100, (l.quantity / reference) * 100);
  }
}
