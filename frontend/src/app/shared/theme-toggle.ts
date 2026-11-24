import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ThemeService } from '../core/services/theme.service';
import { Icon } from './icon';

/** Bouton de bascule entre le thème clair et le thème sombre. */
@Component({
  selector: 'app-theme-toggle',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="theme-toggle" (click)="theme.toggle()"
            [attr.aria-label]="theme.theme() === 'dark' ? 'Passer au thème clair' : 'Passer au thème sombre'"
            [title]="theme.theme() === 'dark' ? 'Thème clair' : 'Thème sombre'">
      <span class="theme-toggle__icon" [class.is-dark]="theme.theme() === 'dark'">
        <app-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" [size]="18" />
      </span>
    </button>
  `
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
}
