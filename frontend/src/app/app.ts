import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationService } from './core/services/notification.service';
import { ThemeService } from './core/services/theme.service';

/** Composant racine : routeur + zone des notifications. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <div class="toasts" aria-live="polite">
      @for (toast of notifications.toasts(); track toast.id) {
        <div class="toast" [class]="'toast toast--' + toast.type" (click)="notifications.dismiss(toast.id)">
          {{ toast.message }}
        </div>
      }
    </div>
  `
})
export class App {
  protected readonly notifications = inject(NotificationService);

  constructor() {
    // thème appliqué dès le démarrage (choix mémorisé ou préférence du système)
    inject(ThemeService);
  }
}
