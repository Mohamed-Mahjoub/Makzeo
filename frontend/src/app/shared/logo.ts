import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let uid = 0;

/** Logo Makzeo (cube d'entrepôt) en SVG. */
@Component({
  selector: 'app-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 64 64" [attr.width]="size()" [attr.height]="size()" role="img" aria-label="Makzeo">
      <defs>
        <linearGradient [attr.id]="id" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#6366f1" />
          <stop offset="1" stop-color="#14b8a6" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" [attr.fill]="'url(#' + id + ')'" />
      <path d="M32 12 50 21.5v21L32 52 14 42.5v-21z" fill="rgba(255,255,255,.12)" stroke="#fff" stroke-width="3.2" stroke-linejoin="round" />
      <path d="M14 21.5 32 31l18-9.5M32 31v21" fill="none" stroke="#fff" stroke-width="3.2" stroke-linejoin="round" />
      <path d="M23 16.8 41 26.3" fill="none" stroke="rgba(255,255,255,.55)" stroke-width="2.4" stroke-linecap="round" />
    </svg>
  `,
  styles: [':host { display: inline-flex; line-height: 0; }']
})
export class Logo {
  readonly size = input(32);
  protected readonly id = `mkz-grad-${uid++}`;
}
