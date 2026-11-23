import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';
import { Shell } from './layout/shell';

/** Routes de l'application (pages chargées à la demande). */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login').then((m) => m.LoginPage) },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: 'Tableau de bord',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardPage) },
      { path: 'products', title: 'Produits',
        loadComponent: () => import('./features/products/product-list').then((m) => m.ProductListPage) },
      { path: 'categories', title: 'Catégories',
        loadComponent: () => import('./features/categories/category-list').then((m) => m.CategoryListPage) },
      { path: 'suppliers', title: 'Fournisseurs',
        loadComponent: () => import('./features/suppliers/supplier-list').then((m) => m.SupplierListPage) },
      { path: 'stock', title: 'Niveaux de stock',
        loadComponent: () => import('./features/stock/stock-levels').then((m) => m.StockLevelsPage) },
      { path: 'movements', title: 'Mouvements',
        loadComponent: () => import('./features/movements/movements').then((m) => m.MovementsPage) },
      { path: 'alerts', title: 'Alertes',
        loadComponent: () => import('./features/alerts/alerts').then((m) => m.AlertsPage) },
      { path: 'inventories', title: 'Inventaires',
        loadComponent: () => import('./features/inventories/inventory-list').then((m) => m.InventoryListPage) },
      { path: 'inventories/:id', title: 'Inventaire',
        loadComponent: () => import('./features/inventories/inventory-detail').then((m) => m.InventoryDetailPage) },
      { path: 'reports', title: 'Rapports', canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE', 'CONSULTANT')],
        loadComponent: () => import('./features/reports/reports').then((m) => m.ReportsPage) },
      { path: 'achats', canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE', 'CONSULTANT')], children: [
        { path: '', pathMatch: 'full', title: 'Achats intelligents',
          loadComponent: () => import('./features/procurement/procurement-dashboard').then((m) => m.ProcurementDashboardPage) },
        { path: 'recommandations', title: 'Recommandations d\'achat',
          loadComponent: () => import('./features/procurement/recommendations').then((m) => m.RecommendationsPage) },
        { path: 'prix', title: 'Prédiction des prix',
          loadComponent: () => import('./features/procurement/price-forecasts').then((m) => m.PriceForecastsPage) },
        { path: 'devis', title: 'Comparaison des offres',
          loadComponent: () => import('./features/procurement/quote-comparison').then((m) => m.QuoteComparisonPage) },
        { path: 'demandes', title: 'Demandes de prix',
          loadComponent: () => import('./features/procurement/rfq-list').then((m) => m.RfqListPage) },
        { path: 'fournisseurs', title: 'Fournisseurs du marché',
          loadComponent: () => import('./features/procurement/market-suppliers').then((m) => m.MarketSuppliersPage) }
      ] },
      { path: 'users', title: 'Utilisateurs', canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/users/user-list').then((m) => m.UserListPage) },
      { path: 'settings', title: 'Paramètres',
        loadComponent: () => import('./features/settings/settings').then((m) => m.SettingsPage) },
      { path: 'profile', title: 'Mon profil',
        loadComponent: () => import('./features/auth/profile').then((m) => m.ProfilePage) }
    ]
  },
  { path: '**', redirectTo: '' }
];
