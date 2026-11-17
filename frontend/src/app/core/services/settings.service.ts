import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CurrencyOption, Settings } from '../models/models';
import { setCurrency } from '../../shared/format';
import { api } from './api';

/** Paramètres de l'application (devise), servis par auth-service : /api/settings. */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);

  readonly settings = signal<Settings | null>(null);

  /** Charge les paramètres et applique la devise à tout l'affichage. */
  load(): void {
    this.http.get<Settings>(api('/api/settings')).subscribe({
      next: (s) => this.apply(s),
      error: () => undefined
    });
  }

  currencies(): Observable<CurrencyOption[]> {
    return this.http.get<CurrencyOption[]>(api('/api/settings/currencies'));
  }

  updateCurrency(currency: string): Observable<Settings> {
    return this.http.put<Settings>(api('/api/settings'), { currency }).pipe(tap((s) => this.apply(s)));
  }

  private apply(s: Settings): void {
    this.settings.set(s);
    setCurrency(s.currency, s.currencyFractionDigits);
  }
}
