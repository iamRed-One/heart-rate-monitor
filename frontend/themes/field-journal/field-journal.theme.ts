// The field-journal theme. `astryx experimental theme editor` wrote this file once and never changes it again.
// The checked settings live in field-journal.settings.json and the generated field-journal.settings.ts;
// change them with `astryx experimental theme editor set field-journal ...`. Add your own changes below: they win over the settings.
import {defineTheme} from '@astryxdesign/core/theme';
import {fieldJournalSettings} from './field-journal.settings';

export const fieldJournalTheme = defineTheme({
  extends: fieldJournalSettings,
  name: 'field-journal',
  tokens: {
    '--font-family-body': '"Manrope Variable", sans-serif',
    '--font-family-heading': '"Manrope Variable", sans-serif',
    '--color-background-body': '#ffffff',
    '--color-border-default': '#d9e1da',
    '--radius-control': '.625rem',
    '--size-element-md': '2.75rem',
    '--size-element-lg': '3rem',
    '--color-brand-deep': '#173e32',
    '--color-brand-on-deep': '#f6f5ec',
    '--color-brand-muted': '#c8d9cd',
    '--color-brand-nav-active': '#305547',
    '--color-brand-tint': '#edf2ed',
    '--color-text-on-accent': '#ffffff',
    '--color-status-error': '#9b2525',
    '--color-status-error-surface': '#fff0ee',
  },
});
