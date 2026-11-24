import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'makzeo.theme';

/** Thème clair / sombre : choix mémorisé dans le navigateur, sinon préférence du système. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Theme>(this.initial());

  readonly theme = this.current.asReadonly();

  constructor() {
    this.apply(this.current());
  }

  toggle(): void {
    const next: Theme = this.current() === 'dark' ? 'light' : 'dark';
    const doc = this.document as Document & { startViewTransition?: (cb: () => void) => unknown };
    const change = () => {
      this.current.set(next);
      this.apply(next);
      try { localStorage.setItem(STORAGE_KEY, next); } catch { /* stockage indisponible */ }
    };
    // fondu entre les deux thèmes quand le navigateur le permet
    if (doc.startViewTransition && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
      doc.startViewTransition(change);
    } else {
      change();
    }
  }

  private apply(theme: Theme): void {
    this.document.documentElement.dataset['theme'] = theme;
    this.document.documentElement.style.colorScheme = theme;
  }

  private initial(): Theme {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') { return saved; }
    } catch { /* stockage indisponible */ }
    return typeof matchMedia !== 'undefined' && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
