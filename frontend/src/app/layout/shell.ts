import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ROLE_LABELS, Role } from '../core/models/models';
import { AuthService } from '../core/services/auth.service';
import { SettingsService } from '../core/services/settings.service';
import { Icon } from '../shared/icon';

interface MenuItem {
  path: string;
  label: string;
  icon: string;
  roles?: Role[];
}

interface MenuSection {
  title: string;
  items: MenuItem[];
}

/** Mise en page principale : barre latérale (menu selon le rôle) + en-tête + contenu. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html'
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly settings = inject(SettingsService);
  protected readonly collapsed = signal(false);

  constructor() {
    // Devise de l'application : appliquée à tous les montants affichés
    this.settings.load();
  }

  private readonly sections: MenuSection[] = [
    { title: 'Pilotage', items: [
      { path: '/dashboard', label: 'Tableau de bord', icon: 'dashboard' },
      { path: '/alerts', label: 'Alertes', icon: 'bell' },
      { path: '/reports', label: 'Rapports', icon: 'file', roles: ['ADMIN', 'GESTIONNAIRE', 'CONSULTANT'] }
    ] },
    { title: 'Stock', items: [
      { path: '/movements', label: 'Entrées / sorties', icon: 'swap' },
      { path: '/stock', label: 'Niveaux de stock', icon: 'layers' },
      { path: '/inventories', label: 'Inventaires', icon: 'clipboard' }
    ] },
    { title: 'Référentiel', items: [
      { path: '/products', label: 'Produits', icon: 'box' },
      { path: '/categories', label: 'Catégories', icon: 'tag' },
      { path: '/suppliers', label: 'Fournisseurs', icon: 'truck' }
    ] },
    { title: 'Achats IA', items: [
      { path: '/achats', label: 'Tableau de bord achats', icon: 'coins', roles: ['ADMIN', 'GESTIONNAIRE', 'CONSULTANT'] },
      { path: '/achats/recommandations', label: 'Recommandations', icon: 'cart', roles: ['ADMIN', 'GESTIONNAIRE', 'CONSULTANT'] },
      { path: '/achats/prix', label: 'Prédiction des prix', icon: 'trend', roles: ['ADMIN', 'GESTIONNAIRE', 'CONSULTANT'] },
      { path: '/achats/devis', label: 'Comparaison des offres', icon: 'swap', roles: ['ADMIN', 'GESTIONNAIRE', 'CONSULTANT'] },
      { path: '/achats/demandes', label: 'Demandes de prix', icon: 'file', roles: ['ADMIN', 'GESTIONNAIRE', 'CONSULTANT'] },
      { path: '/achats/fournisseurs', label: 'Fournisseurs du marché', icon: 'globe', roles: ['ADMIN', 'GESTIONNAIRE', 'CONSULTANT'] }
    ] },
    { title: 'Administration', items: [
      { path: '/users', label: 'Utilisateurs', icon: 'users', roles: ['ADMIN'] },
      { path: '/settings', label: 'Paramètres', icon: 'settings' }
    ] }
  ];

  protected readonly menu = computed(() => {
    this.auth.user();
    return this.sections
      .map((s) => ({ ...s, items: s.items.filter((i) => !i.roles || this.auth.hasAnyRole(...i.roles)) }))
      .filter((s) => s.items.length > 0);
  });

  protected readonly roleLabel = computed(() => {
    const user = this.auth.user();
    return user ? user.roles.map((r) => ROLE_LABELS[r]).join(', ') : '';
  });

  protected readonly initials = computed(() => {
    const name = this.auth.user()?.fullName ?? '';
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  });
}
