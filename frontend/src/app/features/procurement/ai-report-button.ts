import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { ReportService } from '../../core/services/report.service';
import { Icon } from '../../shared/icon';

/** Télécharge le rapport PDF imprimable de l'agent IA (synthèse de l'apport du module achats). */
@Component({
  selector: 'app-ai-report-button',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" [class]="'btn ' + (primary() ? 'btn--primary' : 'btn--ghost')" [disabled]="busy()" (click)="download()"
            title="Rapport de synthèse imprimable : économies, recommandations, meilleures offres, prédictions">
      <app-icon name="file" [size]="16" /> {{ busy() ? 'Génération…' : 'Rapport PDF de l’agent IA' }}
    </button>
  `
})
export class AiReportButton {
  private readonly reports = inject(ReportService);
  private readonly notify = inject(NotificationService);
  readonly primary = input(false);
  protected readonly busy = signal(false);

  download(): void {
    this.busy.set(true);
    this.reports.download('/api/procurement/report/pdf', {}).subscribe({
      next: (file) => {
        this.busy.set(false);
        this.notify.success(`Rapport téléchargé : ${file}`);
      },
      error: (err) => {
        this.busy.set(false);
        this.notify.error(errorMessage(err));
      }
    });
  }
}
