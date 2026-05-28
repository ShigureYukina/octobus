'use client';

import * as React from 'react';
import { cn } from '@/lib/utils';
import type { ThemeColorConfig } from '@/types/theme';
import { PRESET_THEMES } from '@/types/theme';

interface PresetSelectorProps {
  value: ThemeColorConfig;
  onChange: (colors: ThemeColorConfig) => void;
  className?: string;
}

export function PresetSelector({ value, onChange, className }: PresetSelectorProps) {
  const selectedPreset = PRESET_THEMES.find(
    (preset) => preset.colors.primary === value.primary
  );

  return (
    <div className={cn('grid grid-cols-2 gap-2', className)}>
      {PRESET_THEMES.map((preset) => (
        <button
          key={preset.name}
          onClick={() => onChange(preset.colors)}
          className={cn(
            'flex items-center gap-3 p-3 rounded-lg border transition-all',
            'hover:border-primary/50 hover:shadow-sm',
            selectedPreset?.name === preset.name
              ? 'border-primary bg-primary/5'
              : 'border-border'
          )}
        >
          <div className="flex gap-1">
            <div
              className="w-4 h-4 rounded-full"
              style={{ backgroundColor: preset.colors.primary }}
            />
            <div
              className="w-4 h-4 rounded-full"
              style={{ backgroundColor: preset.colors.accent }}
            />
          </div>
          <span className="text-sm font-medium">{preset.label}</span>
        </button>
      ))}
    </div>
  );
}