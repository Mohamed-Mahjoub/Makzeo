import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Analytics, Category, Replenishment, ReplenishmentPriority, StockAlert } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { errorMessage } from '../../core/services/error-message';
import { ReportService } from '../../core/services/report.service';
import { StockService } from '../../core/services/stock.service';
import { BarItem, BarListChart } from '../../shared/charts/bar-list-chart';
import { PALETTE } from '../../shared/charts/chart-utils';
import { DonutChart, Slice } from '../../shared/charts/donut-chart';
import { Series, TimeSeriesChart } from '../../shared/charts/time-series-chart';
import { money, moneyRounded, num, shortDate } from '../../shared/format';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';

type Tab = 'status' | 'replenishment' | 'valuation';

const CATEGORY_COLORS = [PALETTE.primary, PALETTE.teal, PALETTE.amber, PALETTE.violet, PALETTE.red, PALETTE.slate,
  PALETTE.green];

/**
 * Tableau de bord décisionnel en 3 vues (inspiré des tableaux de bord BI achats / stocks) :
 * 1. État des stocks  2. Prévision de réapprovisionnement  3. Valorisation du stock.
 */
@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, DatePipe, RouterLink, KpiCard, Icon, TimeSeriesChart, DonutChart, BarListChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html'
})
export class DashboardPage {
  private readonly reports = inject(ReportService);
  private readonly stock = inject(StockService);
  private readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly tab = signal<Tab>('status');
  protected readonly days = signal(30);
  protected readonly categoryId = signal<number | null>(null);
  protected readonly categories = signal<Category[]>([]);
  protected readonly data = signal<Analytics | null>(null);
  protected readonly alerts = signal<StockAlert[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly priorityFilter = signal<ReplenishmentPriority | 'ALL' | 'TO_ORDER'>('TO_ORDER');

  protected readonly periods = [7, 30, 90, 180];
  protected readonly money = money;
  protected readonly moneyRounded = moneyRounded;
  protected readonly num = num;

  constructor() {
    this.catalog.categories().subscribe({ next: (c) => this.categories.set(c), error: () => undefined });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      analytics: this.reports.analytics(this.days(), this.categoryId()),
      alerts: this.stock.alerts({ activeOnly: true, size: 6 })
    }).subscribe({
      next: ({ analytics, alerts }) => {
        this.data.set(analytics);
        this.alerts.set(alerts.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
  }

  setPeriod(days: number): void {
    this.days.set(days);
    this.load();
  }

  setCategory(value: number | null): void {
    this.categoryId.set(value);
    this.load();
  }

  restock(item: Replenishment): void {
    this.router.navigate(['/movements'], {
      queryParams: { productId: item.productId, type: 'IN', quantity: item.suggestedQuantity || null }
    });
  }

  // ---------- Vue 1 : état des stocks
  protected readonly flowLabels = computed(() => (this.data()?.dailyFlows ?? []).map((f) => shortDate(f.date)));

  protected readonly flowSeries = computed<Series[]>(() => {
    const flows = this.data()?.dailyFlows ?? [];
    const exits = flows.map((f) => f.exits);
    const movingAverage = exits.map((_, i) => {
      const window = exits.slice(Math.max(0, i - 6), i + 1);
      return Math.round((window.reduce((s, v) => s + v, 0) / window.length) * 10) / 10;
    });
    return [
      { name: 'Entrées', color: PALETTE.teal, values: flows.map((f) => f.entries), kind: 'bar' },
      { name: 'Sorties', color: PALETTE.primary, values: exits, kind: 'bar' },
      { name: 'Sorties — moyenne mobile 7 j', color: PALETTE.amber, values: movingAverage, kind: 'line' }
    ];
  });

  protected readonly statusSlices = computed<Slice[]>(() => {
    const colors: Record<string, string> = { OK: PALETTE.green, LOW: PALETTE.amber, OUT_OF_STOCK: PALETTE.red };
    return (this.data()?.statusDistribution ?? []).map((s) => ({ label: s.label, value: s.count, color: colors[s.status] }));
  });

  protected readonly topConsumed = computed<BarItem[]>(() =>
    (this.data()?.topConsumed ?? []).map((p) => ({
      label: p.name, sublabel: p.reference, value: p.exits,
      display: `${num(p.exits)} (${num(p.avgDailyExits)}/j)`
    })));

  // ---------- Vue 2 : réapprovisionnement
  protected readonly replenishment = computed(() => {
    const list = this.data()?.replenishment ?? [];
    const filter = this.priorityFilter();
    if (filter === 'ALL') {
      return list;
    }
    if (filter === 'TO_ORDER') {
      return list.filter((r) => r.priority !== 'NORMALE');
    }
    return list.filter((r) => r.priority === filter);
  });

  protected readonly priorityCounts = computed(() => {
    const counts: Record<ReplenishmentPriority, number> = { CRITIQUE: 0, HAUTE: 0, MOYENNE: 0, NORMALE: 0 };
    for (const r of this.data()?.replenishment ?? []) {
      counts[r.priority]++;
    }
    return counts;
  });

  /** Largeur de la jauge de couverture (30 jours = 100 %). */
  coverageWidth(r: Replenishment): number {
    if (r.coverageDays === null) {
      return 100;
    }
    return Math.min(100, (r.coverageDays / 30) * 100);
  }

  priorityClass(p: ReplenishmentPriority): string {
    return { CRITIQUE: 'badge--danger', HAUTE: 'badge--warning', MOYENNE: 'badge--info', NORMALE: 'badge--success' }[p];
  }

  // ---------- Vue 3 : valorisation
  protected readonly categorySlices = computed<Slice[]>(() =>
    (this.data()?.valueByCategory ?? []).map((c, i) => ({
      label: c.category, value: Math.round(c.value), color: CATEGORY_COLORS[i % CATEGORY_COLORS.length]
    })));

  protected readonly topValue = computed<BarItem[]>(() =>
    (this.data()?.topValue ?? []).map((p) => ({
      label: p.name, sublabel: `${p.reference} · ${num(p.quantity)} × ${money(p.unitPrice)}`, value: p.value,
      display: moneyRounded(p.value)
    })));

  protected readonly categoryShare = computed(() => {
    const d = this.data();
    const total = d?.kpis.stockValue || 1;
    return (d?.valueByCategory ?? []).map((c) => ({ ...c, share: Math.round((c.value / total) * 1000) / 10 }));
  });
}
