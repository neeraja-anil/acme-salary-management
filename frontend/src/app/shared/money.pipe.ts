import { Pipe, PipeTransform } from '@angular/core';

/** INR uses Indian digit grouping (₹18,00,000); everything else uses the usual thousands grouping. */
function localeFor(currency: string): string {
  return currency === 'INR' ? 'en-IN' : 'en-US';
}

const formatters = new Map<string, Intl.NumberFormat>();

function formatter(currency: string, short: boolean): Intl.NumberFormat {
  const key = `${currency}:${short}`;
  let format = formatters.get(key);
  if (!format) {
    format = new Intl.NumberFormat(localeFor(currency), {
      style: 'currency',
      currency,
      maximumFractionDigits: short ? 1 : 0,
      notation: short ? 'compact' : 'standard',
    });
    formatters.set(key, format);
  }
  return format;
}

/** Whole-unit money: €78,000, $84,630, ₹18,00,000. The short form ($812.3M) is for cards and charts. */
export function formatMoney(amount: number, currency: string, short = false): string {
  return formatter(currency, short).format(amount);
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency: string, style: 'full' | 'short' = 'full'): string {
    return amount == null ? '–' : formatMoney(amount, currency, style === 'short');
  }
}
