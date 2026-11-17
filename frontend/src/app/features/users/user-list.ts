import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageResult, ROLE_LABELS, Role, User } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { UserService } from '../../core/services/user.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';
import { Pagination } from '../../shared/pagination';

interface UserForm {
  username: string;
  email: string;
  fullName: string;
  password: string;
  roles: Record<Role, boolean>;
}

const ROLES: Role[] = ['ADMIN', 'GESTIONNAIRE', 'MAGASINIER', 'CONSULTANT'];

function emptyForm(): UserForm {
  return { username: '', email: '', fullName: '', password: '',
    roles: { ADMIN: false, GESTIONNAIRE: false, MAGASINIER: true, CONSULTANT: false } };
}

/** Administration des utilisateurs et de leurs rôles (ADMIN). */
@Component({
  selector: 'app-user-list',
  imports: [FormsModule, DatePipe, PageHeader, Pagination, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Utilisateurs" subtitle="Comptes, rôles et droits d'accès">
      <button type="button" class="btn btn--primary" (click)="open(null)"><app-icon name="plus" [size]="16" /> Nouvel utilisateur</button>
    </app-page-header>
    <section class="card">
      <form class="toolbar" (ngSubmit)="load()">
        <div class="search"><app-icon name="search" [size]="16" /><input name="q" [(ngModel)]="q" placeholder="Nom, email…" /></div>
        <button type="submit" class="btn btn--ghost">Rechercher</button>
      </form>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Utilisateur</th><th>Nom</th><th>Email</th><th>Rôles</th><th>État</th><th>Dernière connexion</th><th></th></tr></thead>
          <tbody>
            @for (u of result()?.content ?? []; track u.id) {
              <tr [class.row--muted]="!u.enabled">
                <td><strong>{{ u.username }}</strong></td>
                <td>{{ u.fullName }}</td>
                <td>{{ u.email }}</td>
                <td>@for (r of u.roles; track r) { <span class="badge badge--info">{{ roleLabels[r] }}</span> }</td>
                <td><span class="badge" [class.badge--success]="u.enabled">{{ u.enabled ? 'Actif' : 'Désactivé' }}</span></td>
                <td>{{ u.lastLoginAt ? (u.lastLoginAt | date: 'dd/MM/yyyy HH:mm') : 'Jamais' }}</td>
                <td class="actions">
                  <button type="button" class="icon-btn" (click)="open(u)" title="Modifier"><app-icon name="edit" [size]="16" /></button>
                  <button type="button" class="icon-btn" (click)="resetPassword(u)" title="Réinitialiser le mot de passe"><app-icon name="key" [size]="16" /></button>
                  @if (u.username !== auth.user()?.username) {
                    <button type="button" class="icon-btn" (click)="toggle(u)" [title]="u.enabled ? 'Désactiver' : 'Activer'"><app-icon name="power" [size]="16" /></button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination [page]="page()" [totalPages]="result()?.totalPages ?? 0" [totalElements]="result()?.totalElements ?? 0"
                      (pageChange)="load($event)" />
    </section>

    @if (formOpen()) {
      <app-modal [title]="editing() ? 'Modifier ' + editing()!.username : 'Nouvel utilisateur'" (closed)="formOpen.set(false)">
        <form class="form" (ngSubmit)="save()" #f="ngForm">
          @if (!editing()) {
            <label class="field"><span>Nom d'utilisateur *</span>
              <input name="username" [(ngModel)]="form.username" required minlength="3" maxlength="50" pattern="[a-zA-Z0-9._-]+" /></label>
          }
          <label class="field"><span>Nom complet *</span><input name="fullName" [(ngModel)]="form.fullName" required maxlength="120" /></label>
          <label class="field"><span>Email *</span><input type="email" name="email" [(ngModel)]="form.email" required email maxlength="120" /></label>
          @if (!editing()) {
            <label class="field"><span>Mot de passe * (8 caractères minimum)</span>
              <input type="password" name="password" [(ngModel)]="form.password" required minlength="8" /></label>
          }
          <fieldset class="field">
            <span>Rôles *</span>
            <div class="checks">
              @for (r of roles; track r) {
                <label><input type="checkbox" [name]="'role-' + r" [(ngModel)]="form.roles[r]" /> {{ roleLabels[r] }}</label>
              }
            </div>
          </fieldset>
          <div class="form__actions">
            <button type="button" class="btn btn--ghost" (click)="formOpen.set(false)">Annuler</button>
            <button type="submit" class="btn btn--primary" [disabled]="f.invalid">Enregistrer</button>
          </div>
        </form>
      </app-modal>
    }
  `
})
export class UserListPage {
  private readonly api = inject(UserService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly roles = ROLES;
  protected readonly roleLabels = ROLE_LABELS;

  protected q = '';
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<User> | null>(null);
  protected readonly editing = signal<User | null>(null);
  protected readonly formOpen = signal(false);
  protected form: UserForm = emptyForm();

  constructor() {
    this.load();
  }

  load(page = 0): void {
    this.page.set(page);
    this.api.search({ q: this.q, page, size: 20 }).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  open(u: User | null): void {
    this.editing.set(u);
    this.form = u
      ? { username: u.username, email: u.email, fullName: u.fullName, password: '',
          roles: { ADMIN: u.roles.includes('ADMIN'), GESTIONNAIRE: u.roles.includes('GESTIONNAIRE'),
            MAGASINIER: u.roles.includes('MAGASINIER'), CONSULTANT: u.roles.includes('CONSULTANT') } }
      : emptyForm();
    this.formOpen.set(true);
  }

  save(): void {
    const roles = ROLES.filter((r) => this.form.roles[r]);
    if (roles.length === 0) {
      this.notify.error('Sélectionnez au moins un rôle.');
      return;
    }
    const editing = this.editing();
    const request = editing
      ? this.api.update(editing.id, { email: this.form.email, fullName: this.form.fullName, roles })
      : this.api.create({ username: this.form.username, email: this.form.email, fullName: this.form.fullName,
          password: this.form.password, roles });
    request.subscribe({
      next: () => {
        this.notify.success('Utilisateur enregistré.');
        this.formOpen.set(false);
        this.load(this.page());
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  toggle(u: User): void {
    this.api.setEnabled(u.id, !u.enabled).subscribe({
      next: () => this.load(this.page()),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  resetPassword(u: User): void {
    const password = prompt(`Nouveau mot de passe pour ${u.username} (8 caractères minimum) :`);
    if (!password) {
      return;
    }
    this.api.resetPassword(u.id, password).subscribe({
      next: () => this.notify.success('Mot de passe réinitialisé.'),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
