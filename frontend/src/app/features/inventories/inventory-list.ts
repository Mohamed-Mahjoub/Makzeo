import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { INVENTORY_STATUS_LABELS, Inventory, InventoryStatus, PageResult } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { StockService } from '../../core/services/stock.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

/** Liste des inventaires physiques. */
@Component({
  selector: 'app-inventory-list',
  imports: [FormsModule, DatePipe, RouterLink, PageHeader, Pagination, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Inventaires" subtitle="Comptage physique et ajustement automatique des écarts">
      @if (auth.canMoveStock()) {
        <button type="button" class="btn btn--primary" (click)="formOpen.set(true)"><app-icon name="plus" [size]="16" /> Nouvel inventaire</button>
      }
    </app-page-header>
    <section class="card">
      <div class="toolbar">
        <div class="chips">
          <button type="button" class="chip" [class.is-active]="status() === null" (click)="setStatus(null)">Tous</button>
          <button type="button" class="chip chip--info" [class.is-active]="status() === 'IN_PROGRESS'" (click)="setStatus('IN_PROGRESS')">En cours</button>
          <button type="button" class="chip chip--success" [class.is-active]="status() === 'VALIDATED'" (click)="setStatus('VALIDATED')">Validés</button>
          <button type="button" class="chip" [class.is-active]="status() === 'CANCELLED'" (click)="setStatus('CANCELLED')">Annulés</button>
        </div>
      </div>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>#</th><th>Libellé</th><th>Statut</th><th>Ouvert par</th><th>Date</th><th>Clôture</th><th></th></tr></thead>
          <tbody>
            @for (i of result()?.content ?? []; track i.id) {
              <tr>
                <td class="muted">{{ i.id }}</td>
                <td><a class="link" [routerLink]="['/inventories', i.id]"><strong>{{ i.name }}</strong></a></td>
                <td><span class="badge" [class.badge--info]="i.status === 'IN_PROGRESS'" [class.badge--success]="i.status === 'VALIDATED'">
                  {{ labels[i.status] }}</span></td>
                <td>{{ i.createdBy }}</td>
                <td>{{ i.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ i.closedAt ? (i.closedAt | date: 'dd/MM/yyyy HH:mm') + ' par ' + i.closedBy : '—' }}</td>
                <td><a class="btn btn--sm btn--ghost" [routerLink]="['/inventories', i.id]">Ouvrir</a></td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="center muted">Aucun inventaire.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>

    @if (formOpen()) {
      <app-modal title="Nouvel inventaire" (closed)="formOpen.set(false)">
        <form class="form" (ngSubmit)="create()" #f="ngForm">
          <label class="field"><span>Libellé *</span>
            <input name="name" [(ngModel)]="name" required maxlength="150" placeholder="Ex. Inventaire fin de trimestre" /></label>
          <label class="field"><span>Notes</span><textarea name="notes" rows="2" [(ngModel)]="notes" maxlength="1000"></textarea></label>
          <div class="form__actions">
            <button type="button" class="btn btn--ghost" (click)="formOpen.set(false)">Annuler</button>
            <button type="submit" class="btn btn--primary" [disabled]="f.invalid">Créer</button>
          </div>
        </form>
      </app-modal>
    }
  `
})
export class InventoryListPage {
  private readonly stock = inject(StockService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly labels = INVENTORY_STATUS_LABELS;

  protected readonly status = signal<InventoryStatus | null>(null);
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<Inventory> | null>(null);
  protected readonly formOpen = signal(false);
  protected name = '';
  protected notes = '';

  constructor() {
    this.load();
  }

  setStatus(status: InventoryStatus | null): void {
    this.status.set(status);
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.stock.inventories({ status: this.status(), page, size: 20 }).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  create(): void {
    this.stock.openInventory({ name: this.name, notes: this.notes || null }).subscribe({
      next: (inv) => {
        this.notify.success('Inventaire créé.');
        this.router.navigate(['/inventories', inv.id]);
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
