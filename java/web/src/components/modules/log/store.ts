'use client';

import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export type LogStatusFilter = '' | 'success' | 'error';
export type LogTimeRange = '15m' | '1h' | '24h' | '7d' | 'all';
export type LogViewMode = 'pagination' | 'waterfall';

interface LogFilterState {
    modelName: string;
    channelName: string;
    status: LogStatusFilter;
    timeRange: LogTimeRange;
    mode: LogViewMode;
    filtersExpanded: boolean;
    setModelName: (value: string) => void;
    setChannelName: (value: string) => void;
    setStatus: (value: LogStatusFilter) => void;
    setTimeRange: (value: LogTimeRange) => void;
    setMode: (value: LogViewMode) => void;
    toggleFiltersExpanded: () => void;
    reset: () => void;
}

const initialState = {
    modelName: '',
    channelName: '',
    status: '' as LogStatusFilter,
    timeRange: '24h' as LogTimeRange,
    mode: 'pagination' as LogViewMode,
    filtersExpanded: false,
};

export const useLogFilterStore = create<LogFilterState>()(
    persist(
        (set) => ({
            ...initialState,
            setModelName: (value) => set({ modelName: value }),
            setChannelName: (value) => set({ channelName: value }),
            setStatus: (value) => set({ status: value }),
            setTimeRange: (value) => set({ timeRange: value }),
            setMode: (value) => set({ mode: value }),
            toggleFiltersExpanded: () => set((state) => ({ filtersExpanded: !state.filtersExpanded })),
            reset: () => set((state) => ({ ...initialState, filtersExpanded: state.filtersExpanded })),
        }),
        {
            name: 'log-filter-storage',
            storage: createJSONStorage(() => localStorage),
            partialize: (state) => ({
                modelName: state.modelName,
                channelName: state.channelName,
                status: state.status,
                timeRange: state.timeRange,
                mode: state.mode,
            }),
        }
    )
);
