import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';

/** Page de connexion. */
@Component({
  selector: 'app-login',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="login">
      <section class="login__hero">
        <div class="login__brand">
          <svg viewBox="0 0 32 32" width="44" height="44" aria-hidden="true">
            <rect width="32" height="32" rx="7" fill="#0f2742" />
            <path d="M8 11l8-4 8 4v10l-8 4-8-4zM8 11l8 4 8-4M16 15v10" fill="none" stroke="#5eead4"
                  stroke-width="2.2" stroke-linejoin="round" />
          </svg>
          <span>Makzeo</span>
        </div>
        <h1>Pilotez vos stocks en temps réel</h1>
        <p class="login__tagline">Makzeo — de l'arabe <em>makhzen</em>, l'entrepôt, qui a donné « magasin ».</p>
        <ul>
          <li>Catalogue produits, catégories et codes-barres</li>
          <li>Entrées, sorties, inventaires et historique complet</li>
          <li>Alertes de rupture et de seuil minimum</li>
          <li>Tableaux de bord et rapports PDF / Excel</li>
        </ul>
      </section>
      <section class="login__panel">
        <form class="card login__card" (ngSubmit)="submit()">
          <h2>Connexion</h2>
          <p class="muted">Identifiez-vous pour accéder à l'application.</p>
          <label class="field">
            <span>Nom d'utilisateur</span>
            <input name="username" [(ngModel)]="username" autocomplete="username" required autofocus />
          </label>
          <label class="field">
            <span>Mot de passe</span>
            <input name="password" type="password" [(ngModel)]="password" autocomplete="current-password" required />
          </label>
          @if (error()) {
            <div class="form-error">{{ error() }}</div>
          }
          <button class="btn btn--primary btn--block" type="submit" [disabled]="loading()">
            {{ loading() ? 'Connexion…' : 'Se connecter' }}
          </button>
          <p class="login__hint muted">Compte initial : <code>admin</code> / <code>Admin&#64;12345</code></p>
        </form>
      </section>
    </div>
  `
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected username = '';
  protected password = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    if (this.auth.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  submit(): void {
    if (!this.username || !this.password) {
      this.error.set('Veuillez saisir vos identifiants.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
  }
}
