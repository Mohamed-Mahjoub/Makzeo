import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PageResult } from '../../core/models/models';
import {
  RECO_STATUS_LABELS, RECO_TYPE_LABELS, Recommendation, RecommendationStatus, RecommendationType
} from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { money } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

/** Recommandations d'achat chiffrées : achat anticipé avant une hausse, changement de fournisseur. */
@Component({
  selector: 'app-recommendations',
  imports: [DatePipe, DecimalPipe, RouterLink, PageHeader, Pagination, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Recommandations d'achat"
                     subtitle="Chaque recommandation compare le coût d'un achat immédiat avec celui d'un achat différé">
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--ghost" (click)="refresh()"><app-icon name="refresh" [size]="16" /> Recalculer</button>
      }
    </app-page-header>
    <section class="card">
      <div class="toolbar">
        <div class="chips">
          <button type="button" class="chip" [class.is-active]="status() === 'OPEN'" (click)="setStatus('OPEN')">À décider</button>
          <button type="button" class="chip chip--success" [class.is-active]="status() === 'ACCEPTED'" (click)="setStatus('ACCEPTED')">Acceptées</button>
          <button type="button" class="chip" [class.is-active]="status() === 'DISMISSED'" (click)="setStatus('DISMISSED')">Écartées</button>
          <button type="button" class="chip" [class.is-active]="status() === null" (click)="setStatus(null)">Toutes</button>
        </div>
        <div class="chips">
          <button type="button" class="chip" [class.is-active]="type() === null" (click)="setType(null)">Tous types</button>
          <button type="button" class="chip chip--warning" [class.is-active]="type() === 'ANTICIPATED_PURCHASE'" (click)="setType('ANTICIPATED_PURCHASE')">Achats anticipés</button>
          <button type="button" class="chip chip--info" [class.is-active]="type() === 'SWITCH_SUPPLIER'" (click)="setType('SWITCH_SUPPLIER')">Changements de fournisseur</button>
        </div>
      </div>

      <div class="reco-list">
        @for (r of result()?.content ?? []; track r.id) {
          <article class="reco">
            <header>
              <span class="badge" [class.badge--warning]="r.type === 'ANTICIPATED_PURCHASE'" [class.badge--info]="r.type === 'SWITCH_SUPPLIER'">{{ types[r.type] }}</span>
              <h3>{{ r.productName }} <small class="muted">{{ r.productReference }}</small></h3>
              <div class="gain"><span>Gain net estimé</span><strong>{{ money(r.netSavings) }}</strong></div>
            </header>
            <div class="figures">
              <div><span>Quantité</span><strong>{{ r.quantity }}</strong></div>
              <div><span>{{ r.type === 'ANTICIPATED_PURCHASE' ? 'Prix actuel' : 'Prix proposé' }}</span><strong>{{ money(r.recommendedUnitPrice) }}</strong></div>
              <div><span>{{ r.type === 'ANTICIPATED_PURCHASE' ? 'Prix après hausse' : 'Dernier prix payé' }}</span>
                <strong>{{ money(r.type === 'ANTICIPATED_PURCHASE' ? r.expectedFuturePrice : r.referenceUnitPrice) }}</strong></div>
              <div><span>Achat maintenant</span><strong>{{ money(r.costNow + r.holdingCost) }}</strong>
                @if (r.holdingCost > 0) { <small class="muted">dont stockage {{ money(r.holdingCost) }}</small> }</div>
              <div><span>{{ r.type === 'ANTICIPATED_PURCHASE' ? 'Achat différé' : 'Fournisseur actuel' }}</span><strong>{{ money(r.costLater) }}</strong></div>
              <div><span>Fournisseur</span><strong>{{ r.supplierName ?? 'Actuel' }}</strong></div>
            </div>
            <p class="rationale">{{ r.rationale }}</p>
            <footer>
              <span class="small muted">
                @if (r.buyBefore) { À décider avant le {{ r.buyBefore | date: 'dd/MM/yyyy' }} · }
                @if (r.confidence !== null) { confiance {{ r.confidence * 100 | number: '1.0-0' }} % · }
                proposée le {{ r.createdAt | date: 'dd/MM/yyyy' }}
                @if (r.decidedBy) { · {{ statuses[r.status].toLowerCase() }} par {{ r.decidedBy }} }
              </span>
              <span class="actions">
                <a class="btn btn--sm btn--ghost" routerLink="/achats/devis" [queryParams]="{ productId: r.productId }">Voir les offres</a>
                @if (r.status === 'OPEN' && auth.canManageCatalog()) {
                  <button type="button" class="btn btn--sm btn--danger-ghost" (click)="dismiss(r)">Écarter</button>
                  <button type="button" class="btn btn--sm btn--success" (click)="accept(r)"><app-icon name="check" [size]="14" /> Accepter</button>
                }
              </span>
            </footer>
          </article>
        } @empty {
          <p class="center muted">Aucune recommandation dans cette catégorie.</p>
        }
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>
  `,
  styles: [`
    .reco-list { display: flex; flex-direction: column; gap: 14px; }
    .reco { border: 1px solid var(--border); border-radius: 12px; padding: 14px 18px; }
    .reco header { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .reco h3 { margin: 0; flex: 1; font-size: 1rem; }
    .gain { display: flex; flex-direction: column; align-items: flex-end; }
    .gain span { font-size: .75rem; color: var(--muted); }
    .gain strong { font-size: 1.3rem; color: var(--success); }
    .figures { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; margin: 12px 0; }
    .figures div { display: flex; flex-direction: column; }
    .figures span { font-size: .75rem; color: var(--muted); }
    .rationale { margin: 0 0 10px; font-size: .9rem; }
    .reco footer { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
    @media (max-width: 900px) { .figures { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  `]
})
export class RecommendationsPage {
  private readonly api = inject(ProcurementService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly types = RECO_TYPE_LABELS;
  protected readonly statuses = RECO_STATUS_LABELS;
  protected readonly money = money;

  protected readonly status = signal<RecommendationStatus | null>('OPEN');
  protected readonly type = signal<RecommendationType | null>(null);
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<Recommendation> | null>(null);

  constructor() {
    this.load();
  }

  setStatus(s: RecommendationStatus | null): void {
    this.status.set(s);
    this.load();
  }

  setType(t: RecommendationType | null): void {
    this.type.set(t);
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.api.recommendations({ status: this.status(), type: this.type(), page, size: 10 }).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  refresh(): void {
    this.api.refreshRecommendations().subscribe({
      next: (r) => {
        this.notify.success(`${r.created} nouvelle(s) recommandation(s).` + (r.notified ? ` Alerte envoyée (${r.notified}).` : ''));
        this.load();
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  accept(r: Recommendation): void {
    this.api.acceptRecommendation(r.id).subscribe({
      next: () => {
        this.notify.success('Recommandation acceptée : économie comptabilisée.');
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  dismiss(r: Recommendation): void {
    this.api.dismissRecommendation(r.id).subscribe({
      next: () => {
        this.notify.success('Recommandation écartée.');
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
