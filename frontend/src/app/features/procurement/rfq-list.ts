import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageResult, Product } from '../../core/models/models';
import { MarketSupplier, RFQ_STATUS_LABELS, Rfq, RfqStatus } from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';
import { ProductPicker } from '../../shared/product-picker';

/**
 * Demandes de prix : l'agent prépare les brouillons, le responsable des achats les relit,
 * les valide (individuellement ou par lot) et elles partent par courriel.
 */
@Component({
  selector: 'app-rfq-list',
  imports: [DatePipe, FormsModule, PageHeader, Pagination, Modal, Icon, ProductPicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Demandes de prix" subtitle="Préparées par l'agent ou un acheteur, envoyées par courriel après validation">
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--ghost" (click)="openCreate()"><app-icon name="plus" [size]="16" /> Nouvelle demande</button>
        @if (status() === 'DRAFT') {
          <button type="button" class="btn btn--primary" [disabled]="selected().size === 0" (click)="approveSelected()">
            <app-icon name="check" [size]="16" /> Valider et envoyer ({{ selected().size }})</button>
        }
      }
    </app-page-header>
    <section class="card">
      <div class="toolbar">
        <div class="chips">
          @for (s of statusFilters; track s.value) {
            <button type="button" class="chip" [class.is-active]="status() === s.value" (click)="setStatus(s.value)">{{ s.label }}</button>
          }
        </div>
      </div>
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            @if (status() === 'DRAFT' && auth.canManageCatalog()) {
              <th><input type="checkbox" [checked]="allSelected()" (change)="toggleAll()" aria-label="Tout sélectionner" /></th>
            }
            <th>Référence</th><th>Article</th><th>Fournisseur</th><th class="num">Quantité</th><th>Statut</th><th>Créée</th><th></th></tr></thead>
          <tbody>
            @for (r of result()?.content ?? []; track r.id) {
              <tr>
                @if (status() === 'DRAFT' && auth.canManageCatalog()) {
                  <td><input type="checkbox" [checked]="selected().has(r.id)" (change)="toggle(r.id)" [attr.aria-label]="'Sélectionner ' + r.reference" /></td>
                }
                <td><code>{{ r.reference }}</code></td>
                <td><strong>{{ r.productName }}</strong><br /><small class="muted">{{ r.productReference }}</small></td>
                <td>{{ r.supplierName }}<br /><small class="muted">{{ r.supplierEmail ?? 'e-mail manquant' }}</small></td>
                <td class="num">{{ r.quantity }}</td>
                <td><span class="badge" [class.badge--warning]="r.status === 'DRAFT'" [class.badge--info]="r.status === 'SENT' || r.status === 'APPROVED'"
                          [class.badge--success]="r.status === 'ANSWERED'" [class.badge--danger]="r.status === 'FAILED'">{{ labels[r.status] }}</span>
                  @if (r.failureReason) { <br /><small class="muted">{{ r.failureReason }}</small> }</td>
                <td>{{ r.createdAt | date: 'dd/MM/yyyy' }}<br /><small class="muted">{{ r.createdBy }}</small></td>
                <td class="actions">
                  <button type="button" class="btn btn--sm btn--ghost" (click)="openDetail(r)">{{ r.status === 'DRAFT' && auth.canManageCatalog() ? 'Relire' : 'Voir' }}</button>
                  @if ((r.status === 'DRAFT' || r.status === 'FAILED') && auth.canManageCatalog()) {
                    <button type="button" class="btn btn--sm btn--success" (click)="approve(r)"><app-icon name="check" [size]="14" /> Envoyer</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td [attr.colspan]="status() === 'DRAFT' ? 8 : 7" class="center muted">Aucune demande de prix.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>

    @if (detail(); as d) {
      <app-modal [title]="'Demande ' + d.reference" [wide]="true" (closed)="detail.set(null)">
        <form class="form form--grid" (ngSubmit)="saveDraft()" #f="ngForm">
          <label class="field"><span>Fournisseur</span><input [value]="d.supplierName + ' (' + (d.supplierEmail ?? 'e-mail manquant') + ')'" disabled /></label>
          <label class="field"><span>Quantité *</span>
            <input type="number" name="quantity" [(ngModel)]="edit.quantity" min="1" required [disabled]="!editable()" /></label>
          <label class="field field--full"><span>Objet *</span>
            <input name="subject" [(ngModel)]="edit.subject" required maxlength="200" [disabled]="!editable()" /></label>
          <label class="field field--full"><span>Message *</span>
            <textarea name="body" rows="14" [(ngModel)]="edit.body" required maxlength="7000" [disabled]="!editable()"></textarea></label>
          <label class="field"><span>Réponse attendue avant le</span>
            <input type="date" name="due" [(ngModel)]="edit.responseDueDate" [disabled]="!editable()" /></label>
          <div class="form__actions field--full">
            @if (editable()) {
              <button type="button" class="btn btn--danger-ghost" (click)="cancelRfq(d)">Annuler la demande</button>
              <button type="submit" class="btn btn--ghost" [disabled]="f.invalid">Enregistrer le brouillon</button>
              <button type="button" class="btn btn--primary" [disabled]="f.invalid" (click)="saveAndApprove()">
                <app-icon name="check" [size]="16" /> Valider et envoyer</button>
            } @else {
              <button type="button" class="btn btn--ghost" (click)="detail.set(null)">Fermer</button>
            }
          </div>
        </form>
      </app-modal>
    }

    @if (createOpen()) {
      <app-modal title="Nouvelle demande de prix" [wide]="true" (closed)="createOpen.set(false)">
        <div class="form form--grid">
          <div class="field field--full"><span>Article *</span>
            @if (product(); as p) {
              <div class="picked"><strong>{{ p.name }}</strong> <small class="muted">{{ p.reference }}</small>
                <button type="button" class="btn btn--sm btn--ghost" (click)="product.set(null)">Changer</button></div>
            } @else {
              <app-product-picker (picked)="product.set($event)" />
            }
          </div>
          <label class="field"><span>Fournisseur validé *</span>
            <select name="supplier" [(ngModel)]="supplierId">
              <option [ngValue]="null">— Choisir —</option>
              @for (s of suppliers(); track s.id) { <option [ngValue]="s.id">{{ s.name }}{{ s.email ? '' : ' (sans e-mail)' }}</option> }
            </select></label>
          <label class="field"><span>Quantité *</span><input type="number" name="qty" [(ngModel)]="quantity" min="1" /></label>
          <p class="small muted field--full">L'agent rédige le message ; vous pourrez le relire avant l'envoi.</p>
          <div class="form__actions field--full">
            <button type="button" class="btn btn--ghost" (click)="createOpen.set(false)">Annuler</button>
            <button type="button" class="btn btn--primary" [disabled]="!product() || !supplierId || quantity < 1" (click)="create()">Préparer le brouillon</button>
          </div>
        </div>
      </app-modal>
    }
  `,
  styles: [`.picked { display: flex; align-items: center; gap: 10px; padding: 8px 0; } code { font-size: .8rem; }`]
})
export class RfqListPage {
  private readonly api = inject(ProcurementService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly labels = RFQ_STATUS_LABELS;
  protected readonly statusFilters: { value: RfqStatus | null; label: string }[] = [
    { value: 'DRAFT', label: 'À valider' }, { value: 'SENT', label: 'Envoyées' }, { value: 'ANSWERED', label: 'Répondues' },
    { value: 'FAILED', label: 'En échec' }, { value: null, label: 'Toutes' }
  ];

  protected readonly status = signal<RfqStatus | null>('DRAFT');
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<Rfq> | null>(null);
  protected readonly selected = signal<Set<number>>(new Set());
  protected readonly allSelected = computed(() => {
    const rows = this.result()?.content ?? [];
    return rows.length > 0 && rows.every((r) => this.selected().has(r.id));
  });

  protected readonly detail = signal<Rfq | null>(null);
  protected readonly editable = computed(() => this.detail()?.status === 'DRAFT' && this.auth.canManageCatalog());
  protected edit = { quantity: 1, subject: '', body: '', responseDueDate: null as string | null };

  protected readonly createOpen = signal(false);
  protected readonly product = signal<Product | null>(null);
  protected readonly suppliers = signal<MarketSupplier[]>([]);
  protected supplierId: number | null = null;
  protected quantity = 1;

  constructor() {
    this.load();
  }

  setStatus(status: RfqStatus | null): void {
    this.status.set(status);
    this.selected.set(new Set());
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.api.rfqs({ status: this.status(), page, size: 20 }).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  toggle(id: number): void {
    const next = new Set(this.selected());
    if (next.has(id)) { next.delete(id); } else { next.add(id); }
    this.selected.set(next);
  }

  toggleAll(): void {
    const rows = this.result()?.content ?? [];
    this.selected.set(this.allSelected() ? new Set() : new Set(rows.map((r) => r.id)));
  }

  approve(r: Rfq): void {
    this.api.approveRfq(r.id).subscribe({
      next: (res) => this.reportSending([res]),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  approveSelected(): void {
    this.api.approveRfqs([...this.selected()]).subscribe({
      next: (res) => {
        this.selected.set(new Set());
        this.reportSending(res);
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  openDetail(r: Rfq): void {
    this.edit = { quantity: r.quantity, subject: r.subject, body: r.body, responseDueDate: r.responseDueDate };
    this.detail.set(r);
  }

  saveDraft(): void {
    const d = this.detail();
    if (!d) { return; }
    this.api.updateRfq(d.id, this.edit).subscribe({
      next: () => {
        this.notify.success('Brouillon enregistré.');
        this.detail.set(null);
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  saveAndApprove(): void {
    const d = this.detail();
    if (!d) { return; }
    this.api.updateRfq(d.id, this.edit).subscribe({
      next: () => {
        this.detail.set(null);
        this.approve(d);
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  cancelRfq(r: Rfq): void {
    this.api.cancelRfq(r.id).subscribe({
      next: () => {
        this.notify.success('Demande annulée.');
        this.detail.set(null);
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  openCreate(): void {
    this.product.set(null);
    this.supplierId = null;
    this.quantity = 1;
    this.api.suppliers('VALIDATED', 0, 500).subscribe({ next: (r) => this.suppliers.set(r.content) });
    this.createOpen.set(true);
  }

  create(): void {
    const p = this.product();
    if (!p || !this.supplierId) { return; }
    this.api.draftRfq(p.id, this.supplierId, this.quantity).subscribe({
      next: (r) => {
        this.createOpen.set(false);
        this.setStatus('DRAFT');
        this.openDetail(r);
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  private reportSending(results: Rfq[]): void {
    const sent = results.filter((r) => r.status === 'SENT').length;
    const failed = results.length - sent;
    if (sent > 0) { this.notify.success(`${sent} demande(s) de prix envoyée(s).`); }
    if (failed > 0) { this.notify.error(`${failed} envoi(s) en échec : voir l'onglet « En échec ».`); }
    this.load(this.page());
  }
}
