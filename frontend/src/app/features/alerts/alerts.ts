import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ALERT_STATUS_LABELS, AlertType, PageResult, StockAlert } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { StockService } from '../../core/services/stock.service';
import { Icon } from '../../shared/icon';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

/** Alertes de rupture et de seuil minimum (créées et résolues automatiquement par stock-service). */
@Component({
  selector: 'app-alerts',
  imports: [DatePipe, RouterLink, PageHeader, Pagination, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Alertes de stock" subtitle="Déclenchées automatiquement à chaque mouvement ; résolues au réapprovisionnement">
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--ghost" (click)="recompute()" title="Après modification des seuils minimum">
          <app-icon name="refresh" [size]="16" /> Recalculer</button>
      }
    </app-page-header>
    <section class="card">
      <div class="toolbar">
        <div class="chips">
          <button type="button" class="chip" [class.is-active]="activeOnly()" (click)="setActive(true)">Actives</button>
          <button type="button" class="chip" [class.is-active]="!activeOnly()" (click)="setActive(false)">Historique complet</button>
        </div>
        <div class="chips">
          <button type="button" class="chip" [class.is-active]="type() === null" (click)="setType(null)">Tous types</button>
          <button type="button" class="chip chip--danger" [class.is-active]="type() === 'RUPTURE'" (click)="setType('RUPTURE')">Rupture</button>
          <button type="button" class="chip chip--warning" [class.is-active]="type() === 'SEUIL_MIN'" (click)="setType('SEUIL_MIN')">Seuil minimum</button>
        </div>
      </div>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Date</th><th>Type</th><th>Produit</th><th class="num">Stock</th><th class="num">Seuil</th>
            <th>Statut</th><th>Traitement</th><th></th></tr></thead>
          <tbody>
            @for (a of result()?.content ?? []; track a.id) {
              <tr>
                <td>{{ a.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td><span class="badge" [class.badge--danger]="a.type === 'RUPTURE'" [class.badge--warning]="a.type === 'SEUIL_MIN'">{{ a.typeLabel }}</span></td>
                <td><strong>{{ a.productName }}</strong><br /><small class="muted">{{ a.productReference }}</small></td>
                <td class="num">{{ a.quantity }}</td>
                <td class="num">{{ a.threshold }}</td>
                <td><span class="badge" [class.badge--info]="a.status === 'ACKNOWLEDGED'" [class.badge--success]="a.status === 'RESOLVED'"
                          [class.badge--danger]="a.status === 'OPEN'">{{ statusLabels[a.status] }}</span></td>
                <td class="small muted">
                  @if (a.acknowledgedBy) { Prise en compte par {{ a.acknowledgedBy }} le {{ a.acknowledgedAt | date: 'dd/MM HH:mm' }}<br /> }
                  @if (a.resolvedAt) { Résolue le {{ a.resolvedAt | date: 'dd/MM/yyyy HH:mm' }} }
                </td>
                <td class="actions">
                  @if (a.status === 'OPEN' && auth.canMoveStock()) {
                    <button type="button" class="btn btn--sm btn--ghost" (click)="acknowledge(a)"><app-icon name="check" [size]="14" /> Prendre en compte</button>
                  }
                  @if (a.status !== 'RESOLVED' && auth.canMoveStock()) {
                    <a class="btn btn--sm btn--ghost" routerLink="/movements" [queryParams]="{ productId: a.productId, type: 'IN' }">
                      <app-icon name="arrowIn" [size]="14" /> Réapprovisionner</a>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="8" class="center muted">Aucune alerte.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>
  `
})
export class AlertsPage {
  private readonly stock = inject(StockService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly statusLabels = ALERT_STATUS_LABELS;

  protected readonly activeOnly = signal(true);
  protected readonly type = signal<AlertType | null>(null);
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<StockAlert> | null>(null);

  constructor() {
    this.load();
  }

  setActive(value: boolean): void {
    this.activeOnly.set(value);
    this.load();
  }

  setType(value: AlertType | null): void {
    this.type.set(value);
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.stock.alerts({ activeOnly: this.activeOnly(), type: this.type(), page, size: 20 }).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  acknowledge(a: StockAlert): void {
    this.stock.acknowledgeAlert(a.id).subscribe({
      next: () => {
        this.notify.success('Alerte prise en compte.');
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  recompute(): void {
    this.stock.recomputeAlerts().subscribe({
      next: (r) => {
        this.notify.success(`${r.evaluatedProducts} produit(s) réévalué(s).`);
        this.load();
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
