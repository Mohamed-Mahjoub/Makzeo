import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { Icon } from '../../shared/icon';
import { Logo } from '../../shared/logo';
import { ThemeToggle } from '../../shared/theme-toggle';

/** Page de connexion. */
@Component({
  selector: 'app-login',
  imports: [FormsModule, Icon, Logo, ThemeToggle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="login">
      <section class="login__hero">
        <div class="login__logo">
          <app-logo [size]="112" />
          <div class="login__wordmark">Makzeo<small>Gestion des produits et des stocks</small></div>
        </div>

        <div class="login__intro">
          <h1>Votre catalogue, vos stocks et vos achats au même endroit.</h1>
          <p>Connectez-vous avec le compte fourni par votre administrateur.</p>
          <div class="login__features">
            <div class="login__feature"><span><app-icon name="barcode" [size]="18" /></span><strong>Produits et codes-barres</strong></div>
            <div class="login__feature"><span><app-icon name="swap" [size]="18" /></span><strong>Entrées, sorties et inventaires</strong></div>
            <div class="login__feature"><span><app-icon name="bell" [size]="18" /></span><strong>Alertes de stock</strong></div>
            <div class="login__feature"><span><app-icon name="chart" [size]="18" /></span><strong>Achats et rapports</strong></div>
          </div>
        </div>

        <p class="login__foot">Makzeo · {{ year }}</p>
      </section>

      <section class="login__panel">
        <app-theme-toggle />
        <form class="card login__card" (ngSubmit)="submit()">
          <div class="login__mobile-logo"><app-logo [size]="56" /> Makzeo</div>
          <div>
            <h2>Connexion</h2>
            <p class="muted">Saisissez votre identifiant et votre mot de passe.</p>
          </div>
          <label class="field">
            <span>Nom d'utilisateur</span>
            <input name="username" [(ngModel)]="username" autocomplete="username" required autofocus />
          </label>
          <label class="field">
            <span>Mot de passe</span>
            <span class="password-field">
              <input name="password" [type]="showPassword() ? 'text' : 'password'" [(ngModel)]="password"
                     autocomplete="current-password" required />
              <button type="button" class="icon-btn" (click)="showPassword.set(!showPassword())"
                      [attr.aria-label]="showPassword() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'">
                <app-icon [name]="showPassword() ? 'x' : 'key'" [size]="16" />
              </button>
            </span>
          </label>
          @if (error()) {
            <div class="form-error">{{ error() }}</div>
          }
          <button class="btn btn--primary btn--block" type="submit" [disabled]="loading()">
            {{ loading() ? 'Connexion…' : 'Se connecter' }}
          </button>
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
  protected readonly showPassword = signal(false);
  protected readonly year = new Date().getFullYear();

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
