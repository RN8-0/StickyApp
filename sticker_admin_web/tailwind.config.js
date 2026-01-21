/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                background: '#0B141A',
                card: '#1F2C34',
                hover: '#2A3942',
                primary: '#00A884',
                secondary: '#075E54',
                accent: '#34B7F1',
                textMain: '#E9EDEF',
                textSec: '#8696A0',
                danger: '#F15C6D',
                warning: '#FFD279',
                success: '#00A884',
            },
        },
    },
    plugins: [],
}
