import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/models';

/** Extrait un message lisible d'une erreur HTTP (format ApiError du backend). */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Serveur injoignable : vérifiez que les microservices sont démarrés.';
    }
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.message) {
      if (body.violations && body.violations.length > 0) {
        return body.message + ' : ' + body.violations.map((v) => `${v.field} ${v.message}`).join(', ');
      }
      return body.message;
    }
    if (error.status === 403) {
      return 'Accès refusé : droits insuffisants.';
    }
    return `Erreur ${error.status} : ${error.statusText}`;
  }
  return 'Erreur inattendue';
}
