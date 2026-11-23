import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageResult } from '../../core/models/models';
import {
  MarketSupplier, MarketSupplierRequest, MarketSupplierStatus, SUPPLIER_SOURCE_LABELS, SUPPLIER_STATUS_LABELS
} from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

function emptyForm(): MarketSupplierRequest {
  return { name: '', website: null, email: null, phone: null, city: null, notes: null };
}

/** Fournisseurs du marché : ceux détectés par la veille sont validés ou écartés par un acheteur. */
@Component({
  selector: 'app-market-suppliers',
  imports: [DatePipe, FormsModule, PageHeader, Pagination, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Fournisseurs du marché"
                     subtitle="Catalogue, fournisseurs détectés par la veille et ajouts manuels : seul un fournisseur validé reçoit des demandes de prix">
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--primary" (click)="open(null)"><app-icon name="plus" [size]="16" /> Ajouter un fournisseur</button>
      }
    </app-page-header>
    <section class="card">
      <div class="toolbar">
        <div class="chips">
          <button type="button" class="chip chip--warning" [class.is-active]="status() === 'CANDIDATE'" (click)="setStatus('CANDIDATE')">À valider</button>
          <button type="button" class="chip chip--success" [class.is-active]="status() === 'VALIDATED'" (click)="setStatus('VALIDATED')">Validés</button>
          <button type="button" class="chip" [class.is-active]="status() === 'REJECTED'" (click)="setStatus('REJECTED')">Écartés</button>
          <button type="button" class="chip" [class.is-active]="status() === null" (click)="setStatus(null)">Tous</button>
        </div>
      </div>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Fournisseur</th><th>Contact</th><th>Ville</th><th>Origine</th><th>Statut</th><th>Détecté le</th><th></th></tr></thead>
          <tbody>
            @for (s of result()?.content ?? []; track s.id) {
              <tr>
                <td><strong>{{ s.name }}</strong>
                  @if (s.website) { <br /><a class="small" [href]="s.website" target="_blank" rel="noopener noreferrer">{{ s.website }}</a> }</td>
                <td class="small">{{ s.email ?? 'E-mail inconnu' }}@if (s.phone) {<br />{{ s.phone }}}</td>
                <td>{{ s.city ?? '—' }}</td>
                <td>{{ sources[s.source] }}</td>
                <td><span class="badge" [class.badge--warning]="s.status === 'CANDIDATE'" [class.badge--success]="s.status === 'VALIDATED'"
                          [class.badge--danger]="s.status === 'REJECTED'">{{ statuses[s.status] }}</span>
                  @if (s.status === 'VALIDATED' && !s.email) { <br /><small class="muted">Ajoutez un e-mail pour les RFQ</small> }</td>
                <td>{{ s.discoveredAt | date: 'dd/MM/yyyy' }}</td>
                <td class="actions">
                  @if (auth.canManageCatalog()) {
                    @if (s.status !== 'VALIDATED') {
                      <button type="button" class="btn btn--sm btn--success" (click)="validate(s)"><app-icon name="check" [size]="14" /> Valider</button>
                    }
                    @if (s.status === 'CANDIDATE' && s.source !== 'CATALOG') {
                      <button type="button" class="btn btn--sm btn--danger-ghost" (click)="reject(s)"><app-icon name="x" [size]="14" /> Écarter</button>
                    }
                    <button type="button" class="icon-btn" (click)="open(s)" title="Modifier"><app-icon name="edit" [size]="16" /></button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="center muted">Aucun fournisseur dans cette catégorie.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>

    @if (formOpen()) {
      <app-modal [title]="editing() ? 'Modifier le fournisseur' : 'Ajouter un fournisseur'" [wide]="true" (closed)="formOpen.set(false)">
        <form class="form form--grid" (ngSubmit)="save()" #f="ngForm">
          <label class="field"><span>Raison sociale *</span><input name="name" [(ngModel)]="form.name" required maxlength="150" /></label>
          <label class="field"><span>Site web</span><input name="website" [(ngModel)]="form.website" maxlength="255" placeholder="https://" /></label>
          <label class="field"><span>E-mail (demandes de prix)</span><input type="email" name="email" [(ngModel)]="form.email" maxlength="150" email /></label>
          <label class="field"><span>Téléphone</span><input name="phone" [(ngModel)]="form.phone" maxlength="40" /></label>
          <label class="field"><span>Ville</span><input name="city" [(ngModel)]="form.city" maxlength="80" /></label>
          <label class="field field--full"><span>Notes</span><textarea name="notes" rows="2" [(ngModel)]="form.notes" maxlength="1000"></textarea></label>
          <div class="form__actions field--full">
            <button type="button" class="btn btn--ghost" (click)="formOpen.set(false)">Annuler</button>
            <button type="submit" class="btn btn--primary" [disabled]="f.invalid">Enregistrer</button>
          </div>
        </form>
      </app-modal>
    }
  `
})
export class MarketSuppliersPage {
  private readonly api = inject(ProcurementService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly statuses = SUPPLIER_STATUS_LABELS;
  protected readonly sources = SUPPLIER_SOURCE_LABELS;

  protected readonly status = signal<MarketSupplierStatus | null>('CANDIDATE');
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<MarketSupplier> | null>(null);
  protected readonly editing = signal<MarketSupplier | null>(null);
  protected readonly formOpen = signal(false);
  protected form: MarketSupplierRequest = emptyForm();

  constructor() {
    this.load();
  }

  setStatus(status: MarketSupplierStatus | null): void {
    this.status.set(status);
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.api.suppliers(this.status(), page, 20).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  open(s: MarketSupplier | null): void {
    this.editing.set(s);
    this.form = s ? { name: s.name, website: s.website, email: s.email, phone: s.phone, city: s.city, notes: s.notes }
      : emptyForm();
    this.formOpen.set(true);
  }

  save(): void {
    const current = this.editing();
    const body = { ...this.form, email: this.form.email || null, website: this.form.website || null };
    const call = current ? this.api.updateSupplier(current.id, body) : this.api.createSupplier(body);
    call.subscribe({
      next: () => {
        this.notify.success('Fournisseur enregistré.');
        this.formOpen.set(false);
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  validate(s: MarketSupplier): void {
    this.api.validateSupplier(s.id).subscribe({
      next: () => {
        this.notify.success(`${s.name} validé : il pourra recevoir des demandes de prix.`);
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  reject(s: MarketSupplier): void {
    this.api.rejectSupplier(s.id).subscribe({
      next: () => {
        this.notify.success(`${s.name} écarté.`);
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
