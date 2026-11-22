'use client';

import { useEffect } from 'react';
import { useTheme } from 'next-themes';
import { useSettingStore } from '@/stores/setting';

const LOGO_PATHS = [
    'M50 15 C70 15 85 30 85 50 C85 65 75 75 70 80 M50 15 C30 15 15 30 15 50 C15 65 25 75 30 80',
    'M30 80 Q30 90 20 90',
    'M43 77 Q43 90 38 90',
    'M57 77 Q57 90 62 90',
    'M70 80 Q70 90 80 90',
];

function oklchToHex(oklch: string): string | null {
    if (typeof document === 'undefined') return null;
    const canvas = document.createElement('canvas');
    canvas.width = 1;
    canvas.height = 1;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    ctx.fillStyle = oklch;
    ctx.fillRect(0, 0, 1, 1);
    const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data;
    return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
}

function generateFaviconSvg(hexColor: string): string {
    const paths = LOGO_PATHS.map(
        (d) => `<path d="${d}" fill="none" stroke="${hexColor}" stroke-width="6" stroke-linecap="round"/>`
    ).join('');
    return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">${paths}</svg>`;
}

function setFavicon(svgString: string) {
    const href = `data:image/svg+xml,${encodeURIComponent(svgString)}`;

    let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!link) {
        link = document.createElement('link');
        link.rel = 'icon';
        document.head.appendChild(link);
    }
    link.type = 'image/svg+xml';
    link.href = href;

    const appleLink = document.querySelector<HTMLLinkElement>('link[rel="apple-touch-icon"]');
    if (appleLink) {
        appleLink.href = href;
    }
}

export function useDynamicFavicon() {
    const { resolvedTheme } = useTheme();
    const { lightThemeColors, darkThemeColors } = useSettingStore();

    useEffect(() => {
        const colors = resolvedTheme === 'dark' ? darkThemeColors : lightThemeColors;
        const hex = oklchToHex(colors.primary);
        if (!hex) return;
        const svg = generateFaviconSvg(hex);
        setFavicon(svg);
    }, [resolvedTheme, lightThemeColors, darkThemeColors]);
}
