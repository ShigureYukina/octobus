"use client"

import * as React from "react"
import { ThemeProvider as NextThemesProvider, useTheme } from "next-themes"
import { useThemeColors } from "@/hooks/use-theme-colors"

function ThemeColorUpdater() {
    const { resolvedTheme } = useTheme()

    React.useEffect(() => {
        const metaThemeColor = document.querySelector('meta[name="theme-color"]')
        if (metaThemeColor) {
            metaThemeColor.setAttribute(
                'content',
                resolvedTheme === 'dark' ? '#413a2c' : '#eae9e3'
            )
        }
    }, [resolvedTheme])

    return null
}

function ThemeColorsApplier() {
    useThemeColors()
    return null
}

export function ThemeProvider({ children, ...props }: React.ComponentProps<typeof NextThemesProvider>) {
    return (
        <NextThemesProvider {...props}>
            <ThemeColorUpdater />
            <ThemeColorsApplier />
            {children}
        </NextThemesProvider>
    )
}