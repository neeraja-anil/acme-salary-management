import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * The app's PrimeNG theme: Aura with an indigo primary colour, adjusted where Aura's defaults miss
 * WCAG AA contrast. Change the look here, through design tokens,
 * rather than by overriding PrimeNG's classes in CSS.
 */
export const AcmePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{indigo.50}',
      100: '{indigo.100}',
      200: '{indigo.200}',
      300: '{indigo.300}',
      400: '{indigo.400}',
      500: '{indigo.500}',
      600: '{indigo.600}',
      700: '{indigo.700}',
      800: '{indigo.800}',
      900: '{indigo.900}',
      950: '{indigo.950}',
    },
    // A thicker ring than Aura's 1px, so keyboard focus is easy to see (WCAG 2.4.7).
    focusRing: { width: '2px', style: 'solid', color: '{primary.color}', offset: '2px', shadow: 'none' },
    colorScheme: {
      light: {
        // White on indigo-500 is 4.47:1, just under AA; indigo-600 is 6.3:1.
        primary: {
          color: '{primary.600}',
          contrastColor: '#ffffff',
          hoverColor: '{primary.700}',
          activeColor: '{primary.800}',
        },
        highlight: {
          background: '{primary.50}',
          focusBackground: '{primary.100}',
          color: '{primary.700}',
          focusColor: '{primary.800}',
        },
        // Aura's slate-300 input border is 1.5:1 against white; this is 3.15:1 (WCAG 1.4.11).
        formField: {
          borderColor: '#8592a6',
          hoverBorderColor: '{surface.500}',
        },
      },
    },
  },
  components: {
    // Comfortable rows and quiet headers for data tables.
    datatable: {
      headerCell: { padding: '0.75rem 1rem' },
      bodyCell: { padding: '0.875rem 1rem' },
      columnTitle: { fontWeight: '600' },
      colorScheme: {
        light: {
          headerCell: { background: '{surface.50}', color: '{text.muted.color}' },
          row: { hoverBackground: '{surface.50}' },
        },
      },
    },
    card: {
      root: { borderRadius: '12px' },
      body: { padding: '1.5rem', gap: '1rem' },
      caption: { gap: '0.25rem' },
      title: { fontSize: '1.143rem', fontWeight: '600' },
    },
    // Tabs sit straight on the page background, so their bar and panels are transparent.
    tabs: {
      tablist: { background: 'transparent' },
      tab: { padding: '0.875rem 1.25rem', fontWeight: '600' },
      tabpanel: { background: 'transparent', padding: '1.5rem 0 0 0' },
    },
  },
});
