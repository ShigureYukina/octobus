'use client';

import * as React from 'react';
import { useTranslations } from 'next-intl';
import { cn } from '@/lib/utils';
import { useSettingStore } from '@/stores/setting';
import { useTheme } from 'next-themes';
import type { ThemeColorConfig } from '@/types/theme';
import { PRESET_THEMES } from '@/types/theme';
import { ColorPicker } from './color-picker';
import { Button } from '@/components/ui/button';
import { RotateCcw, ChevronDown } from 'lucide-react';

interface ThemeColorPanelProps {
  className?: string;
}

export function ThemeColorPanel({ className }: ThemeColorPanelProps) {
  const t = useTranslations('setting.themeColor');
  const { resolvedTheme } = useTheme();
  const {
    lightThemeColors,
    darkThemeColors,
    setLightThemeColors,
    setDarkThemeColors,
    resetThemeColors,
  } = useSettingStore();

  const [showCustom, setShowCustom] = React.useState(false);

  const isDark = resolvedTheme === 'dark';
  const currentColors = isDark ? darkThemeColors : lightThemeColors;
  const setCurrentColors = isDark ? setDarkThemeColors : setLightThemeColors;

  const isPresetActive = (preset: typeof PRESET_THEMES[0]) => {
    const presetColors = isDark ? preset.darkColors : preset.colors;
    return presetColors.primary === currentColors.primary &&
           presetColors.accent === currentColors.accent;
  };

  const activePreset = PRESET_THEMES.find(p => isPresetActive(p));

  const handlePresetClick = (preset: typeof PRESET_THEMES[0]) => {
    if (isDark) {
      setDarkThemeColors(preset.darkColors);
    } else {
      setLightThemeColors(preset.colors);
    }
  };

  const updateColor = (key: keyof ThemeColorConfig, value: string) => {
    setCurrentColors({ ...currentColors, [key]: value });
  };

  return (
    <div className={cn('space-y-4', className)}>
      <div className="flex justify-end">
        <Button variant="ghost" size="sm" onClick={resetThemeColors} className="h-7 px-2 text-xs">
          <RotateCcw className="h-3 w-3 mr-1" />
          {t('reset')}
        </Button>
      </div>

      <div className="grid grid-cols-5 gap-2">
        {PRESET_THEMES.map((preset) => (
          <button
            key={preset.name}
            onClick={() => handlePresetClick(preset)}
            className={cn(
              'flex flex-col items-center gap-1.5 p-2 rounded-lg border transition-all',
              activePreset?.name === preset.name
                ? 'border-primary bg-primary/5'
                : 'border-border hover:border-primary/50'
            )}
          >
            <div
              className="w-8 h-8 rounded-full border border-border/50"
              style={{ backgroundColor: isDark ? preset.darkColors.primary : preset.colors.primary }}
            />
            <span className="text-xs text-muted-foreground">{preset.label}</span>
          </button>
        ))}
      </div>

      <button
        onClick={() => setShowCustom(!showCustom)}
        className="flex items-center justify-between w-full px-3 py-2 text-sm text-muted-foreground hover:text-foreground border border-dashed border-border rounded-lg hover:border-primary/50 transition-colors"
      >
        <span>{t('custom')}</span>
        <ChevronDown className={cn('h-4 w-4 transition-transform', showCustom && 'rotate-180')} />
      </button>

      {showCustom && (
        <div className="space-y-3 p-3 border border-border rounded-lg bg-muted/30">
          <div className="space-y-2">
            <span className="text-xs text-muted-foreground">{t('background')}</span>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('pageBackground')}</span>
              <ColorPicker
                value={currentColors.background}
                onChange={(v) => updateColor('background', v)}
                className="w-48"
              />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('cardBackground')}</span>
              <ColorPicker
                value={currentColors.card}
                onChange={(v) => updateColor('card', v)}
                className="w-48"
              />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('sidebarBackground')}</span>
              <ColorPicker
                value={currentColors.sidebar}
                onChange={(v) => updateColor('sidebar', v)}
                className="w-48"
              />
            </div>
          </div>

          <div className="space-y-2">
            <span className="text-xs text-muted-foreground">{t('accentColors')}</span>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('primary')}</span>
              <ColorPicker
                value={currentColors.primary}
                onChange={(v) => updateColor('primary', v)}
                className="w-48"
              />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('accent')}</span>
              <ColorPicker
                value={currentColors.accent}
                onChange={(v) => updateColor('accent', v)}
                className="w-48"
              />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('ring')}</span>
              <ColorPicker
                value={currentColors.ring}
                onChange={(v) => updateColor('ring', v)}
                className="w-48"
              />
            </div>
          </div>

          <div className="space-y-2">
            <span className="text-xs text-muted-foreground">{t('sidebarColors')}</span>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('sidebarPrimary')}</span>
              <ColorPicker
                value={currentColors.sidebarPrimary}
                onChange={(v) => updateColor('sidebarPrimary', v)}
                className="w-48"
              />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{t('sidebarAccent')}</span>
              <ColorPicker
                value={currentColors.sidebarAccent}
                onChange={(v) => updateColor('sidebarAccent', v)}
                className="w-48"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
