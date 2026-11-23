import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Product } from '../../core/models/models';
import {
  AVAILABILITY_LABELS, Availability, Comparison, EQUIVALENCE_LABELS, Equivalence, MarketSupplier, QUOTE_SOURCE_LABELS,
  QuoteRequest, SourcingLead
} from '../../core/models/procurement.models';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ProcurementService } from '../../core/services/procurement.service';
import { money } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { ProductPicker } from '../../shared/product-picker';

function emptyQuote(productId: number): QuoteRequest {
  return { rfqId: null, productId, marketSupplierId: null, offeredName: '', equivalence: 'IDENTICAL', unitPrice: null,
    priceIncludesVat: false, minQuantity: 1, availability: 'IN_STOCK', leadTimeDays: null, paymentTerms: null,
    validUntil: null, qualityScore: 3, source: null };
}

/** Comparaison des offres d'un article (rapport qualité-prix) et collecte des devis. */
@Component({
  selector: 'app-quote-comparison',
  imports: [DatePipe, DecimalPipe, FormsModule, PageHeader, Modal, Icon, ProductPicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Comparaison des offres"
                     subtitle="Devis reçus, prix publiés et offres détectées par la veille, classés selon le rapport qualité-prix">
      @if (product() && auth.canManageCatalog()) {
        <button type="button" class="btn btn--primary" (click)="openQuote()"><app-icon name="plus" [size]="16" /> Ajouter un devis</button>
      }
    </app-page-header>

    <section class="card">
      @if (product(); as p) {
        <div class="toolbar">
          <div><strong>{{ p.name }}</strong> <small class="muted">{{ p.reference }} · {{ p.barcode }}</small></div>
          <label class="inline">Quantité nécessaire
            <input type="number" min="1" [(ngModel)]="quantity" (change)="compare()" class="qty" /></label>
          <button type="button" class="btn btn--sm btn--ghost" (click)="product.set(null); comparison.set(null)">Changer d'article</button>
        </div>
      } @else {
        <app-product-picker (picked)="pick($event)" />
      }
    </section>

    @if (comparison(); as c) {
      <section class="card">
        <div class="card__title"><h3>Classement pour {{ c.quantity }} unité(s)</h3>
          <span class="small muted">Dernier prix payé : <strong>{{ c.referencePrice !== null ? money(c.referencePrice) : 'inconnu' }}</strong>
            · consommation : {{ c.monthlyConsumption | number: '1.0-1' }} / mois</span></div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>#</th><th>Fournisseur</th><th>Article proposé</th><th class="num">Prix HT</th><th class="num">Coût total</th>
              <th class="num">Délai</th><th>Qualité</th><th class="num">Score</th><th class="num">Écart</th><th class="num">Gain annuel</th></tr></thead>
            <tbody>
              @for (o of c.offers; track o.quoteId) {
                <tr [class.row-best]="o.recommended" [class.row-muted]="!o.eligible">
                  <td>@if (o.recommended) { <span class="badge badge--success">Recommandé</span> } @else { {{ o.eligible ? o.rank : '—' }} }</td>
                  <td><strong>{{ o.supplierName }}</strong><br /><small class="muted">{{ sources[o.source] }}
                    @if (o.validUntil) { · valable au {{ o.validUntil | date: 'dd/MM/yyyy' }} }</small></td>
                  <td>{{ o.offeredName }}<br />
                    <span class="badge" [class.badge--info]="o.equivalence === 'EQUIVALENT'">{{ equivalences[o.equivalence] }}</span>
                    <small class="muted"> {{ availability[o.availability] }}@if (o.minQuantity > 1) { · min. {{ o.minQuantity }} }</small>
                    @if (o.exclusionReason) { <br /><small class="text-danger">{{ o.exclusionReason }}</small> }</td>
                  <td class="num">{{ money(o.unitPrice) }}</td>
                  <td class="num">{{ money(o.totalCost) }}@if (o.orderQuantity !== c.quantity) {<br /><small class="muted">pour {{ o.orderQuantity }} u.</small>}</td>
                  <td class="num">{{ o.leadTimeDays !== null ? o.leadTimeDays + ' j' : '?' }}</td>
                  <td>{{ stars(o.qualityScore) }}</td>
                  <td class="num"><strong>{{ o.eligible ? (o.score | number: '1.1-1') : '—' }}</strong></td>
                  <td class="num">@if (o.savingPct !== null) {
                    <span [class.text-success]="o.savingPct > 0" [class.text-danger]="o.savingPct < 0">{{ o.savingPct > 0 ? '-' : '+' }}{{ abs(o.savingPct) | number: '1.1-1' }} %</span> }</td>
                  <td class="num">{{ o.annualSaving ? money(o.annualSaving) : '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="10" class="center muted">Aucune offre pour cet article : lancez la veille ou envoyez des demandes de prix.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <p class="small muted">Score sur 100 : prix effectif 60 %, délai 20 %, qualité 20 %. Le coût effectif tient compte du minimum de commande ;
          un article équivalent perd 5 %.</p>
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
                <tr><td colspan="6" class="center muted">Aucune offre repérée pour cet article.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }

    @if (quoteOpen()) {
      <app-modal title="Ajouter un devis" [wide]="true" (closed)="quoteOpen.set(false)">
        <div class="extract">
          <label class="field"><span>Coller le texte du devis (courriel, PDF) pour le faire lire par l'agent</span>
            <textarea rows="5" [(ngModel)]="quoteText" maxlength="20000" placeholder="Bonjour, suite à votre demande RFQ-…, nous vous proposons…"></textarea></label>
          <button type="button" class="btn btn--sm btn--ghost" [disabled]="!quoteText.trim() || extracting()" (click)="extract()">
            <app-icon name="search" [size]="14" /> {{ extracting() ? 'Lecture…' : 'Lire avec l’IA' }}</button>
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
    if (productId) {
      this.catalog.product(productId).subscribe({ next: (p) => this.pick(p) });
    }
  }

  pick(p: Product): void {
    this.product.set(p);
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
