import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  CONFIDENCE_LABELS, PricePrediction, ProcurementOverview, SavingsSummary, WatchRun
} from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { AiReportButton } from './ai-report-button';
import { BarItem, BarListChart } from '../../shared/charts/bar-list-chart';
import { moneyRounded } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';
import { PageHeader } from '../../shared/page-header';

/** Tableau de bord des achats : économies, opportunités, veille du marché, prix à surveiller. */
@Component({
  selector: 'app-procurement-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink, PageHeader, KpiCard, BarListChart, Icon, AiReportButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Achats intelligents"
                     [subtitle]="'Veille mensuelle du marché (' + (overview()?.marketName ?? 'Tunisie') + (overview()?.aiProvider ? ', via ' + overview()?.aiProvider : '') + '), demandes de prix, prédiction des prix et achats anticipés'">
      <app-ai-report-button [primary]="!auth.canManageCatalog()" />
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--primary" (click)="runWatch()" [disabled]="running()">
          <app-icon name="refresh" [size]="16" /> {{ running() ? 'Veille en cours…' : 'Lancer la veille' }}</button>
      }
    </app-page-header>

    @if (overview(); as o) {
      @if (!o.aiEnabled || !o.mailEnabled) {
        <div class="notice">
          @if (!o.aiEnabled) { <p><strong>Veille web non configurée :</strong> renseignez SERPER_API_KEY (et TAVILY_API_KEY)
            ou AI_API_KEY pour rechercher des fournisseurs sur le web. Comparaisons, prédictions statistiques et recommandations restent disponibles.</p> }
          @if (!o.mailEnabled) { <p><strong>Serveur de messagerie non configuré :</strong> les demandes de prix validées ne peuvent pas être envoyées.</p> }
        </div>
      }

      <div class="kpi-grid">
        <app-kpi-card label="Économies potentielles" [value]="money(o.potentialSavings)" icon="coins" tone="success"
                      [hint]="o.openOpportunities + ' opportunité(s) à décider'" />
        <app-kpi-card label="Économies réalisées" [value]="money(o.realizedSavings)" icon="check" tone="info"
                      hint="Recommandations acceptées" />
        <app-kpi-card label="Hausses prévues" [value]="o.risingPrices" icon="trend" [tone]="o.risingPrices > 0 ? 'warning' : 'default'"
                      [hint]="o.fallingPrices + ' baisse(s) prévue(s)'" />
        <app-kpi-card label="RFQ à valider" [value]="o.draftRfqs" icon="file" [tone]="o.draftRfqs > 0 ? 'warning' : 'default'"
                      [hint]="o.sentRfqs + ' envoyée(s), en attente de réponse'" />
        <app-kpi-card label="Fournisseurs à valider" [value]="o.candidateSuppliers" icon="truck"
                      [hint]="o.validatedSuppliers + ' validé(s)'" />
        <app-kpi-card label="Dernière veille" [value]="lastRunLabel(o.lastRun)" icon="clock"
                      [tone]="o.lastRun?.status === 'FAILED' ? 'danger' : 'default'"
                      [hint]="o.lastRun ? (o.lastRun.startedAt | date: 'dd/MM/yyyy HH:mm') : 'Jamais lancée'" />
      </div>

      @if (o.lastRun; as r) {
        <section class="card">
          <div class="card__title"><h3>Veille {{ r.period }}</h3>
            <span class="badge" [class.badge--info]="r.status === 'RUNNING'" [class.badge--success]="r.status === 'COMPLETED'"
                  [class.badge--danger]="r.status === 'FAILED'">{{ runStatus(r) }}</span></div>
          <div class="stats-row">
            <div><strong>{{ r.productsScanned }}</strong><span>articles analysés</span></div>
            <div><strong>{{ r.suppliersDiscovered }}</strong><span>nouveaux fournisseurs</span></div>
            <div><strong>{{ r.leadsFound }}</strong><span>offres repérées</span></div>
            <div><strong>{{ r.rfqsDrafted }}</strong><span>demandes de prix préparées</span></div>
            <div><strong>{{ r.recommendations }}</strong><span>recommandations</span></div>
          </div>
          @if (r.message) { <p class="small muted">{{ r.message }}</p> }
        </section>
      }
    }

    <div class="grid grid--2">
      <section class="card">
        <div class="card__title"><h3>Économies par fournisseur</h3>
          <a class="btn btn--sm btn--ghost" routerLink="/achats/recommandations">Recommandations</a></div>
        @if (bySupplier().length > 0) {
          <app-bar-list-chart [items]="bySupplier()" [color]="'#16a34a'" />
        } @else {
          <p class="muted center">Aucune économie identifiée pour l'instant.</p>
        }
      </section>
      <section class="card">
        <div class="card__title"><h3>Économies par article</h3></div>
        @if (byProduct().length > 0) {
          <app-bar-list-chart [items]="byProduct()" [color]="'#0d9488'" />
        } @else {
          <p class="muted center">Aucune économie identifiée pour l'instant.</p>
        }
      </section>
    </div>

    <section class="card">
      <div class="card__title"><h3>Prix à surveiller</h3>
        <a class="btn btn--sm btn--ghost" routerLink="/achats/prix">Toutes les prédictions</a></div>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Article</th><th class="num">Prix actuel</th><th class="num">Prix prévu</th><th class="num">Variation</th>
            <th class="num">Échéance</th><th>Confiance</th></tr></thead>
          <tbody>
            @for (p of watchlist(); track p.productId) {
              <tr>
                <td><strong>{{ p.productName }}</strong><br /><small class="muted">{{ p.productReference }}</small></td>
                <td class="num">{{ p.currentPrice | number: '1.3-3' }}</td>
                <td class="num">{{ p.predictedPrice | number: '1.3-3' }}</td>
                <td class="num"><span [class.text-danger]="p.changePct > 0" [class.text-success]="p.changePct < 0">
                  {{ p.changePct > 0 ? '+' : '' }}{{ p.changePct | number: '1.1-1' }} %</span></td>
                <td class="num">{{ p.horizonDays }} j</td>
                <td><span class="badge" [class.badge--success]="p.confidenceLevel === 'HIGH'" [class.badge--warning]="p.confidenceLevel === 'MEDIUM'"
                          [class.badge--danger]="p.confidenceLevel === 'LOW'">{{ confidence[p.confidenceLevel] }} ({{ p.confidence * 100 | number: '1.0-0' }} %)</span></td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="center muted">Aucune prédiction : lancez la veille ou importez l'historique d'achats.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: [`
    .notice { background: var(--warning-50); border: 1px solid var(--warning-50); border-radius: 10px; padding: 10px 16px; margin-bottom: 18px; }
    .notice p { margin: 4px 0; }
    .stats-row { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
    .stats-row div { display: flex; flex-direction: column; }
    .stats-row strong { font-size: 1.4rem; }
    .stats-row span { color: var(--muted); font-size: .82rem; }
    .text-danger { color: var(--danger); font-weight: 600; }
    .text-success { color: var(--success); font-weight: 600; }
    @media (max-width: 900px) { .stats-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  `]
})
export class ProcurementDashboardPage implements OnDestroy {
  private readonly api = inject(ProcurementService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly confidence = CONFIDENCE_LABELS;
  protected readonly money = moneyRounded;

  protected readonly overview = signal<ProcurementOverview | null>(null);
  protected readonly savings = signal<SavingsSummary | null>(null);
  protected readonly predictions = signal<PricePrediction[]>([]);
  protected readonly running = computed(() => this.overview()?.lastRun?.status === 'RUNNING');
  private poll: ReturnType<typeof setInterval> | null = null;

  protected readonly bySupplier = computed<BarItem[]>(() => (this.savings()?.bySupplier ?? []).slice(0, 8).map((l) => ({
    label: l.label, value: l.realized + l.potential, display: moneyRounded(l.realized + l.potential),
    sublabel: `${l.count} recommandation(s)`
  })));
  protected readonly byProduct = computed<BarItem[]>(() => (this.savings()?.byProduct ?? []).slice(0, 8).map((l) => ({
    label: l.label, value: l.realized + l.potential, display: moneyRounded(l.realized + l.potential)
  })));
  protected readonly watchlist = computed(() => this.predictions()
    .filter((p) => Math.abs(p.changePct) >= 1).slice(0, 10));

  constructor() {
    this.load();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  load(): void {
    this.api.overview().subscribe({
      next: (o) => {
        this.overview.set(o);
        if (o.lastRun?.status === 'RUNNING') {
          this.startPolling();
        } else {
          this.stopPolling();
        }
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
    this.api.savings().subscribe({ next: (s) => this.savings.set(s), error: () => this.savings.set(null) });
    this.api.predictions().subscribe({ next: (p) => this.predictions.set(p), error: () => this.predictions.set([]) });
  }

  runWatch(): void {
    this.api.runWatch().subscribe({
      next: () => {
        this.notify.info('Veille lancée : elle peut prendre plusieurs minutes.');
        this.load();
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  lastRunLabel(run: WatchRun | null): string {
    return run ? run.period : '—';
  }

  runStatus(run: WatchRun): string {
    return run.status === 'RUNNING' ? 'En cours' : run.status === 'COMPLETED' ? 'Terminée' : 'En échec';
  }

  private startPolling(): void {
    if (!this.poll) {
      this.poll = setInterval(() => this.load(), 10000);
    }
  }

  private stopPolling(): void {
    if (this.poll) {
      clearInterval(this.poll);
      this.poll = null;
    }
  }
}
