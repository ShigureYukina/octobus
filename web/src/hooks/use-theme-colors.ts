'use client';

import { useEffect } from 'react';
import { useTheme } from 'next-themes';
import { useSettingStore } from '@/stores/setting';
import type { ThemeColorConfig } from '@/types/theme';

export function useThemeColors() {
  const { resolvedTheme } = useTheme();
  const { lightThemeColors, darkThemeColors } = useSettingStore();

  useEffect(() => {
    const colors = resolvedTheme === 'dark' ? darkThemeColors : lightThemeColors;
    applyThemeColors(colors);
  }, [resolvedTheme, lightThemeColors, darkThemeColors]);
}

function applyThemeColors(colors: ThemeColorConfig) {
  const root = document.documentElement;
  
  root.style.setProperty('--background', colors.background);
  root.style.setProperty('--foreground', colors.foreground);
  root.style.setProperty('--card', colors.card);
  root.style.setProperty('--card-foreground', colors.cardForeground);
  root.style.setProperty('--popover', colors.popover);
  root.style.setProperty('--popover-foreground', colors.popoverForeground);
  root.style.setProperty('--primary', colors.primary);
  root.style.setProperty('--primary-foreground', colors.primaryForeground);
  root.style.setProperty('--secondary', colors.secondary);
  root.style.setProperty('--secondary-foreground', colors.secondaryForeground);
  root.style.setProperty('--muted', colors.muted);
  root.style.setProperty('--muted-foreground', colors.mutedForeground);
  root.style.setProperty('--accent', colors.accent);
  root.style.setProperty('--accent-foreground', colors.accentForeground);
  root.style.setProperty('--destructive', colors.destructive);
  root.style.setProperty('--destructive-foreground', colors.destructiveForeground);
  root.style.setProperty('--border', colors.border);
  root.style.setProperty('--input', colors.input);
  root.style.setProperty('--ring', colors.ring);
  
  colors.chartColors.forEach((color, index) => {
    root.style.setProperty(`--chart-${index + 1}`, color);
  });
  
  root.style.setProperty('--sidebar', colors.sidebar);
  root.style.setProperty('--sidebar-foreground', colors.sidebarForeground);
  root.style.setProperty('--sidebar-primary', colors.sidebarPrimary);
  root.style.setProperty('--sidebar-primary-foreground', colors.sidebarPrimaryForeground);
  root.style.setProperty('--sidebar-accent', colors.sidebarAccent);
  root.style.setProperty('--sidebar-accent-foreground', colors.sidebarAccentForeground);
  root.style.setProperty('--sidebar-border', colors.sidebarBorder);
  root.style.setProperty('--sidebar-ring', colors.sidebarRing);
}
