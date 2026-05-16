import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: { base: '#0f1117', panel: '#1a1d27', border: '#2a2d3a' },
        node: {
          start: '#3b82f6', send: '#22c55e', expect: '#eab308',
          validate: '#a855f7', decision: '#f97316', retry: '#06b6d4',
          wait: '#6b7280', endPass: '#22c55e', endFail: '#ef4444',
        },
        accent: { blue: '#3b82f6', green: '#22c55e', red: '#ef4444', amber: '#f59e0b' },
      },
    },
  },
  plugins: [],
};
export default config;
