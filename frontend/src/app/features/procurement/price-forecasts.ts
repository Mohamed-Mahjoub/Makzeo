import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CONFIDENCE_LABELS, ImportResult, PRICE_SOURCE_LABELS, PriceHistory, PricePrediction
} from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { Series, TimeSeriesChart } from '../../shared/charts/time-series-chart';
import { money, shortDate } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';

/** Prédictions de prix : variation attendue, échéance, confiance et justification ; historique par article. */
@Component({
  selector: 'app-price-forecasts',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, PageHeader, Modal, Icon, TimeSeriesChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Prédiction des prix"
                     subtitle="Tendance de l'historique d'achats, saisonnalité, inflation et signaux du marché relevés par l'agent">
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--ghost" (click)="importOpen.set(true)"><app-icon name="download" [size]="16" /> Importer l'historique d'achats</button>
      }
    </app-page-header>

    <section class="card">
      <div class="toolbar">
        <div class="chips">
          <button type="button" class="chip chip--danger" [class.is-active]="filter() === 'up'" (click)="filter.set('up')">Hausses</button>
          <button type="button" class="chip chip--success" [class.is-active]="filter() === 'down'" (click)="filter.set('down')">Baisses</button>
          <button type="button" class="chip" [class.is-active]="filter() === 'all'" (click)="filter.set('all')">Tous les articles</button>
        </div>
      </div>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Article</th><th class="num">Prix actuel</th><th class="num">Prix prévu</th><th class="num">Variation</th>
            <th class="num">Échéance</th><th>Confiance</th><th class="num">Historique</th><th></th></tr></thead>
          <tbody>
            @for (p of visible(); track p.productId) {
              <tr [class.is-selected]="selected()?.productId === p.productId">
                <td><strong>{{ p.productName }}</strong><br /><small class="muted">{{ p.productReference }}</small></td>
                <td class="num">{{ money(p.currentPrice) }}</td>
                <td class="num">{{ money(p.predictedPrice) }}</td>
                <td class="num"><strong [class.text-danger]="p.changePct > 0" [class.text-success]="p.changePct < 0">
                  {{ p.changePct > 0 ? '+' : '' }}{{ p.changePct | number: '1.1-1' }} %</strong></td>
                <td class="num">{{ p.horizonDays }} j</td>
                <td><span class="badge" [class.badge--success]="p.confidenceLevel === 'HIGH'" [class.badge--warning]="p.confidenceLevel === 'MEDIUM'"
                          [class.badge--danger]="p.confidenceLevel === 'LOW'">{{ confidence[p.confidenceLevel] }} · {{ p.confidence * 100 | number: '1.0-0' }} %</span></td>
                <td class="num">{{ p.sampleSize }} mois</td>
                <td class="actions"><button type="button" class="btn btn--sm btn--ghost" (click)="show(p)">Détail</button>
                  <a class="btn btn--sm btn--ghost" routerLink="/achats/devis" [queryParams]="{ productId: p.productId }">Offres</a></td>
              </tr>
            } @empty {
              <tr><td colspan="8" class="center muted">Aucune prédiction : lancez la veille ou importez l'historique d'achats.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    @if (selected(); as s) {
      <section class="card">
        <div class="card__title"><h3>{{ s.productName }} : historique et tendance</h3>
          <button type="button" class="icon-btn" (click)="selected.set(null)" aria-label="Fermer"><app-icon name="x" /></button></div>
        @if (chartLabels().length > 1) {
          <app-time-series-chart [labels]="chartLabels()" [series]="chartSeries()" ariaLabel="Historique des prix" />
        }
        <p>{{ s.rationale }}</p>
        <div class="factors">
          <div><span>Tendance mensuelle</span><strong>{{ s.monthlyTrendPct | number: '1.2-2' }} %</strong></div>
          <div><span>Saisonnalité</span><strong>{{ s.seasonalityPct | number: '1.1-1' }} %</strong></div>
          <div><span>Inflation de référence</span><strong>{{ s.inflationPct | number: '1.1-1' }} % / an</strong></div>
          <div><span>Signal du marché</span><strong>{{ s.marketSignalPct !== null ? (s.marketSignalPct | number: '1.1-1') + ' %' : 'non disponible' }}</strong></div>
          <div><span>Calculée le</span><strong>{{ s.generatedAt | date: 'dd/MM/yyyy HH:mm' }}</strong></div>
        </div>
        <div class="table-wrap">
          <table class="table table--compact">
            <thead><tr><th>Date</th><th>Origine</th><th>Fournisseur</th><th class="num">Quantité</th><th class="num">Prix HT</th></tr></thead>
            <tbody>
              @for (pt of history()?.points ?? []; track pt.id) {
                <tr><td>{{ pt.observedOn | date: 'dd/MM/yyyy' }}</td><td>{{ sources[pt.source] }}</td><td>{{ pt.supplierName ?? '—' }}</td>
                  <td class="num">{{ pt.quantity ?? '—' }}</td><td class="num">{{ money(pt.unitPrice) }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }

    @if (importOpen()) {
      <app-modal title="Importer l'historique d'achats" [wide]="true" (closed)="importOpen.set(false)">
        <p class="small">Une ligne par achat : <code>date;référence;fournisseur;quantité;prix unitaire HT</code>
          (date au format AAAA-MM-JJ ou JJ/MM/AAAA). Plus l'historique est long, plus les prédictions sont fiables.</p>
        <textarea rows="10" class="csv" [(ngModel)]="csv" placeholder="date;reference;fournisseur;quantite;prix
2025-01-15;RAM-001;Fournisseur A;20;118.500"></textarea>
        @if (importResult(); as r) {
          <p><strong>{{ r.imported }}</strong> ligne(s) importée(s).</p>
          @if (r.rejected.length > 0) {
            <ul class="small rejected">@for (line of r.rejected; track $index) { <li>{{ line }}</li> }</ul>
          }
        }
        <div class="form__actions">
          <button type="button" class="btn btn--ghost" (click)="importOpen.set(false)">Fermer</button>
          <button type="button" class="btn btn--primary" [disabled]="!csv.trim()" (click)="importCsv()">Importer</button>
        </div>
      </app-modal>
    }
  `,
  styles: [`
    .text-danger { color: var(--danger); }
    .text-success { color: var(--success); }
    .factors { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin: 12px 0 18px; }
    .factors div { display: flex; flex-direction: column; background: var(--surface-2, #f8fafc); border-radius: 8px; padding: 8px 12px; }
    .factors span { font-size: .78rem; color: var(--muted); }
    .csv { width: 100%; font-family: ui-monospace, monospace; font-size: .85rem; }
    .rejected { max-height: 160px; overflow: auto; color: var(--danger); }
    tr.is-selected { background: var(--primary-50); }
    @media (max-width: 900px) { .factors { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  `]
})
export class PriceForecastsPage {
  private readonly api = inject(ProcurementService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly confidence = CONFIDENCE_LABELS;
  protected readonly sources = PRICE_SOURCE_LABELS;
  protected readonly money = money;

  protected readonly predictions = signal<PricePrediction[]>([]);
  protected readonly filter = signal<'up' | 'down' | 'all'>('all');
  protected readonly visible = computed(() => this.predictions().filter((p) =>
    this.filter() === 'all' || (this.filter() === 'up' ? p.changePct > 0 : p.changePct < 0)));
  protected readonly selected = signal<PricePrediction | null>(null);
  protected readonly history = signal<PriceHistory | null>(null);

  protected readonly chartLabels = computed(() => {
    const h = this.history();
    const s = this.selected();
    if (!h || !s) { return []; }
    return [...h.points.map((p) => shortDate(p.observedOn)), `+${s.horizonDays} j`];
  });
  protected readonly chartSeries = computed<Series[]>(() => {
    const h = this.history();
    const s = this.selected();
    if (!h || !s) { return []; }
    return [{ name: 'Prix observé puis prévu', color: '#2563eb', kind: 'line',
      values: [...h.points.map((p) => p.unitPrice), s.predictedPrice] }];
  });

  protected readonly importOpen = signal(false);
  protected readonly importResult = signal<ImportResult | null>(null);
  protected csv = '';

  constructor() {
    this.load();
  }

  load(): void {
    this.api.predictions().subscribe({
      next: (p) => this.predictions.set(p),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  show(p: PricePrediction): void {
    this.selected.set(p);
    this.api.priceHistory(p.productId).subscribe({
      next: (h) => this.history.set(h),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  importCsv(): void {
    this.api.importPurchases(this.csv).subscribe({
      next: (r) => {
        this.importResult.set(r);
        if (r.imported > 0) {
          this.notify.success(`${r.imported} achat(s) importé(s). Les prédictions seront recalculées à la prochaine veille.`);
        }
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
