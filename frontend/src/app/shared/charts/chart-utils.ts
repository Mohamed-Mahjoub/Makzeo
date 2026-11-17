/** Utilitaires d'échelle pour les graphiques SVG. */

/** Arrondit un maximum à une valeur "ronde" (1, 2, 2.5, 5 x 10^n). */
export function niceMax(value: number): number {
  if (value <= 0) {
    return 1;
  }
  const pow = Math.pow(10, Math.floor(Math.log10(value)));
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (m * pow >= value) {
      return m * pow;
    }
  }
  return 10 * pow;
}

export function ticks(max: number, count = 4): number[] {
  return Array.from({ length: count + 1 }, (_, i) => (max / count) * i);
}

/** Formatage compact des nombres (1 200 -> 1,2 k). */
export function compact(value: number): string {
  const abs = Math.abs(value);
  if (abs >= 1_000_000) {
    return (value / 1_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' M';
  }
  if (abs >= 1_000) {
    return (value / 1_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' k';
  }
  return value.toLocaleString('fr-FR', { maximumFractionDigits: 1 });
}

export const PALETTE = {
  primary: '#2563eb',
  teal: '#0d9488',
  amber: '#d97706',
  red: '#dc2626',
  green: '#16a34a',
  violet: '#7c3aed',
  slate: '#64748b'
};
