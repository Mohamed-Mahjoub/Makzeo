import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Product } from '../../core/models/models';
import {
  AVAILABILITY_LABELS, Availability, ComparedOffer, Comparison, EQUIVALENCE_LABELS, Equivalence, MarketSupplier,
  QUOTE_SOURCE_LABELS, QuoteRequest, QuotedProduct, SourcingLead
} from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { PriceBar, PriceBarsChart } from '../../shared/charts/price-bars-chart';
import { money } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { ProductPicker } from '../../shared/product-picker';
import { AiReportButton } from './ai-report-button';

/** Offre reclassée selon les pondérations choisies à l'écran. */
interface RankedRow {
  offer: ComparedOffer;
  included: boolean;
  score: number;
  rank: number;
}

function emptyQuote(productId: number): QuoteRequest {
  return { rfqId: null, productId, marketSupplierId: null, offeredName: '', equivalence: 'IDENTICAL', unitPrice: null,
    priceIncludesVat: false, minQuantity: 1, availability: 'IN_STOCK', leadTimeDays: null, paymentTerms: null,
    validUntil: null, qualityScore: 3, source: null };
}

/** Comparaison des offres d'un article (rapport qualité-prix) et collecte des devis. */
@Component({
  selector: 'app-quote-comparison',
  imports: [DatePipe, DecimalPipe, FormsModule, PageHeader, Modal, Icon, ProductPicker, KpiCard, PriceBarsChart, AiReportButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Comparaison des offres"
                     subtitle="Devis reçus, prix publiés et offres détectées par la veille, classés selon le rapport qualité-prix">
      <app-ai-report-button />
      @if (product() && auth.canManageCatalog()) {
        <button type="button" class="btn btn--primary" (click)="openQuote()"><app-icon name="plus" [size]="16" /> Ajouter un devis</button>
      }
    </app-page-header>

    <div class="layout">
      <!-- navigation : articles disposant d'offres -->
      <aside class="card nav-panel">
        <h4>Articles avec offres <span class="badge">{{ quoted().length }}</span></h4>
        <div class="search"><app-icon name="search" [size]="16" />
          <input type="search" [ngModel]="navQ()" (ngModelChange)="navQ.set($event)" placeholder="Filtrer…" aria-label="Filtrer les articles" /></div>
        <ul class="nav-list">
          @for (qp of quotedVisible(); track qp.productId) {
            <li>
              <button type="button" [class.is-active]="product()?.id === qp.productId" (click)="openProduct(qp.productId)">
                <span class="nav-name">{{ qp.name }}</span>
                <small class="muted">{{ qp.reference }} · {{ qp.offers }} offre(s)</small>
                @if (gap(qp); as g) {
                  <span class="nav-gap" [class.good]="g < 0">{{ g < 0 ? '' : '+' }}{{ g | number: '1.0-0' }} %</span>
                }
              </button>
            </li>
          } @empty {
            <li class="muted small">Aucun article avec offres{{ navQ() ? ' pour ce filtre' : '' }}.</li>
          }
        </ul>
        <details class="other">
          <summary>Autre article du catalogue</summary>
          <app-product-picker (picked)="pick($event)" />
        </details>
      </aside>

      <div class="main">
        @if (product(); as p) {
          <section class="card">
            <div class="card__title">
              <div><h3>{{ p.name }}</h3><small class="muted">{{ p.reference }} · {{ p.barcode }}</small></div>
              <div class="nav-buttons">
                <button type="button" class="btn btn--sm btn--ghost" [disabled]="navIndex() <= 0" (click)="stepProduct(-1)">‹ Précédent</button>
                <button type="button" class="btn btn--sm btn--ghost" [disabled]="navIndex() < 0 || navIndex() >= quotedVisible().length - 1"
                        (click)="stepProduct(1)">Suivant ›</button>
                <label class="inline">Quantité
                  <input type="number" min="1" [(ngModel)]="quantity" (change)="compare()" class="qty" /></label>
              </div>
            </div>

            @if (comparison(); as c) {
              <div class="kpi-grid kpi-grid--4">
                <app-kpi-card label="Offres comparées" [value]="eligibleCount() + ' / ' + c.offers.length" icon="clipboard"
                              hint="éligibles / total" />
                <app-kpi-card label="Meilleure offre" [value]="bestPrice()" icon="check" tone="success"
                              [hint]="bestSupplier()" />
                <app-kpi-card label="Dernier prix payé" [value]="c.referencePrice !== null ? money(c.referencePrice) : 'inconnu'" icon="clock"
                              [hint]="'consommation : ' + (c.monthlyConsumption | number: '1.0-1') + ' / mois'" />
                <app-kpi-card label="Économie possible" [value]="bestSaving() !== null ? (bestSaving()! | number: '1.1-1') + ' %' : '—'" icon="coins"
                              [tone]="(bestSaving() ?? 0) > 0 ? 'success' : 'default'"
                              [hint]="bestAnnualHint()" />
              </div>
            }
          </section>

          @if (comparison(); as c) {
            <div class="grid grid--3-1">
              <section class="card">
                <div class="card__title"><h3>Prix HT des offres</h3>
                  <small class="muted">Repère : dernier prix payé · cliquez sur une offre pour la mettre en évidence</small></div>
                <app-price-bars-chart [bars]="bars()" [reference]="c.referencePrice" [selectedId]="focus()" (picked)="toggleFocus($event)" />
              </section>
              <section class="card weights">
                <div class="card__title"><h3>Pondérations</h3>
                  <button type="button" class="btn btn--sm btn--ghost" (click)="resetWeights()">60 / 20 / 20</button></div>
                <label>Prix <strong>{{ share(wPrice()) }} %</strong>
                  <input type="range" min="0" max="100" [ngModel]="wPrice()" (ngModelChange)="wPrice.set(+$event)" /></label>
                <label>Délai <strong>{{ share(wLead()) }} %</strong>
                  <input type="range" min="0" max="100" [ngModel]="wLead()" (ngModelChange)="wLead.set(+$event)" /></label>
                <label>Qualité <strong>{{ share(wQuality()) }} %</strong>
                  <input type="range" min="0" max="100" [ngModel]="wQuality()" (ngModelChange)="wQuality.set(+$event)" /></label>
                <label class="check"><input type="checkbox" [ngModel]="withEquivalents()" (ngModelChange)="withEquivalents.set($event)" />
                  Inclure les articles équivalents</label>
                <p class="small muted">Le classement est recalculé instantanément ; la recommandation enregistrée par le module utilise 60 / 20 / 20.</p>
              </section>
            </div>

            <section class="card">
              <div class="toolbar">
                <div class="search"><app-icon name="search" [size]="16" />
                  <input type="search" [ngModel]="offerQ()" (ngModelChange)="offerQ.set($event)" placeholder="Fournisseur ou article…"
                         aria-label="Rechercher une offre" /></div>
                <div class="chips">
                  <button type="button" class="chip" [class.is-active]="eqFilter() === 'all'" (click)="eqFilter.set('all')">Toutes</button>
                  <button type="button" class="chip" [class.is-active]="eqFilter() === 'IDENTICAL'" (click)="eqFilter.set('IDENTICAL')">Identiques</button>
                  <button type="button" class="chip chip--info" [class.is-active]="eqFilter() === 'EQUIVALENT'" (click)="eqFilter.set('EQUIVALENT')">Équivalents</button>
                </div>
              </div>
              <div class="table-wrap">
                <table class="table">
                  <thead><tr><th>#</th><th>Fournisseur</th><th>Article proposé</th><th class="num">Prix HT</th><th class="num">Coût total</th>
                    <th class="num">Délai</th><th>Qualité</th><th>Score</th><th class="num">Écart</th><th class="num">Gain annuel</th></tr></thead>
                  <tbody>
                    @for (r of tableRows(); track r.offer.quoteId) {
                      <tr [class.row-best]="r.rank === 1" [class.row-muted]="!r.included" [class.row-focus]="focus() === r.offer.quoteId"
                          (click)="toggleFocus(r.offer.quoteId)">
                        <td>@if (r.rank === 1) { <span class="badge badge--success">1er</span> } @else { {{ r.rank || '—' }} }</td>
                        <td><strong>{{ r.offer.supplierName }}</strong><br /><small class="muted">{{ sources[r.offer.source] }}
                          @if (r.offer.validUntil) { · valable au {{ r.offer.validUntil | date: 'dd/MM/yyyy' }} }</small></td>
                        <td>{{ r.offer.offeredName }}<br />
                          <span class="badge" [class.badge--info]="r.offer.equivalence === 'EQUIVALENT'">{{ equivalences[r.offer.equivalence] }}</span>
                          <small class="muted"> {{ availability[r.offer.availability] }}@if (r.offer.minQuantity > 1) { · min. {{ r.offer.minQuantity }} }</small>
                          @if (r.offer.exclusionReason) { <br /><small class="text-danger">{{ r.offer.exclusionReason }}</small> }</td>
                        <td class="num">{{ money(r.offer.unitPrice) }}</td>
                        <td class="num">{{ money(r.offer.totalCost) }}@if (r.offer.orderQuantity !== c.quantity) {<br /><small class="muted">pour {{ r.offer.orderQuantity }} u.</small>}</td>
                        <td class="num">{{ r.offer.leadTimeDays !== null ? r.offer.leadTimeDays + ' j' : '?' }}</td>
                        <td class="stars" [title]="r.offer.qualityScore + ' / 5'">{{ stars(r.offer.qualityScore) }}</td>
                        <td class="score">@if (r.included) {
                          <span class="score__bar"><span [style.width.%]="r.score"></span></span><strong>{{ r.score | number: '1.1-1' }}</strong> } @else { — }</td>
                        <td class="num">@if (r.offer.savingPct !== null) {
                          <span [class.text-success]="r.offer.savingPct > 0" [class.text-danger]="r.offer.savingPct < 0">{{ r.offer.savingPct > 0 ? '-' : '+' }}{{ abs(r.offer.savingPct) | number: '1.1-1' }} %</span> }</td>
                        <td class="num">{{ r.offer.annualSaving ? money(r.offer.annualSaving) : '—' }}</td>
                      </tr>
                    } @empty {
                      <tr><td colspan="10" class="center muted">Aucune offre ne correspond : lancez la veille, envoyez des demandes de prix ou ajoutez un devis.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
              <p class="small muted">Score sur 100 = prix effectif × {{ share(wPrice()) }} % + délai × {{ share(wLead()) }} % + qualité × {{ share(wQuality()) }} % ;
                le coût effectif tient compte du minimum de commande ; un article équivalent perd 5 %.</p>
            </section>

            <section class="card">
              <div class="card__title"><h3>Offres repérées par la veille</h3></div>
              <div class="table-wrap">
                <table class="table">
                  <thead><tr><th>Fournisseur</th><th>Article</th><th>Correspondance</th><th class="num">Prix publié</th><th>Justification</th><th>Date</th></tr></thead>
                  <tbody>
                    @for (l of leads(); track l.id) {
                      <tr>
                        <td>{{ l.supplierName }}</td>
                        <td>@if (l.url) { <a [href]="l.url" target="_blank" rel="noopener noreferrer">{{ l.offeredName }}</a> } @else { {{ l.offeredName }} }
                          @if (l.offeredReference) { <br /><small class="muted">{{ l.offeredReference }}</small> }</td>
                        <td><span class="badge" [class.badge--info]="l.equivalence === 'EQUIVALENT'">{{ equivalences[l.equivalence] }}</span>
                          <small class="muted"> {{ l.similarity }} %</small></td>
                        <td class="num">{{ l.publishedPrice !== null ? money(l.publishedPrice) : '—' }}</td>
                        <td class="small">{{ l.rationale }}</td>
                        <td>{{ l.detectedAt | date: 'dd/MM/yyyy' }}</td>
                      </tr>
                    } @empty {
                      <tr><td colspan="6" class="center muted">Aucune offre repérée par la veille pour cet article.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </section>
          }
        } @else {
          <section class="card empty">
            <app-icon name="swap" [size]="32" />
            <h3>Choisissez un article</h3>
            <p class="muted">Sélectionnez un article dans la liste de gauche ou recherchez un autre article du catalogue.</p>
          </section>
        }
      </div>
    </div>

    @if (quoteOpen()) {
      <app-modal title="Ajouter un devis" [wide]="true" (closed)="quoteOpen.set(false)">
        <div class="extract">
          <label class="field"><span>Coller le texte du devis (courriel, PDF) pour le faire lire par l'agent</span>
            <textarea rows="5" [(ngModel)]="quoteText" maxlength="20000" placeholder="Bonjour, suite à votre demande RFQ-…, nous vous proposons…"></textarea></label>
          <button type="button" class="btn btn--sm btn--ghost" [disabled]="!quoteText.trim() || extracting()" (click)="extract()">
            <app-icon name="search" [size]="14" /> {{ extracting() ? 'Lecture…' : 'Lire le devis' }}</button>
          <p class="small muted">Les champs ci-dessous sont pré-remplis : vérifiez-les avant d'enregistrer.</p>
        </div>
        <form class="form form--grid" (ngSubmit)="saveQuote()" #f="ngForm">
          <label class="field"><span>Fournisseur *</span>
            <select name="supplier" [(ngModel)]="quote.marketSupplierId" required>
              <option [ngValue]="null">— Choisir —</option>
              @for (s of suppliers(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
            </select></label>
          <label class="field"><span>Article proposé *</span><input name="offered" [(ngModel)]="quote.offeredName" required maxlength="255" /></label>
          <label class="field"><span>Correspondance</span>
            <select name="eq" [(ngModel)]="quote.equivalence">
              @for (e of equivalenceKeys; track e) { <option [ngValue]="e">{{ equivalences[e] }}</option> }
            </select></label>
          <label class="field"><span>Prix unitaire *</span><input type="number" step="0.001" min="0.001" name="price" [(ngModel)]="quote.unitPrice" required /></label>
          <label class="field checkbox"><input type="checkbox" name="vat" [(ngModel)]="quote.priceIncludesVat" /> <span>Prix TTC (converti en HT)</span></label>
          <label class="field"><span>Quantité minimale</span><input type="number" min="1" name="minQty" [(ngModel)]="quote.minQuantity" /></label>
          <label class="field"><span>Disponibilité</span>
            <select name="avail" [(ngModel)]="quote.availability">
              @for (a of availabilityKeys; track a) { <option [ngValue]="a">{{ availability[a] }}</option> }
            </select></label>
          <label class="field"><span>Délai de livraison (jours)</span><input type="number" min="0" max="365" name="lead" [(ngModel)]="quote.leadTimeDays" /></label>
          <label class="field"><span>Validité de l'offre</span><input type="date" name="valid" [(ngModel)]="quote.validUntil" /></label>
          <label class="field"><span>Qualité (1 à 5)</span><input type="number" min="1" max="5" name="quality" [(ngModel)]="quote.qualityScore" /></label>
          <label class="field field--full"><span>Conditions de paiement</span><input name="terms" [(ngModel)]="quote.paymentTerms" maxlength="255" /></label>
          <div class="form__actions field--full">
            <button type="button" class="btn btn--ghost" (click)="quoteOpen.set(false)">Annuler</button>
            <button type="submit" class="btn btn--primary" [disabled]="f.invalid">Enregistrer le devis</button>
          </div>
        </form>
      </app-modal>
    }
  `,
  styles: [`
    .inline { display: flex; align-items: center; gap: 8px; font-size: .9rem; }
    .qty { width: 90px; }
    .row-best { background: var(--success-50); }
    .row-muted { opacity: .55; }
    .text-danger { color: var(--danger); }
    .text-success { color: var(--success); font-weight: 600; }
    .extract { border-bottom: 1px solid var(--border); padding-bottom: 12px; margin-bottom: 12px; }
    .checkbox { flex-direction: row; align-items: center; gap: 8px; }
    .layout { display: grid; grid-template-columns: 290px minmax(0, 1fr); gap: 18px; align-items: start; }
    .nav-panel { position: sticky; top: 12px; max-height: calc(100vh - 110px); display: flex; flex-direction: column; gap: 10px; }
    .nav-panel h4 { margin: 0; display: flex; justify-content: space-between; align-items: center; }
    .nav-panel .search { max-width: none; min-width: 0; }
    .nav-list { list-style: none; margin: 0; padding: 0; overflow-y: auto; flex: 1; }
    .nav-list button { width: 100%; display: grid; grid-template-columns: 1fr auto; gap: 2px 8px; text-align: left; background: none;
      border: 0; border-radius: 8px; padding: 8px 10px; cursor: pointer; font: inherit; }
    .nav-list button:hover { background: var(--surface-2, #f8fafc); }
    .nav-list button.is-active { background: var(--primary-50); box-shadow: inset 3px 0 0 var(--primary); }
    .nav-name { font-weight: 600; font-size: .86rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .nav-list small { grid-column: 1; }
    .nav-gap { grid-row: 1 / span 2; grid-column: 2; align-self: center; font-size: .78rem; font-weight: 700; color: var(--danger); }
    .nav-gap.good { color: var(--success); }
    .other summary { cursor: pointer; font-size: .85rem; color: var(--primary); margin-bottom: 8px; }
    .nav-buttons { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .main { min-width: 0; }
    .weights label { display: flex; flex-direction: column; gap: 4px; font-size: .86rem; margin-bottom: 12px; }
    .weights label strong { float: right; }
    .weights .check { flex-direction: row; align-items: center; gap: 8px; }
    .weights input[type=range] { width: 100%; accent-color: var(--primary); }
    .row-focus { outline: 2px solid var(--primary); outline-offset: -2px; }
    tbody tr { cursor: pointer; }
    .stars { color: #f59e0b; white-space: nowrap; letter-spacing: 1px; }
    .score { min-width: 110px; }
    .score__bar { display: inline-block; width: 60px; height: 6px; background: var(--track); border-radius: 99px; margin-right: 6px;
      vertical-align: middle; overflow: hidden; }
    .score__bar span { display: block; height: 100%; background: var(--primary); }
    .empty { text-align: center; padding: 48px 20px; color: var(--muted); }
    .empty h3 { color: var(--text); margin: 10px 0 4px; }
    @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } .nav-panel { position: static; max-height: 360px; } }
  `]
})
export class QuoteComparisonPage {
  private readonly api = inject(ProcurementService);
  private readonly catalog = inject(CatalogService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly money = money;
  protected readonly equivalences = EQUIVALENCE_LABELS;
  protected readonly availability = AVAILABILITY_LABELS;
  protected readonly sources = QUOTE_SOURCE_LABELS;
  protected readonly equivalenceKeys: Equivalence[] = ['IDENTICAL', 'EQUIVALENT'];
  protected readonly availabilityKeys: Availability[] = ['IN_STOCK', 'ON_ORDER', 'UNAVAILABLE'];

  protected readonly product = signal<Product | null>(null);

  // navigation entre les articles disposant d'offres
  protected readonly quoted = signal<QuotedProduct[]>([]);
  protected readonly navQ = signal('');
  protected readonly quotedVisible = computed(() => {
    const q = this.navQ().trim().toLowerCase();
    return this.quoted().filter((p) => !q || p.name.toLowerCase().includes(q) || p.reference.toLowerCase().includes(q));
  });
  protected readonly navIndex = computed(() => this.quotedVisible().findIndex((p) => p.productId === this.product()?.id));

  // classement interactif
  protected readonly wPrice = signal(60);
  protected readonly wLead = signal(20);
  protected readonly wQuality = signal(20);
  protected readonly withEquivalents = signal(true);
  protected readonly offerQ = signal('');
  protected readonly eqFilter = signal<'all' | Equivalence>('all');
  protected readonly focus = signal<number | null>(null);

  /** Recalcule le score de chaque offre avec les pondérations de l'écran (même formule que le module). */
  protected readonly ranked = computed<RankedRow[]>(() => {
    const c = this.comparison();
    if (!c) { return []; }
    const total = this.wPrice() + this.wLead() + this.wQuality() || 1;
    const [wp, wl, wq] = [this.wPrice() / total, this.wLead() / total, this.wQuality() / total];
    const included = (o: ComparedOffer) => o.eligible && (this.withEquivalents() || o.equivalence === 'IDENTICAL');
    const pool = c.offers.filter(included);
    const minCost = Math.min(...pool.map((o) => o.effectiveUnitCost));
    const leads = pool.map((o) => o.leadTimeDays).filter((d): d is number => d !== null);
    const minLead = leads.length ? Math.min(...leads) : 0;
    const rows = c.offers.map((o) => {
      if (!included(o)) { return { offer: o, included: false, score: 0, rank: 0 }; }
      const lead = o.leadTimeDays ?? 30;
      let score = 100 * (wp * (minCost / o.effectiveUnitCost) + wl * ((minLead + 1) / (lead + 1)) + wq * (o.qualityScore / 5));
      if (o.equivalence === 'EQUIVALENT') { score *= 0.95; }
      return { offer: o, included: true, score: Math.round(score * 10) / 10, rank: 0 };
    });
    rows.sort((a, b) => Number(b.included) - Number(a.included) || b.score - a.score
      || a.offer.effectiveUnitCost - b.offer.effectiveUnitCost);
    let rank = 1;
    return rows.map((r) => ({ ...r, rank: r.included ? rank++ : 0 }));
  });

  protected readonly tableRows = computed(() => {
    const q = this.offerQ().trim().toLowerCase();
    return this.ranked().filter((r) => (this.eqFilter() === 'all' || r.offer.equivalence === this.eqFilter())
      && (!q || r.offer.supplierName.toLowerCase().includes(q) || r.offer.offeredName.toLowerCase().includes(q)));
  });

  protected readonly best = computed(() => this.ranked().find((r) => r.rank === 1) ?? null);
  protected readonly eligibleCount = computed(() => this.ranked().filter((r) => r.included).length);
  protected readonly bestSaving = computed(() => this.best()?.offer.savingPct ?? null);
  protected readonly bestSupplier = computed(() => this.best()?.offer.supplierName ?? 'aucune');
  protected readonly bestPrice = computed(() => {
    const b = this.best();
    return b ? money(b.offer.unitPrice) : '—';
  });
  protected readonly bestAnnualHint = computed(() => {
    const annual = this.best()?.offer.annualSaving;
    return annual ? money(annual) + ' par an' : 'sur le dernier prix payé';
  });

  protected readonly bars = computed<PriceBar[]>(() => this.ranked().map((r) => ({
    id: r.offer.quoteId,
    label: r.offer.supplierName,
    sublabel: r.offer.offeredName,
    value: r.offer.unitPrice,
    display: money(r.offer.unitPrice),
    tone: !r.included ? 'excluded' : r.rank === 1 ? 'best' : r.offer.equivalence === 'EQUIVALENT' ? 'equivalent' : 'identical'
  })));
  protected readonly comparison = signal<Comparison | null>(null);
  protected readonly leads = signal<SourcingLead[]>([]);
  protected quantity: number | null = null;

  protected readonly quoteOpen = signal(false);
  protected readonly suppliers = signal<MarketSupplier[]>([]);
  protected readonly extracting = signal(false);
  protected quote: QuoteRequest = emptyQuote(0);
  protected quoteText = '';

  constructor() {
    const productId = Number(inject(ActivatedRoute).snapshot.queryParamMap.get('productId'));
    this.api.quotedProducts().subscribe({
      next: (list) => {
        this.quoted.set(list);
        if (!productId && !this.product() && list.length > 0) {
          this.openProduct(list[0].productId);
        }
      },
      error: () => this.quoted.set([])
    });
    if (productId) {
      this.openProduct(productId);
    }
  }

  openProduct(productId: number): void {
    this.catalog.product(productId).subscribe({ next: (p) => this.pick(p), error: (err) => this.notify.error(errorMessage(err)) });
  }

  stepProduct(delta: number): void {
    const next = this.quotedVisible()[this.navIndex() + delta];
    if (next) { this.openProduct(next.productId); }
  }

  /** Écart (%) entre la meilleure offre et le dernier prix payé, pour la liste de navigation. */
  gap(p: QuotedProduct): number | null {
    return p.bestUnitPrice !== null && p.referencePrice ? ((p.bestUnitPrice - p.referencePrice) / p.referencePrice) * 100 : null;
  }

  share(w: number): number {
    const total = this.wPrice() + this.wLead() + this.wQuality() || 1;
    return Math.round((w / total) * 100);
  }

  resetWeights(): void {
    this.wPrice.set(60);
    this.wLead.set(20);
    this.wQuality.set(20);
    this.withEquivalents.set(true);
  }

  toggleFocus(quoteId: number): void {
    this.focus.set(this.focus() === quoteId ? null : quoteId);
  }

  pick(p: Product): void {
    this.product.set(p);
    this.focus.set(null);
    this.offerQ.set('');
    this.quantity = null;
    this.compare();
  }

  compare(): void {
    const p = this.product();
    if (!p) { return; }
    this.api.comparison(p.id, this.quantity).subscribe({
      next: (c) => {
        this.comparison.set(c);
        this.quantity = c.quantity;
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
    this.api.leads(p.id).subscribe({ next: (l) => this.leads.set(l), error: () => this.leads.set([]) });
  }

  openQuote(): void {
    const p = this.product();
    if (!p) { return; }
    this.quote = emptyQuote(p.id);
    this.quote.offeredName = p.name;
    this.quoteText = '';
    this.api.suppliers('VALIDATED', 0, 500).subscribe({ next: (r) => this.suppliers.set(r.content) });
    this.quoteOpen.set(true);
  }

  extract(): void {
    const p = this.product();
    if (!p) { return; }
    this.extracting.set(true);
    this.api.extractQuote(p.id, this.quoteText).subscribe({
      next: (e) => {
        this.extracting.set(false);
        const match = this.suppliers().find((s) => e.supplierName && s.name.toLowerCase().includes(e.supplierName.toLowerCase()));
        this.quote = {
          ...this.quote,
          marketSupplierId: match?.id ?? this.quote.marketSupplierId,
          offeredName: e.offeredName ?? this.quote.offeredName,
          unitPrice: e.unitPrice,
          priceIncludesVat: e.priceIncludesVat,
          minQuantity: e.minQuantity ?? 1,
          availability: e.availability,
          leadTimeDays: e.leadTimeDays,
          paymentTerms: e.paymentTerms,
          validUntil: e.validUntil,
          source: 'AI_EXTRACTED'
        };
        this.notify.info('Devis lu : vérifiez les champs avant d\'enregistrer.');
      },
      error: (err) => {
        this.extracting.set(false);
        this.notify.error(errorMessage(err));
      }
    });
  }

  saveQuote(): void {
    this.api.registerQuote(this.quote).subscribe({
      next: () => {
        this.notify.success('Devis enregistré.');
        this.quoteOpen.set(false);
        this.compare();
        this.api.quotedProducts().subscribe({ next: (list) => this.quoted.set(list) });
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  stars(n: number): string {
    return '★'.repeat(n) + '☆'.repeat(5 - n);
  }

  abs(n: number): number {
    return Math.abs(n);
  }
}
