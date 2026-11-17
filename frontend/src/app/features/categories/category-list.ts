import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Category } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { errorMessage } from '../../core/services/error-message';
import { NotificationService } from '../../core/services/notification.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { PageHeader } from '../../shared/page-header';

/** Gestion des catégories de produits. */
@Component({
  selector: 'app-category-list',
  imports: [FormsModule, PageHeader, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Catégories" subtitle="Classement des produits du catalogue">
      @if (auth.canManageCatalog()) {
        <button type="button" class="btn btn--primary" (click)="open(null)"><app-icon name="plus" [size]="16" /> Nouvelle catégorie</button>
      }
    </app-page-header>
    <section class="card">
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>#</th><th>Nom</th><th>Description</th><th></th></tr></thead>
          <tbody>
            @for (c of categories(); track c.id) {
              <tr>
                <td class="muted">{{ c.id }}</td>
                <td><strong>{{ c.name }}</strong></td>
                <td>{{ c.description ?? '—' }}</td>
                <td class="actions">
                  @if (auth.canManageCatalog()) {
                    <button type="button" class="icon-btn" (click)="open(c)" title="Modifier"><app-icon name="edit" [size]="16" /></button>
                    <button type="button" class="icon-btn icon-btn--danger" (click)="remove(c)" title="Supprimer"><app-icon name="x" [size]="16" /></button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="4" class="center muted">Aucune catégorie.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    @if (formOpen()) {
      <app-modal [title]="editing() ? 'Modifier la catégorie' : 'Nouvelle catégorie'" (closed)="formOpen.set(false)">
        <form class="form" (ngSubmit)="save()" #f="ngForm">
          <label class="field"><span>Nom *</span><input name="name" [(ngModel)]="name" required maxlength="100" /></label>
          <label class="field"><span>Description</span>
            <textarea name="description" rows="3" [(ngModel)]="description" maxlength="500"></textarea></label>
          <div class="form__actions">
            <button type="button" class="btn btn--ghost" (click)="formOpen.set(false)">Annuler</button>
            <button type="submit" class="btn btn--primary" [disabled]="f.invalid">Enregistrer</button>
          </div>
        </form>
      </app-modal>
    }
  `
})
export class CategoryListPage {
  private readonly catalog = inject(CatalogService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  protected readonly categories = signal<Category[]>([]);
  protected readonly editing = signal<Category | null>(null);
  protected readonly formOpen = signal(false);
  protected name = '';
  protected description: string | null = null;

  constructor() {
    this.load();
  }

  load(): void {
    this.catalog.categories().subscribe({
      next: (c) => this.categories.set(c),
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  open(category: Category | null): void {
    this.editing.set(category);
    this.name = category?.name ?? '';
    this.description = category?.description ?? null;
    this.formOpen.set(true);
  }

  save(): void {
    const body = { name: this.name, description: this.description || null };
    const editing = this.editing();
    const request = editing ? this.catalog.updateCategory(editing.id, body) : this.catalog.createCategory(body);
    request.subscribe({
      next: () => {
        this.notify.success('Catégorie enregistrée.');
        this.formOpen.set(false);
        this.load();
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }

  remove(category: Category): void {
    if (!confirm(`Supprimer la catégorie « ${category.name} » ?`)) {
      return;
    }
    this.catalog.deleteCategory(category.id).subscribe({
      next: () => {
        this.notify.success('Catégorie supprimée.');
        this.load();
      },
      error: (err) => this.notify.error(errorMessage(err))
    });
  }
}
