import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { INVENTORY_STATUS_LABELS, Inventory, Product } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ReportService } from '../../core/services/report.service';
import { StockService } from '../../core/services/stock.service';
import { Icon } from '../../shared/icon';
import { PageHeader } from '../../shared/page-header';
import { ProductPicker } from '../../shared/product-picker';

/** Détail d'un inventaire : saisie des comptages (scan), écarts, validation et PV. */
@Component({
  selector: 'app-inventory-detail',
  imports: [FormsModule, DatePipe, RouterLink, PageHeader, Icon, ProductPicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (inventory(); as inv) {
      <app-page-header [title]="'Inventaire n° ' + inv.id + ' — ' + inv.name"
                       [subtitle]="'Ouvert par ' + inv.createdBy + ' — ' + labels[inv.status]">
        <a class="btn btn--ghost" routerLink="/inventories">‹ Retour</a>
        @if (auth.canReport()) {
          <button type="button" class="btn btn--ghost" (click)="download('pdf')"><app-icon name="download" [size]="16" /> PV PDF</button>
          <button type="button" class="btn btn--ghost" (click)="download('xlsx')"><app-icon name="download" [size]="16" /> Excel</button>
        }
        @if (editable() && auth.canManageCatalog()) {
          <button type="button" class="btn btn--danger-ghost" (click)="cancel()">Annuler l'inventaire</button>
          <button type="button" class="btn btn--success" (click)="validate()" [disabled]="inv.lineCount === 0">
            <app-icon name="check" [size]="16" /> Valider</button>
        }
      </app-page-header>

      <div class="kpi-grid kpi-grid--4">
        <div class="kpi"><div class="kpi__body"><div class="kpi__label">Produits comptés</div><div class="kpi__value">{{ inv.lineCount }}</div></div></div>
        <div class="kpi kpi--warning"><div class="kpi__body"><div class="kpi__label">Lignes avec écart</div><div class="kpi__value">{{ inv.discrepancyCount }}</div></div></div>
        <div class="kpi kpi--success"><div class="kpi__body"><div class="kpi__label">Écarts positifs</div><div class="kpi__value">+{{ totals().plus }}</div></div></div>
        <div class="kpi kpi--danger"><div class="kpi__body"><div class="kpi__label">Écarts négatifs</div><div class="kpi__value">-{{ totals().minus }}</div></div></div>
      </div>

      @if (editable() && auth.canMoveStock()) {
        <section class="card">
          <div class="card__title"><h3>Saisir un comptage</h3></div>
          <div class="grid grid--2">
            <app-product-picker (picked)="pick($event)" />
            <form class="count-form" (ngSubmit)="saveCount()">
              @if (selected(); as p) {
                <div><strong>{{ p.name }}</strong><br /><small class="muted">{{ p.reference }} · {{ p.barcode }}</small></div>
                <label class="field"><span>Quantité comptée</span>
                  <input type="number" name="counted" [(ngModel)]="counted" min="0" step="1" required /></label>
                <button type="submit" class="btn btn--primary">Enregistrer</button>
              } @else {
                <p class="muted">Scannez ou recherchez un produit à compter.</p>
              }
            </form>
          </div>
        </section>
      }

      <section class="card">
        <div class="card__title"><h3>Lignes de comptage</h3>
          <span class="muted small">La quantité théorique est figée au moment du comptage.</span></div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Référence</th><th>Désignation</th><th class="num">Théorique</th><th class="num">Compté</th>
              <th class="num">Écart</th><th>Compté par</th><th>Date</th><th></th></tr></thead>
            <tbody>
              @for (l of inv.lines; track l.productId) {
                <tr [class.row--warning]="l.difference !== 0">
                  <td><strong>{{ l.productReference }}</strong></td>
                  <td>{{ l.productName }}</td>
                  <td class="num">{{ l.expectedQuantity }}</td>
                  <td class="num"><strong>{{ l.countedQuantity }}</strong></td>
                  <td class="num" [class.text-success]="l.difference > 0" [class.text-danger]="l.difference < 0">
                    {{ l.difference > 0 ? '+' : '' }}{{ l.difference }}</td>
                  <td>{{ l.countedBy }}</td>
                  <td>{{ l.countedAt | date: 'dd/MM HH:mm' }}</td>
                  <td>
                    @if (editable() && auth.canMoveStock()) {
                      <button type="button" class="icon-btn icon-btn--danger" (click)="remove(l.productId)" title="Retirer"><app-icon name="x" [size]="16" /></button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="8" class="center muted">Aucun comptage saisi.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `
})
export class InventoryDetailPage implements OnInit {
  private readonly stock = inject(StockService);
  private readonly reports = inject(ReportService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly labels = INVENTORY_STATUS_LABELS;

  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly inventory = signal<Inventory | null>(null);
  protected readonly selected = signal<Product | null>(null);
  protected counted: number | null = null;

  protected readonly editable = computed(() => this.inventory()?.status === 'IN_PROGRESS');
  protected readonly totals = computed(() => {
    const lines = this.inventory()?.lines ?? [];
    return {
      plus: lines.filter((l) => l.difference > 0).reduce((s, l) => s + l.difference, 0),
      minus: lines.filter((l) => l.difference < 0).reduce((s, l) => s - l.difference, 0)
    };
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.stock.inventory(Number(this.id())).subscribe({
      next: (inv) => this.inventory.set(inv),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  pick(p: Product): void {
    this.selected.set(p);
    this.counted = this.inventory()?.lines.find((l) => l.productId === p.id)?.countedQuantity ?? null;
  }

  saveCount(): void {
    const p = this.selected();
    if (!p || this.counted === null || this.counted < 0) {
      this.notify.error('Saisissez une quantité comptée (≥ 0).');
      return;
    }
    this.stock.recordCount(Number(this.id()), p.id, this.counted).subscribe({
      next: (inv) => {
        this.inventory.set(inv);
        this.notify.success(`Comptage enregistré : ${p.reference} = ${this.counted}`);
        this.selected.set(null);
        this.counted = null;
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  remove(productId: number): void {
    this.stock.removeCount(Number(this.id()), productId).subscribe({
      next: (inv) => this.inventory.set(inv),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  validate(): void {
    if (!confirm('Valider l\'inventaire ? Le stock sera aligné sur les quantités comptées (ajustements).')) {
      return;
    }
    this.stock.validateInventory(Number(this.id())).subscribe({
      next: (inv) => {
        this.inventory.set(inv);
        this.notify.success(`Inventaire validé : ${inv.discrepancyCount} ajustement(s) de stock générés.`);
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  cancel(): void {
    if (!confirm('Annuler cet inventaire ? Aucun ajustement ne sera appliqué.')) {
      return;
    }
    this.stock.cancelInventory(Number(this.id())).subscribe({
      next: (inv) => this.inventory.set(inv),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  download(format: 'pdf' | 'xlsx'): void {
    this.reports.download(`/api/reports/inventories/${this.id()}`, { format }).subscribe({
      next: (name) => this.notify.success(`Rapport téléchargé : ${name}`),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
