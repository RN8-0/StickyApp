/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                background: '#111113',
                card: '#1A1A1E',
                hover: '#252529',
                primary: '#7C6CF0',
                secondary: '#6558D3',
                accent: '#B09EFF',
                textMain: '#D4D4D8',
                textSec: '#85858F',
                danger: '#F06B7A',
                warning: '#FFCB57',
                success: '#4ADE80',
            },
        },
    },
    plugins: [],
}
