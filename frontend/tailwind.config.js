/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        // ── Brand palette (primary blue) ─────────────────────────────────────
        brand: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
          950: '#172554',
        },

        // ── Semantic surface tokens ──────────────────────────────────────────
        // Instead of reaching for raw palette in templates, use these names.
        surface: {
          DEFAULT: '#ffffff',      // primary background
          muted: '#f8fafc',        // slate-50 — subtle secondary background
          hover: 'rgba(248,250,252,0.8)', // for table row hovers
          nav: '#0f172a',          // slate-900 — sidebar background
          overlay: 'rgba(255,255,255,0.9)', // header glass effect
        },

        // ── Semantic divider / border color tokens ──────────────────────────
        // Named "divider-*" so the Tailwind utility is "border-divider-*"
        // (no double-border prefix conflict).
        divider: {
          DEFAULT: '#e2e8f0', // slate-200 — default border / stroke
          muted:   '#f1f5f9', // slate-100 — subtle dividers
          nav:     '#1e293b', // slate-800 — sidebar inner borders
        },

        // ── Semantic text tokens ─────────────────────────────────────────────
        'content-primary': '#0f172a',   // slate-900 — headings, key values
        'content-secondary': '#1e293b', // slate-800 — sub-headings, row text
        'content-body': '#475569',      // slate-600 — body copy
        'content-muted': '#64748b',     // slate-500 — labels, captions
        'content-subtle': '#94a3b8',    // slate-400 — placeholder, inactive
        'content-nav': '#cbd5e1',       // slate-300 — nav link text

        // ── Status: error ────────────────────────────────────────────────────
        'status-error': {
          surface: '#fff1f2',    // rose-50
          border: '#fecdd3',     // rose-200
          text: '#9f1239',       // rose-800
          action: '#e11d48',     // rose-600 — destructive actions
          'action-hover': '#be123c', // rose-700
        },

        // ── Status: warning ──────────────────────────────────────────────────
        'status-warning': {
          surface: '#fffbeb',    // amber-50
          border: '#fde68a',     // amber-200
          text: '#b45309',       // amber-700
        },
      },

      fontFamily: {
        sans: ['"DM Sans"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },

      boxShadow: {
        // Named shadow used on cards and the shell header
        shell: '0 1px 3px 0 rgb(15 23 42 / 0.06), 0 1px 2px -1px rgb(15 23 42 / 0.06)',
      },
    },
  },
  plugins: [],
};
