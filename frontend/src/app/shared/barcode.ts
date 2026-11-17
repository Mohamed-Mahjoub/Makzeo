import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011'];
const G = ['0100111', '0110011', '0011011', '0100001', '0011101', '0111001', '0000101', '0010001', '0001001', '0010111'];
const R = ['1110010', '1100110', '1101100', '1000010', '1011100', '1001110', '1010000', '1000100', '1001000', '1110100'];
const PARITY = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLL', 'LGLGLG', 'LGLGGL', 'LGGLGL'];

export interface Bar { x: number; width: number; guard: boolean; }

/** Encode un EAN-13 en 95 modules ; retourne les barres (runs de 1). Null si le code n'est pas un EAN-13. */
export function encodeEan13(code: string): Bar[] | null {
  if (!/^\d{13}$/.test(code)) {
    return null;
  }
  const digits = code.split('').map(Number);
  const parity = PARITY[digits[0]];
  let bits = '101';
  for (let i = 1; i <= 6; i++) {
    bits += parity[i - 1] === 'L' ? L[digits[i]] : G[digits[i]];
  }
  bits += '01010';
  for (let i = 7; i <= 12; i++) {
    bits += R[digits[i]];
  }
  bits += '101';
  const guards = new Set<number>([0, 1, 2, 45, 46, 47, 48, 49, 92, 93, 94]);
  const bars: Bar[] = [];
  let i = 0;
  while (i < bits.length) {
    if (bits[i] === '1') {
      const start = i;
      while (i < bits.length && bits[i] === '1') {
        i++;
      }
      bars.push({ x: start, width: i - start, guard: guards.has(start) });
    } else {
      i++;
    }
  }
  return bars;
}

/** Affiche un code-barres EAN-13 en SVG (ou le code en clair pour un code interne). */
@Component({
  selector: 'app-barcode',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (bars(); as list) {
      <svg class="barcode" viewBox="-9 0 113 64" [attr.width]="width()" role="img" [attr.aria-label]="'EAN-13 ' + code()">
        <rect x="-9" y="0" width="113" height="64" fill="#fff" />
        @for (bar of list; track bar.x) {
          <rect [attr.x]="bar.x" y="2" [attr.width]="bar.width" [attr.height]="bar.guard ? 52 : 46" fill="#111" />
        }
        <text x="-7" y="62" font-size="9" font-family="monospace">{{ code()[0] }}</text>
        <text x="24" y="62" font-size="9" font-family="monospace" text-anchor="middle" letter-spacing="1.2">{{ code().slice(1, 7) }}</text>
        <text x="70" y="62" font-size="9" font-family="monospace" text-anchor="middle" letter-spacing="1.2">{{ code().slice(7) }}</text>
      </svg>
    } @else {
      <code class="barcode-text">{{ code() }}</code>
    }
  `
})
export class Barcode {
  readonly code = input.required<string>();
  readonly width = input(190);
  protected readonly bars = computed(() => encodeEan13(this.code()));
}
