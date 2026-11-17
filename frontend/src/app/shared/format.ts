import { signal } from '@angular/core';

/**
 * Fonctions de formatage (locale fr-FR).
 * La devise est un paramètre de l'application (page Paramètres) : elle est conservée dans un signal,
 * si bien que tous les écrans qui affichent des montants se mettent à jour quand elle change.
 */

export interface CurrencySetting {
  code: string;
  fractionDigits: number;
}

const currencySignal = signal<CurrencySetting>({ code: 'MAD', fractionDigits: 2 });

/** Devise courante (lecture réactive). */
export const currentCurrency = currencySignal.asReadonly();

export function setCurrency(code: string, fractionDigits: number): void {
  currencySignal.set({ code, fractionDigits });
}

const numberFormat = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });

/** Montant avec symbole et toutes les décimales de la devise (ex. 1 234,567 TND). */
export function money(value: number | null | undefined): string {
  const c = currencySignal();
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency', currency: c.code,
    minimumFractionDigits: c.fractionDigits, maximumFractionDigits: c.fractionDigits
  }).format(value ?? 0);
}

/** Montant arrondi à l'unité, pour les indicateurs (ex. 58 775 €). */
export function moneyRounded(value: number | null | undefined): string {
  const c = currencySignal();
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency', currency: c.code, minimumFractionDigits: 0, maximumFractionDigits: 0
  }).format(value ?? 0);
}

/** Montant sans symbole, avec les décimales de la devise (colonnes de prix). */
export function amount(value: number | null | undefined): string {
  const c = currencySignal();
  return new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: c.fractionDigits, maximumFractionDigits: c.fractionDigits
  }).format(value ?? 0);
}

/** Pas de saisie des prix selon la devise (0.01, 0.001...). */
export function priceStep(): string {
  const digits = currencySignal().fractionDigits;
  return digits === 0 ? '1' : '0.' + '0'.repeat(digits - 1) + '1';
}

export function num(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : numberFormat.format(value);
}

/** 2026-09-26 -> 26/09 */
export function shortDate(iso: string): string {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}

/** Date du jour au format AAAA-MM-JJ (fuseau local). */
export function today(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
