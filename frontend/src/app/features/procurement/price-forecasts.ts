import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CONFIDENCE_LABELS, ConfidenceLevel, ImportResult, PRICE_SOURCE_LABELS, PriceHistory, PricePrediction
} from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { DivergingBarChart, DivergingItem } from '../../shared/charts/diverging-bar-chart';
import { ForecastChart, ForecastPoint, ForecastTarget } from '../../shared/charts/forecast-chart';
import { money } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';
import { AiReportButton } from './ai-report-button';

type Trend = 'all' | 'up' | 'down' | 'stable';
type SortKey = 'change-desc' | 'change-asc' | 'confidence' | 'name';

const PAGE_SIZE = 10;
/** seuil d'une variation significative (identique au module : 3 %) */
const SIGNIFICANT = 3;

/** Prédictions de prix : recherche, filtres, graphiques interactifs et détail par article. */
@Component({
  selector: 'app-price-forecasts',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, PageHeader, Modal, Icon, KpiCard, Pagination,
    ForecastChart, DivergingBarChart, AiReportButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Prédiction des prix"
                     subtitle="Tendance de l'historique d'achats, saisonnalité, inflation et signaux du marché relevés par l'agent">
      <app-ai-report-button />
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--ghost" (click)="importOpen.set(true)"><app-icon name="download" [size]="16" /> Importer l'historique</button>
      }
    </app-page-header>

    <div class="kpi-grid kpi-grid--4">
      <app-kpi-card label="Articles suivis" [value]="predictions().length" icon="tag" [hint]="highShare() + ' % à confiance élevée'" />
      <app-kpi-card label="Hausses prévues" [value]="stats().up" icon="trend" tone="danger"
                    [hint]="'≥ +' + significant + ' % à ' + horizonLabel()" />
      <app-kpi-card label="Baisses prévues" [value]="stats().down" icon="trend" tone="success" [hint]="'≤ -' + significant + ' %'" />
      <app-kpi-card label="Variation moyenne" [value]="(stats().avg > 0 ? '+' : '') + (stats().avg | number: '1.1-1') + ' %'" icon="coins"
                    tone="info" hint="sur les articles affichés" />
    </div>

    <section class="card">
      <div class="toolbar">
        <div class="search"><app-icon name="search" [size]="16" />
          <input type="search" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" placeholder="Rechercher un article ou une référence…"
                 aria-label="Rechercher un article" /></div>
        <div class="chips">
          <button type="button" class="chip" [class.is-active]="trend() === 'all'" (click)="setTrend('all')">Tous ({{ predictions().length }})</button>
          <button type="button" class="chip chip--danger" [class.is-active]="trend() === 'up'" (click)="setTrend('up')">Hausses ({{ stats().up }})</button>
          <button type="button" class="chip chip--success" [class.is-active]="trend() === 'down'" (click)="setTrend('down')">Baisses ({{ stats().down }})</button>
          <button type="button" class="chip" [class.is-active]="trend() === 'stable'" (click)="setTrend('stable')">Stables</button>
        </div>
        <select [ngModel]="level()" (ngModelChange)="level.set($event); page.set(0)" aria-label="Confiance">
          <option [ngValue]="null">Toutes confiances</option>
          <option ngValue="HIGH">Confiance élevée</option>
          <option ngValue="MEDIUM">Confiance moyenne</option>
          <option ngValue="LOW">Confiance faible</option>
        </select>
        <select [ngModel]="sort()" (ngModelChange)="sort.set($event)" aria-label="Trier">
          <option ngValue="change-desc">Plus fortes hausses</option>
          <option ngValue="change-asc">Plus fortes baisses</option>
          <option ngValue="confidence">Confiance</option>
          <option ngValue="name">Nom</option>
        </select>
      </div>

      <div class="grid grid--3-1 split">
        <div>
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Article</th><th class="num">Prix actuel</th><th class="num">Prix prévu</th><th class="num">Variation</th>
                <th>Confiance</th><th class="num">Hist.</th><th></th></tr></thead>
              <tbody>
                @for (p of pageItems(); track p.productId) {
                  <tr class="clickable" [class.is-selected]="selected()?.productId === p.productId" (click)="show(p)">
                    <td><strong>{{ p.productName }}</strong><br /><small class="muted">{{ p.productReference }}</small></td>
                    <td class="num">{{ money(p.currentPrice) }}</td>
                    <td class="num">{{ money(p.predictedPrice) }}</td>
                    <td class="num"><strong [class.text-danger]="p.changePct > 0" [class.text-success]="p.changePct < 0">
                      {{ p.changePct > 0 ? '+' : '' }}{{ p.changePct | number: '1.1-1' }} %</strong><br /><small class="muted">{{ p.horizonDays }} j</small></td>
                    <td><span class="conf"><span class="conf__bar"><span [style.width.%]="p.confidence * 100"
                          [class]="'conf__fill conf--' + p.confidenceLevel"></span></span>
                      <small>{{ confidence[p.confidenceLevel] }}</small></span></td>
                    <td class="num">{{ p.sampleSize }} m</td>
                    <td class="actions"><a class="btn btn--sm btn--ghost" routerLink="/achats/devis" [queryParams]="{ productId: p.productId }"
                         (click)="$event.stopPropagation()">Offres</a></td>
                  </tr>
                } @empty {
                  <tr><td colspan="7" class="center muted">
                    @if (predictions().length === 0) { Aucune prédiction : lancez la veille ou importez l'historique d'achats. }
                    @else { Aucun article ne correspond à la recherche. }</td></tr>
                }
              </tbody>
            </table>
          </div>
          <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="visible().length" (pageChange)="page.set($event)" />
        </div>
        <div>
          <h4 class="side-title">Variations prévues <small class="muted">(cliquez sur un article)</small></h4>
          <app-diverging-bar-chart [items]="chartItems()" (picked)="showById($event)" />
        </div>
      </div>
    </section>

    @if (selected(); as s) {
      <section class="card" id="forecast-detail">
        <div class="card__title">
          <div>
            <h3>{{ s.productName }}</h3>
            <small class="muted">{{ s.productReference }} · prédiction calculée le {{ s.generatedAt | date: 'dd/MM/yyyy HH:mm' }}</small>
          </div>
          <div class="nav">
            <button type="button" class="btn btn--sm btn--ghost" [disabled]="position() <= 0" (click)="step(-1)" aria-label="Article précédent">‹ Précédent</button>
            <span class="muted small">{{ position() + 1 }} / {{ visible().length }}</span>
            <button type="button" class="btn btn--sm btn--ghost" [disabled]="position() < 0 || position() >= visible().length - 1" (click)="step(1)"
                    aria-label="Article suivant">Suivant ›</button>
            <button type="button" class="icon-btn" (click)="selected.set(null)" aria-label="Fermer"><app-icon name="x" /></button>
          </div>
        </div>

        <div class="headline">
          <div><span>Prix actuel</span><strong>{{ money(s.currentPrice) }}</strong></div>
          <div class="arrow" [class.text-danger]="s.changePct > 0" [class.text-success]="s.changePct < 0">
            {{ s.changePct > 0 ? '↗' : s.changePct < 0 ? '↘' : '→' }} {{ s.changePct > 0 ? '+' : '' }}{{ s.changePct | number: '1.1-1' }} %</div>
          <div><span>Prix prévu à {{ s.horizonDays }} jours</span><strong>{{ money(s.predictedPrice) }}</strong></div>
          <div><span>Confiance</span><strong>{{ confidence[s.confidenceLevel] }} ({{ s.confidence * 100 | number: '1.0-0' }} %)</strong></div>
        </div>

        @if (chartPoints().length > 1) {
          <app-forecast-chart [points]="chartPoints()" [target]="chartTarget()" />
        } @else {
          <p class="muted">Historique insuffisant pour tracer la courbe.</p>
        }

        <p class="rationale">{{ s.rationale }}</p>
        <div class="factors">
          <div><span>Tendance mensuelle</span><strong>{{ s.monthlyTrendPct > 0 ? '+' : '' }}{{ s.monthlyTrendPct | number: '1.2-2' }} %</strong></div>
          <div><span>Saisonnalité</span><strong>{{ s.seasonalityPct | number: '1.1-1' }} %</strong></div>
          <div><span>Inflation de référence</span><strong>{{ s.inflationPct | number: '1.1-1' }} % / an</strong></div>
          <div><span>Signal du marché</span><strong>{{ s.marketSignalPct !== null ? (s.marketSignalPct | number: '1.1-1') + ' %' : 'non disponible' }}</strong></div>
          <div><span>Historique</span><strong>{{ s.sampleSize }} mois</strong></div>
        </div>

        <div class="detail-actions">
          <a class="btn btn--sm btn--primary" routerLink="/achats/devis" [queryParams]="{ productId: s.productId }">Comparer les offres</a>
          <a class="btn btn--sm btn--ghost" routerLink="/achats/recommandations">Voir les recommandations</a>
          <button type="button" class="btn btn--sm btn--ghost" (click)="showData.set(!showData())">
            {{ showData() ? 'Masquer' : 'Afficher' }} les données ({{ history()?.points?.length ?? 0 }})</button>
        </div>

        @if (showData()) {
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
        }
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
    .split { align-items: start; margin-bottom: 0; }
    .side-title { margin: 0 0 10px; font-size: .95rem; }
    tr.clickable { cursor: pointer; }
    tr.clickable:hover { background: var(--surface-2, #f8fafc); }
    tr.is-selected, tr.is-selected:hover { background: var(--primary-50); }
    .conf { display: flex; flex-direction: column; gap: 3px; min-width: 90px; }
    .conf__bar { height: 6px; background: #eef2f7; border-radius: 99px; overflow: hidden; }
    .conf__fill { display: block; height: 100%; }
    .conf--HIGH { background: var(--success); }
    .conf--MEDIUM { background: var(--warning); }
    .conf--LOW { background: var(--danger); }
    .nav { display: flex; align-items: center; gap: 8px; }
    .headline { display: grid; grid-template-columns: 1fr auto 1fr 1fr; gap: 16px; align-items: center; margin: 4px 0 14px;
      background: var(--surface-2, #f8fafc); border-radius: 10px; padding: 12px 16px; }
    .headline div { display: flex; flex-direction: column; }
    .headline span { font-size: .78rem; color: var(--muted); }
    .headline strong { font-size: 1.15rem; }
    .headline .arrow { font-size: 1.4rem; font-weight: 700; flex-direction: row; }
    .rationale { margin: 12px 0; }
    .factors { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin: 12px 0 14px; }
    .factors div { display: flex; flex-direction: column; background: var(--surface-2, #f8fafc); border-radius: 8px; padding: 8px 12px; }
    .factors span { font-size: .78rem; color: var(--muted); }
    .detail-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
    .csv { width: 100%; font-family: ui-monospace, monospace; font-size: .85rem; }
    .rejected { max-height: 160px; overflow: auto; color: var(--danger); }
    @media (max-width: 1100px) { .split { grid-template-columns: 1fr; } }
    @media (max-width: 900px) { .factors { grid-template-columns: repeat(2, minmax(0, 1fr)); } .headline { grid-template-columns: 1fr 1fr; } }
  `]
})
export class PriceForecastsPage {
  private readonly api = inject(ProcurementService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly confidence = CONFIDENCE_LABELS;
  protected readonly sources = PRICE_SOURCE_LABELS;
  protected readonly money = money;
  protected readonly significant = SIGNIFICANT;

  protected readonly predictions = signal<PricePrediction[]>([]);
  protected readonly q = signal('');
  protected readonly trend = signal<Trend>('all');
  protected readonly level = signal<ConfidenceLevel | null>(null);
  protected readonly sort = signal<SortKey>('change-desc');
  protected readonly page = signal(0);

  protected readonly selected = signal<PricePrediction | null>(null);
  protected readonly history = signal<PriceHistory | null>(null);
  protected readonly showData = signal(false);

  protected readonly visible = computed(() => {
    const q = this.q().trim().toLowerCase();
    const list = this.predictions().filter((p) => {
      if (q && !(p.productName.toLowerCase().includes(q) || p.productReference.toLowerCase().includes(q))) { return false; }
      if (this.level() && p.confidenceLevel !== this.level()) { return false; }
      switch (this.trend()) {
        case 'up': return p.changePct >= SIGNIFICANT;
        case 'down': return p.changePct <= -SIGNIFICANT;
        case 'stable': return Math.abs(p.changePct) < SIGNIFICANT;
        default: return true;
      }
    });
    const order = { HIGH: 0, MEDIUM: 1, LOW: 2 } as const;
    return [...list].sort((a, b) => {
      switch (this.sort()) {
        case 'change-asc': return a.changePct - b.changePct;
        case 'confidence': return order[a.confidenceLevel] - order[b.confidenceLevel] || b.confidence - a.confidence;
        case 'name': return a.productName.localeCompare(b.productName, 'fr');
        default: return b.changePct - a.changePct;
      }
    });
  });

  protected readonly totalPages = computed(() => Math.ceil(this.visible().length / PAGE_SIZE));
  protected readonly pageItems = computed(() => this.visible().slice(this.page() * PAGE_SIZE, (this.page() + 1) * PAGE_SIZE));

  protected readonly stats = computed(() => {
    const all = this.predictions();
    const shown = this.visible();
    return {
      up: all.filter((p) => p.changePct >= SIGNIFICANT).length,
      down: all.filter((p) => p.changePct <= -SIGNIFICANT).length,
      avg: shown.length ? shown.reduce((s, p) => s + p.changePct, 0) / shown.length : 0
    };
  });
  protected readonly highShare = computed(() => {
    const all = this.predictions();
    return all.length ? Math.round((all.filter((p) => p.confidenceLevel === 'HIGH').length / all.length) * 100) : 0;
  });
  protected readonly horizonLabel = computed(() => `${this.predictions()[0]?.horizonDays ?? 90} jours`);

  /** 12 plus fortes variations (en valeur absolue) parmi les articles filtrés */
  protected readonly chartItems = computed<DivergingItem[]>(() =>
    [...this.visible()].sort((a, b) => Math.abs(b.changePct) - Math.abs(a.changePct)).slice(0, 12)
      .map((p) => ({ id: p.productId, label: p.productName, value: p.changePct, selected: this.selected()?.productId === p.productId })));

  protected readonly position = computed(() => {
    const s = this.selected();
    return s ? this.visible().findIndex((p) => p.productId === s.productId) : -1;
  });

  /** Moyennes mensuelles des prix payés (les devis ne font pas partie de la série). */
  protected readonly chartPoints = computed<ForecastPoint[]>(() => {
    const h = this.history();
    if (!h) { return []; }
    const months = new Map<string, { sum: number; n: number; suppliers: Set<string> }>();
    for (const p of h.points) {
      if (p.source === 'QUOTE') { continue; }
      const key = p.observedOn.slice(0, 7);
      const m = months.get(key) ?? { sum: 0, n: 0, suppliers: new Set<string>() };
      m.sum += p.unitPrice;
      m.n += 1;
      if (p.supplierName) { m.suppliers.add(p.supplierName); }
      months.set(key, m);
    }
    return [...months.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([key, m]) => ({
      label: `${key.slice(5, 7)}/${key.slice(0, 4)}`,
      value: Math.round((m.sum / m.n) * 1000) / 1000,
      detail: `${m.n} prix${m.suppliers.size ? ' · ' + [...m.suppliers].slice(0, 2).join(', ') : ''}`
    }));
  });

  protected readonly chartTarget = computed<ForecastTarget | null>(() => {
    const s = this.selected();
    if (!s) { return null; }
    const d = new Date();
    d.setDate(d.getDate() + s.horizonDays);
    // intervalle indicatif : plus la confiance est faible, plus il est large
    const half = s.predictedPrice * (1 - s.confidence) * 0.25;
    return {
      label: `${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`,
      value: s.predictedPrice, low: s.predictedPrice - half, high: s.predictedPrice + half
    };
  });

  protected readonly importOpen = signal(false);
  protected readonly importResult = signal<ImportResult | null>(null);
  protected csv = '';

  constructor() {
    this.load();
  }

  load(): void {
    this.api.predictions().subscribe({
      next: (p) => {
        this.predictions.set(p);
        if (!this.selected() && this.visible().length > 0) {
          this.show(this.visible()[0], false);
        }
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  setTrend(t: Trend): void {
    this.trend.set(t);
    this.page.set(0);
  }

  showById(id: number): void {
    const p = this.predictions().find((x) => x.productId === id);
    if (p) { this.show(p); }
  }

  show(p: PricePrediction, scroll = true): void {
    this.selected.set(p);
    this.history.set(null);
    this.showData.set(false);
    const index = this.visible().findIndex((x) => x.productId === p.productId);
    if (index >= 0) { this.page.set(Math.floor(index / PAGE_SIZE)); }
    this.api.priceHistory(p.productId).subscribe({
      next: (h) => {
        this.history.set(h);
        if (scroll) {
          setTimeout(() => document.getElementById('forecast-detail')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
        }
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  step(delta: number): void {
    const next = this.visible()[this.position() + delta];
    if (next) { this.show(next, false); }
  }

  importCsv(): void {
    this.api.importPurchases(this.csv).subscribe({
      next: (r) => {
        this.importResult.set(r);
        if (r.imported > 0) {
          this.notify.success(`${r.imported} achat(s) importé(s) : prédictions recalculées.`);
          this.load();
        }
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
