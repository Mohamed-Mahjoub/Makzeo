import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Analytics } from '../models/models';
import { api, toParams } from './api';

export type ReportFormat = 'pdf' | 'xlsx';

/** Appels vers reporting-service : indicateurs du tableau de bord et fichiers PDF / Excel. */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  analytics(days: number, categoryId: number | null): Observable<Analytics> {
    return this.http.get<Analytics>(api('/api/reports/analytics'), { params: toParams({ days, categoryId }) });
  }

  /** Télécharge un rapport et déclenche l'enregistrement du fichier dans le navigateur. */
  download(path: string, params: object): Observable<string> {
    return this.http
      .get(api(path), { params: toParams(params), responseType: 'blob', observe: 'response' })
      .pipe(map((response) => this.save(response)));
  }

  private save(response: HttpResponse<Blob>): string {
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    const plain = /filename="?([^";]+)"?/i.exec(disposition);
    const fileName = utf8 ? decodeURIComponent(utf8[1]) : plain ? plain[1] : 'rapport';
    const url = URL.createObjectURL(response.body as Blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    return fileName;
  }
}
