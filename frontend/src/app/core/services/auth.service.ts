import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResult, Role, User } from '../models/models';
import { api } from './api';

const STORAGE_KEY = 'makzeo.session';

interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

/** Authentification : connexion, stockage du jeton JWT et informations sur l'utilisateur. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly token = computed(() => this.session()?.token ?? null);

  login(username: string, password: string): Observable<AuthResult> {
    return this.http.post<AuthResult>(api('/api/auth/login'), { username, password }).pipe(
      tap((result) => this.store({ token: result.accessToken, expiresAt: result.expiresAt, user: result.user }))
    );
  }

  logout(redirect = true): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  refreshProfile(): void {
    this.http.get<User>(api('/api/auth/me')).subscribe((user) => {
      const current = this.session();
      if (current) {
        this.store({ ...current, user });
      }
    });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(api('/api/auth/me/password'), { currentPassword, newPassword });
  }

  hasAnyRole(...roles: Role[]): boolean {
    const user = this.user();
    return !!user && user.roles.some((r) => roles.includes(r));
  }

  // Raccourcis correspondant à la matrice des droits du backend
  canManageCatalog(): boolean { return this.hasAnyRole('ADMIN', 'GESTIONNAIRE'); }
  canMoveStock(): boolean { return this.hasAnyRole('ADMIN', 'GESTIONNAIRE', 'MAGASINIER'); }
  canReport(): boolean { return this.hasAnyRole('ADMIN', 'GESTIONNAIRE', 'CONSULTANT'); }
  isAdmin(): boolean { return this.hasAnyRole('ADMIN'); }

  private store(session: Session): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as Session;
      if (new Date(session.expiresAt).getTime() <= Date.now()) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      return null;
    }
  }
}
