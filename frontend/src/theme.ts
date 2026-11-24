import '@fontsource/atkinson-hyperlegible/400.css'
import '@fontsource/atkinson-hyperlegible/700.css'
import { createTheme } from '@mui/material/styles'

/**
 * Design tokens. One typeface, Atkinson Hyperlegible (made by the Braille Institute for
 * legibility): this app exists so parents read allergen labels correctly. Surfaces are
 * flat with thin borders; the only heavy border in the app is the packaging-style label
 * panel, so the eye goes there first.
 */
export const tokens = {
  ink: '#1E2A2E',
  sage: '#2F5D50',
  sageDark: '#234740',
  sageTint: '#E3EEE8',
  canvas: '#F2F6F3',
  surface: '#FFFFFF',
  line: '#CBD8D1',
  contains: '#B42318',
  containsTint: '#FDECEA',
  caution: '#9A5B00',
  cautionTint: '#FFF4E0',
  muted: '#52615F',
}

const fontFamily = '"Atkinson Hyperlegible", system-ui, -apple-system, "Segoe UI", sans-serif'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: tokens.sage, dark: tokens.sageDark, contrastText: '#FFFFFF' },
    error: { main: tokens.contains },
    warning: { main: tokens.caution },
    success: { main: tokens.sage },
    text: { primary: tokens.ink, secondary: tokens.muted },
    background: { default: tokens.canvas, paper: tokens.surface },
    divider: tokens.line,
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily,
    fontSize: 15,
    h1: { fontSize: '1.375rem', fontWeight: 700, letterSpacing: 0 },
    h2: { fontSize: '1.75rem', fontWeight: 700, letterSpacing: '-0.01em', lineHeight: 1.2 },
    h3: { fontSize: '1.25rem', fontWeight: 700, lineHeight: 1.3 },
    h4: { fontSize: '1.0625rem', fontWeight: 700 },
    body1: { fontSize: '1rem', lineHeight: 1.55 },
    body2: { fontSize: '0.9375rem', lineHeight: 1.5 },
    button: { textTransform: 'none', fontWeight: 700, fontSize: '0.9375rem' },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundColor: tokens.canvas },
        ':focus-visible': { outline: `3px solid ${tokens.sage}`, outlineOffset: 2 },
        '@media (prefers-reduced-motion: reduce)': {
          '*': { transition: 'none !important', animation: 'none !important' },
        },
      },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { border: `1px solid ${tokens.line}` } },
    },
    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'inherit' },
      styleOverrides: {
        root: { backgroundColor: tokens.surface, borderWidth: '0 0 1px 0', borderStyle: 'solid', borderColor: tokens.line },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { borderRadius: 8, paddingInline: 16 } },
    },
    MuiAlert: {
      styleOverrides: { root: { borderRadius: 10, alignItems: 'flex-start' } },
    },
    MuiTableCell: {
      styleOverrides: { head: { fontWeight: 700, color: tokens.muted } },
    },
  },
})
