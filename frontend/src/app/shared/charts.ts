import { provideCharts, withDefaultRegisterables } from 'ng2-charts';

/**
 * Chart.js set up to match the app: Inter, muted axis labels, light gridlines.
 * The colours are the theme's slate-500 (muted text) and slate-200 (borders). Provide it on the pages
 * that draw charts, so Chart.js loads only with them.
 */
export function provideAppCharts() {
  return provideCharts(withDefaultRegisterables(), {
    defaults: {
      font: { family: "'Inter Variable', Inter, system-ui, sans-serif", size: 12 },
      color: '#64748b',
      borderColor: '#e2e8f0',
      scale: { grid: { color: '#e2e8f0' }, border: { display: false } },
      plugins: {
        tooltip: {
          backgroundColor: '#0f172a',
          padding: 10,
          cornerRadius: 8,
          titleFont: { weight: 600 },
          displayColors: false,
        },
      },
    },
  });
}

/** A theme colour (a CSS custom property such as --chart-bar), read when a chart is drawn. */
export function cssColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}
