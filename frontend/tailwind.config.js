/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: '#1F5C3A', dark: '#164429', soft: '#E7F0E9' },
        ink: { DEFAULT: '#1C2620', 2: '#3B4A41', 3: '#56655B' },
        ground: '#F4F3EE',
        line: { DEFAULT: '#C9D0C6', soft: '#E1E4DC', faint: '#EEF0EA' },
        track: '#E4E6DF',
        danger: { DEFAULT: '#A3271B', text: '#8E2216', soft: '#FBE4E1' },
        warn: { text: '#6B4210', soft: '#FBF1E2', ink: '#7A4B0E' },
        info: { text: '#1F4E87', soft: '#E6EEF8' },
      },
      fontFamily: { sans: ['Figtree', 'system-ui', 'sans-serif'] },
    },
  },
  plugins: [],
}
