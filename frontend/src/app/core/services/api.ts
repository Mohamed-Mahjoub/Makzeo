import { HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/** Construit l'URL complète d'une route d'API. */
export function api(path: string): string {
  return `${environment.apiUrl}${path}`;
}

/** Transforme un objet de filtres en HttpParams (ignore les valeurs vides). */
export function toParams(filters: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(filters as Record<string, unknown>)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
