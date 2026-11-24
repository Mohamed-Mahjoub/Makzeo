import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyOption } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { SettingsService } from '../../core/services/settings.service';
import { Icon } from '../../shared/icon';
import { PageHeader } from '../../shared/page-header';

/** Paramètres de l'application : choix de la devise (modifiable par l'administrateur). */
@Component({
  selector: 'app-settings',
  imports: [FormsModule, DatePipe, PageHeader, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Paramètres" subtitle="Configuration générale de l'application" />
    <div class="grid grid--2">
      <section class="card">
        <div class="card__title"><h3>Devise de l'application</h3></div>
        @if (settingsService.settings(); as s) {
          <div class="currency-current">
            <span class="currency-current__code">{{ s.currency }}</span>
            <div>
              <strong>{{ s.currencyName }}</strong>
              <small class="muted">Symbole « {{ s.currencySymbol }} » · {{ s.currencyFractionDigits }} décimale(s)</small>
              @if (s.updatedBy) {
                <small class="muted">Modifiée par {{ s.updatedBy }} le {{ s.updatedAt | date: 'dd/MM/yyyy HH:mm' }}</small>
              }
            </div>
          </div>
        }

        @if (auth.isAdmin()) {
          <form class="form" (ngSubmit)="save()">
            <label class="field"><span>Nouvelle devise</span>
              <select name="currency" [ngModel]="selected()" (ngModelChange)="selected.set($event)">
                <optgroup label="Devises courantes">
                  @for (c of commonCurrencies(); track c.code) {
                    <option [ngValue]="c.code">{{ c.code }} · {{ c.name }}</option>
                  }
                </optgroup>
                <optgroup label="Toutes les devises">
                  @for (c of otherCurrencies(); track c.code) {
                    <option [ngValue]="c.code">{{ c.code }} · {{ c.name }}</option>
                  }
                </optgroup>
              </select>
            </label>
            @if (preview(); as p) {
              <p class="muted small">Aperçu : <strong>{{ p }}</strong></p>
            }
            <div class="notice">
              <app-icon name="alert" [size]="16" />
              <span>Aucune conversion n'est appliquée : les prix déjà saisis sont simplement affichés dans la
                nouvelle devise. Choisissez la devise avant de saisir le catalogue.</span>
            </div>
            <div class="form__actions">
              <button type="submit" class="btn btn--primary"
                      [disabled]="saving() || selected() === settingsService.settings()?.currency">Enregistrer</button>
            </div>
          </form>
        } @else {
          <p class="muted">Seul un administrateur peut modifier la devise.</p>
        }
      </section>

      <section class="card">
        <div class="card__title"><h3>À propos</h3></div>
        <dl class="details">
          <dt>Application</dt><dd><strong>Makzeo</strong> · gestion de produits et de stock</dd>
          <dt>Origine du nom</dt><dd>De l'arabe <em>makhzen</em> (entrepôt), qui a donné le mot français « magasin ».</dd>
          <dt>Architecture</dt><dd>5 microservices Spring Boot · frontend Angular</dd>
          <dt>Montants</dt><dd>Saisis et affichés dans la devise de l'application (jusqu'à 3 décimales)</dd>
        </dl>
      </section>
    </div>
  `
})
export class SettingsPage {
  protected readonly settingsService = inject(SettingsService);
  protected readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);

  protected readonly currencies = signal<CurrencyOption[]>([]);
  protected readonly selected = signal<string>('');
  protected readonly saving = signal(false);

  protected readonly commonCurrencies = computed(() => this.currencies().filter((c) => c.common));
  protected readonly otherCurrencies = computed(() => this.currencies().filter((c) => !c.common));

  protected readonly preview = computed(() => {
    const option = this.currencies().find((c) => c.code === this.selected());
    if (!option) {
      return null;
    }
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency', currency: option.code,
      minimumFractionDigits: option.fractionDigits, maximumFractionDigits: option.fractionDigits
    }).format(1234567.891);
  });

  constructor() {
    this.settingsService.load();
    this.settingsService.currencies().subscribe({
      next: (list) => {
        this.currencies.set(list);
        this.selected.set(this.settingsService.settings()?.currency ?? list[0]?.code ?? '');
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  save(): void {
    this.saving.set(true);
    this.settingsService.updateCurrency(this.selected()).subscribe({
      next: (s) => {
        this.notify.success(`Devise de l'application : ${s.currencyName} (${s.currency}).`);
        this.saving.set(false);
      },
      error: (err) => {
        this.notify.error(errorMessage(err));
        this.saving.set(false);
      }
    });
  }
}
