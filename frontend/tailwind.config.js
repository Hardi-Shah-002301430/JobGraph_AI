/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"DM Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      colors: {
        bg:      '#0d0f14',
        surface: '#141720',
        border:  '#1e2330',
        muted:   '#3a4055',
        dim:     '#6b7280',
        text:    '#e2e8f0',
        accent:  '#6366f1',
        teal:    '#14b8a6',
        amber:   '#f59e0b',
        rose:    '#f43f5e',
        green:   '#22c55e',
      }
    }
  },
  plugins: []
}
