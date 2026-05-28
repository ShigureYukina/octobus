'use client';

import * as React from 'react';
import { cn } from '@/lib/utils';

interface ColorPickerProps {
  value: string;
  onChange: (value: string) => void;
  className?: string;
}

export function ColorPicker({ value, onChange, className }: ColorPickerProps) {
  const [inputValue, setInputValue] = React.useState(value);
  const [isValid, setIsValid] = React.useState(true);

  React.useEffect(() => {
    setInputValue(value);
  }, [value]);

  const validateOklch = (color: string): boolean => {
    const oklchRegex = /^oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)$/;
    const match = color.match(oklchRegex);
    if (!match) return false;
    
    const l = parseFloat(match[1]);
    const c = parseFloat(match[2]);
    const h = parseFloat(match[3]);
    
    return l >= 0 && l <= 1 && c >= 0 && c <= 0.4 && h >= 0 && h <= 360;
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setInputValue(newValue);
    
    if (validateOklch(newValue)) {
      setIsValid(true);
      onChange(newValue);
    } else {
      setIsValid(false);
    }
  };

  const handleBlur = () => {
    if (!isValid) {
      setInputValue(value);
      setIsValid(true);
    }
  };

  return (
    <div className={cn('flex items-center gap-2', className)}>
      <div
        className="w-8 h-8 rounded-md border border-border"
        style={{ backgroundColor: value }}
      />
      <input
        type="text"
        value={inputValue}
        onChange={handleInputChange}
        onBlur={handleBlur}
        className={cn(
          'flex-1 px-3 py-2 text-sm border rounded-md bg-background text-foreground',
          'focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
          !isValid && 'border-destructive'
        )}
        placeholder="oklch(0.6205 0.1199 144.8607)"
      />
    </div>
  );
}