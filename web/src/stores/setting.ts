import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ThemeColorConfig } from '@/types/theme';
import { DEFAULT_THEME_COLORS, DEFAULT_DARK_THEME_COLORS } from '@/types/theme';

export type Locale = 'zh_hans' | 'zh_hant' | 'en';

function mergeThemeColors(stored: Partial<ThemeColorConfig>, defaults: ThemeColorConfig): ThemeColorConfig {
    return { ...defaults, ...stored };
}

interface SettingState {
    locale: Locale;
    setLocale: (locale: Locale) => void;
    lightThemeColors: ThemeColorConfig;
    darkThemeColors: ThemeColorConfig;
    setLightThemeColors: (colors: ThemeColorConfig) => void;
    setDarkThemeColors: (colors: ThemeColorConfig) => void;
    resetThemeColors: () => void;
}

export const useSettingStore = create<SettingState>()(
    persist(
        (set) => ({
            locale: 'zh_hans',
            setLocale: (locale) => set({ locale }),
            lightThemeColors: DEFAULT_THEME_COLORS,
            darkThemeColors: DEFAULT_DARK_THEME_COLORS,
            setLightThemeColors: (colors) => set({ lightThemeColors: colors }),
            setDarkThemeColors: (colors) => set({ darkThemeColors: colors }),
            resetThemeColors: () => set({
                lightThemeColors: DEFAULT_THEME_COLORS,
                darkThemeColors: DEFAULT_DARK_THEME_COLORS,
            }),
        }),
        {
            name: 'octopus-settings',
            merge: (persistedState, currentState) => {
                const persisted = persistedState as Partial<SettingState>;
                return {
                    ...currentState,
                    ...persisted,
                    lightThemeColors: mergeThemeColors(
                        persisted?.lightThemeColors ?? {},
                        DEFAULT_THEME_COLORS
                    ),
                    darkThemeColors: mergeThemeColors(
                        persisted?.darkThemeColors ?? {},
                        DEFAULT_DARK_THEME_COLORS
                    ),
                };
            },
        }
    )
);

