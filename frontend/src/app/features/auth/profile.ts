import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROLE_LABELS } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeader } from '../../shared/page-header';

/** Profil de l'utilisateur connecté et changement de mot de passe. */
@Component({
  selector: 'app-profile',
  imports: [FormsModule, DatePipe, PageHeader],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Mon profil" subtitle="Informations du compte et sécurité" />
    <div class="grid grid--2">
      @if (auth.user(); as user) {
        <section class="card">
          <h3>Compte</h3>
          <dl class="details">
            <dt>Nom complet</dt><dd>{{ user.fullName }}</dd>
            <dt>Utilisateur</dt><dd>{{ user.username }}</dd>
            <dt>Email</dt><dd>{{ user.email }}</dd>
            <dt>Rôle(s)</dt>
            <dd>@for (r of user.roles; track r) { <span class="badge badge--info">{{ roleLabels[r] }}</span> }</dd>
            <dt>Dernière connexion</dt><dd>{{ user.lastLoginAt | date: 'dd/MM/yyyy HH:mm' }}</dd>
          </dl>
        </section>
      }
      <section class="card">
        <h3>Changer mon mot de passe</h3>
        <form (ngSubmit)="changePassword()" class="form">
          <label class="field"><span>Mot de passe actuel</span>
            <input type="password" name="current" [(ngModel)]="current" required /></label>
          <label class="field"><span>Nouveau mot de passe (8 caractères minimum)</span>
            <input type="password" name="next" [(ngModel)]="next" required minlength="8" /></label>
          <label class="field"><span>Confirmation</span>
            <input type="password" name="confirm" [(ngModel)]="confirm" required /></label>
          <button class="btn btn--primary" type="submit" [disabled]="saving()">Mettre à jour</button>
        </form>
      </section>
    </div>
  `
})
export class ProfilePage {
  protected readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly saving = signal(false);
  protected current = '';
  protected next = '';
  protected confirm = '';

  changePassword(): void {
    if (this.next.length < 8) {
      this.notify.error('Le nouveau mot de passe doit contenir au moins 8 caractères.');
      return;
    }
    if (this.next !== this.confirm) {
      this.notify.error('La confirmation ne correspond pas.');
      return;
    }
    this.saving.set(true);
    this.auth.changePassword(this.current, this.next).subscribe({
      next: () => {
        this.notify.success('Mot de passe modifié.');
        this.current = this.next = this.confirm = '';
        this.saving.set(false);
      },
      error: (err) => {
        this.notify.error(errorMessage(err));
        this.saving.set(false);
      }
    });
  }
}
