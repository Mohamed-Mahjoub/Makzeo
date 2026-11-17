import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Country, PageResult, Supplier, SupplierRequest } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { SupplierService } from '../../core/services/supplier.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

function emptyForm(): SupplierRequest {
  return { code: '', name: '', contactName: null, email: null, phone: null, address: null, city: null,
    country: null, taxId: null, notes: null };
}

/** Gestion des fournisseurs. */
@Component({
  selector: 'app-supplier-list',
  imports: [FormsModule, PageHeader, Pagination, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Fournisseurs" subtitle="Fournisseurs de tous pays : coordonnées et identifiant fiscal">
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--primary" (click)="open(null)"><app-icon name="plus" [size]="16" /> Nouveau fournisseur</button>
      }
    </app-page-header>
    <section class="card">
      <form class="toolbar" (ngSubmit)="load()">
        <div class="search"><app-icon name="search" [size]="16" />
          <input name="q" [(ngModel)]="q" placeholder="Nom, code ou ville…" /></div>
        <select class="select" name="countryFilter" [ngModel]="countryFilter" (ngModelChange)="countryFilter = $event; load()">
          <option [ngValue]="null">Tous les pays</option>
          @for (c of countries(); track c.code) { <option [ngValue]="c.code">{{ c.name }}</option> }
        </select>
        <select class="select" name="active" [ngModel]="active" (ngModelChange)="active = $event; load()">
          <option [ngValue]="null">Tous</option>
          <option [ngValue]="true">Actifs</option>
          <option [ngValue]="false">Inactifs</option>
        </select>
        <button type="submit" class="btn btn--ghost">Rechercher</button>
      </form>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Code</th><th>Raison sociale</th><th>Contact</th><th>Téléphone</th><th>Ville / pays</th><th>Identifiant fiscal</th><th>État</th><th></th></tr></thead>
          <tbody>
            @for (s of result()?.content ?? []; track s.id) {
              <tr [class.row--muted]="!s.active">
                <td><strong>{{ s.code }}</strong></td>
                <td>{{ s.name }}<br /><small class="muted">{{ s.email ?? '' }}</small></td>
                <td>{{ s.contactName ?? '—' }}</td>
                <td>{{ s.phone ?? '—' }}</td>
                <td>{{ s.city ?? '—' }}@if (s.country) { <br /><small class="muted">{{ flag(s.country) }} {{ s.countryName }}</small> }</td>
                <td class="mono">{{ s.taxId ?? '—' }}</td>
                <td><span class="badge" [class.badge--success]="s.active">{{ s.active ? 'Actif' : 'Inactif' }}</span></td>
                <td class="actions">
                  @if (auth.canManageCatalog()) {
                    <button type="button" class="icon-btn" (click)="open(s)" title="Modifier"><app-icon name="edit" [size]="16" /></button>
                    <button type="button" class="icon-btn" (click)="toggle(s)" [title]="s.active ? 'Désactiver' : 'Activer'"><app-icon name="power" [size]="16" /></button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="8" class="center muted">Aucun fournisseur.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>

    @if (formOpen()) {
      <app-modal [title]="editing() ? 'Modifier le fournisseur' : 'Nouveau fournisseur'" [wide]="true" (closed)="formOpen.set(false)">
        <form class="form form--grid" (ngSubmit)="save()" #f="ngForm">
          <label class="field"><span>Code *</span><input name="code" [(ngModel)]="form.code" required maxlength="30" pattern="[A-Za-z0-9_-]+" /></label>
          <label class="field"><span>Raison sociale *</span><input name="name" [(ngModel)]="form.name" required maxlength="150" /></label>
          <label class="field"><span>Contact</span><input name="contactName" [(ngModel)]="form.contactName" maxlength="120" /></label>
          <label class="field"><span>Email</span><input type="email" name="email" [(ngModel)]="form.email" maxlength="120" email /></label>
          <label class="field"><span>Téléphone</span><input name="phone" [(ngModel)]="form.phone" maxlength="30" /></label>
          <label class="field"><span>Pays</span>
            <select name="country" [(ngModel)]="form.country">
              <option [ngValue]="null">— Non renseigné —</option>
              @for (c of countries(); track c.code) { <option [ngValue]="c.code">{{ c.name }}</option> }
            </select></label>
          <label class="field"><span>Ville</span><input name="city" [(ngModel)]="form.city" maxlength="80" /></label>
          <label class="field field--full"><span>{{ taxIdLabel(form.country) }}</span>
            <input name="taxId" [(ngModel)]="form.taxId" maxlength="30" [pattern]="form.country === 'MA' ? '[0-9]{15}' : '[A-Za-z0-9 ./-]{3,30}'"
                   [placeholder]="taxIdPlaceholder(form.country)" /></label>
          <label class="field field--full"><span>Adresse</span><input name="address" [(ngModel)]="form.address" maxlength="255" /></label>
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
export class SupplierListPage {
  private readonly api = inject(SupplierService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  protected q = '';
  protected active: boolean | null = null;
  protected countryFilter: string | null = null;
  protected readonly countries = signal<Country[]>([]);
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<Supplier> | null>(null);
  protected readonly editing = signal<Supplier | null>(null);
  protected readonly formOpen = signal(false);
  protected form: SupplierRequest = emptyForm();

  constructor() {
    this.api.countries().subscribe((c) => this.countries.set(c));
    this.load();
  }

  /** Libellé de l'identifiant fiscal selon le pays. */
  taxIdLabel(country: string | null): string {
    switch (country) {
      case 'MA': return 'ICE (15 chiffres)';
      case 'TN': return 'Matricule fiscal';
      case 'DZ': return 'NIF (numéro d\'identification fiscale)';
      case 'FR': return 'SIRET ou n° TVA intracommunautaire';
      case 'US': return 'EIN (Employer Identification Number)';
      default: return 'Identifiant fiscal (n° TVA, registre du commerce...)';
    }
  }

  taxIdPlaceholder(country: string | null): string {
    switch (country) {
      case 'MA': return '001525836000072';
      case 'TN': return '1234567/A/M/000';
      case 'FR': return 'FR40303265045';
      default: return '';
    }
  }

  /** Drapeau emoji à partir du code pays ISO (ex. TN -> 🇹🇳). */
  flag(code: string): string {
    return code.toUpperCase().replace(/./g, (ch) => String.fromCodePoint(127397 + ch.charCodeAt(0)));
  }

  load(page = 0): void {
    this.page.set(page);
    this.api.search({ q: this.q, active: this.active, country: this.countryFilter, page, size: 15 }).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  open(s: Supplier | null): void {
    this.editing.set(s);
    this.form = s ? { code: s.code, name: s.name, contactName: s.contactName, email: s.email, phone: s.phone,
      address: s.address, city: s.city, country: s.country, taxId: s.taxId, notes: s.notes } : emptyForm();
    this.formOpen.set(true);
  }

  save(): void {
    const body: SupplierRequest = { ...this.form, email: this.form.email || null, taxId: this.form.taxId || null };
    const editing = this.editing();
    (editing ? this.api.update(editing.id, body) : this.api.create(body)).subscribe({
      next: () => {
        this.notify.success('Fournisseur enregistré.');
        this.formOpen.set(false);
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  toggle(s: Supplier): void {
    this.api.setActive(s.id, !s.active).subscribe({
      next: () => this.load(this.page()),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
