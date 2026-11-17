import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Category } from '../../core/models/models';
import { CatalogService } from '../../core/services/catalog.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ReportFormat, ReportService } from '../../core/services/report.service';
import { today } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { PageHeader } from '../../shared/page-header';

/** Génération des rapports PDF / Excel (reporting-service). */
@Component({
  selector: 'app-reports',
  imports: [FormsModule, PageHeader, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Rapports" subtitle="Exports PDF et Excel générés par le microservice de reporting" />
    <div class="grid grid--2">
      <section class="card report-card">
        <div class="report-card__icon"><app-icon name="layers" [size]="22" /></div>
        <h3>État du stock valorisé</h3>
        <p class="muted">Quantités, seuils, état (normal / sous le seuil / rupture) et valeur au prix d'achat de chaque produit.</p>
        <div class="form--grid">
          <label class="field"><span>Catégorie</span>
            <select name="cat" [(ngModel)]="categoryId">
              <option [ngValue]="null">Toutes</option>
              @for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
            </select></label>
          <label class="field field--check"><input type="checkbox" name="inactive" [(ngModel)]="includeInactive" /> Inclure les produits désactivés</label>
        </div>
        <div class="report-card__actions">
          <button type="button" class="btn btn--primary" [disabled]="busy()" (click)="get('/api/reports/stock', 'pdf', { categoryId: categoryId, includeInactive: includeInactive })">
            <app-icon name="download" [size]="16" /> PDF</button>
          <button type="button" class="btn btn--success" [disabled]="busy()" (click)="get('/api/reports/stock', 'xlsx', { categoryId: categoryId, includeInactive: includeInactive })">
            <app-icon name="download" [size]="16" /> Excel</button>
        </div>
      </section>

      <section class="card report-card">
        <div class="report-card__icon"><app-icon name="swap" [size]="22" /></div>
        <h3>Historique des mouvements</h3>
        <p class="muted">Entrées, sorties et ajustements sur une période, avec quantités avant / après et utilisateur.</p>
        <div class="form--grid">
          <label class="field"><span>Du</span><input type="date" name="from" [(ngModel)]="from" /></label>
          <label class="field"><span>Au</span><input type="date" name="to" [(ngModel)]="to" /></label>
          <label class="field"><span>Type</span>
            <select name="type" [(ngModel)]="type">
              <option [ngValue]="null">Tous</option>
              <option [ngValue]="'IN'">Entrées</option>
              <option [ngValue]="'OUT'">Sorties</option>
              <option [ngValue]="'ADJUSTMENT'">Ajustements</option>
            </select></label>
        </div>
        <div class="report-card__actions">
          <button type="button" class="btn btn--primary" [disabled]="busy()" (click)="get('/api/reports/movements', 'pdf', { from: from, to: to, type: type })">
            <app-icon name="download" [size]="16" /> PDF</button>
          <button type="button" class="btn btn--success" [disabled]="busy()" (click)="get('/api/reports/movements', 'xlsx', { from: from, to: to, type: type })">
            <app-icon name="download" [size]="16" /> Excel</button>
        </div>
      </section>

      <section class="card report-card">
        <div class="report-card__icon"><app-icon name="bell" [size]="22" /></div>
        <h3>Alertes de stock</h3>
        <p class="muted">Ruptures et seuils minimum : alertes actives ou historique complet.</p>
        <label class="field field--check"><input type="checkbox" name="activeOnly" [(ngModel)]="activeOnly" /> Alertes actives uniquement</label>
        <div class="report-card__actions">
          <button type="button" class="btn btn--primary" [disabled]="busy()" (click)="get('/api/reports/alerts', 'pdf', { activeOnly: activeOnly })">
            <app-icon name="download" [size]="16" /> PDF</button>
          <button type="button" class="btn btn--success" [disabled]="busy()" (click)="get('/api/reports/alerts', 'xlsx', { activeOnly: activeOnly })">
            <app-icon name="download" [size]="16" /> Excel</button>
        </div>
      </section>

      <section class="card report-card">
        <div class="report-card__icon"><app-icon name="clipboard" [size]="22" /></div>
        <h3>Procès-verbal d'inventaire</h3>
        <p class="muted">Disponible depuis la page de chaque inventaire (boutons « PV PDF » et « Excel »).</p>
      </section>
    </div>
  `
})
export class ReportsPage {
  private readonly reports = inject(ReportService);
  private readonly catalog = inject(CatalogService);
  private readonly notify = inject(NotificationService);

  protected readonly categories = signal<Category[]>([]);
  protected readonly busy = signal(false);
  protected categoryId: number | null = null;
  protected includeInactive = false;
  protected from: string | null = today(-30);
  protected to: string | null = today();
  protected type: string | null = null;
  protected activeOnly = true;

  constructor() {
    this.catalog.categories().subscribe((c) => this.categories.set(c));
  }

  get(path: string, format: ReportFormat, params: object): void {
    this.busy.set(true);
    this.reports.download(path, { ...params, format }).subscribe({
      next: (name) => {
        this.notify.success(`Rapport téléchargé : ${name}`);
        this.busy.set(false);
      },
      error: (err) => {
        this.notify.error(errorMessage(err));
        this.busy.set(false);
      }
    });
  }
}
