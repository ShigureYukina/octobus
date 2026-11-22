'use client';

import { useModelList } from '@/api/endpoints/model';
import { useChannelList } from '@/api/endpoints/channel';
import { RotateCcw } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useLogFilterStore, type LogTimeRange } from './store';
import { useMemo } from 'react';
import { motion } from 'motion/react';
import { EASING } from '@/lib/animations/fluid-transitions';

export function LogFilterBar() {
    const filterT = useTranslations('log.filter');

    const { data: models } = useModelList();
    const { data: channels } = useChannelList();
    const timeRange = useLogFilterStore((state) => state.timeRange);
    const status = useLogFilterStore((state) => state.status);
    const modelName = useLogFilterStore((state) => state.modelName);
    const channelName = useLogFilterStore((state) => state.channelName);
    const setTimeRange = useLogFilterStore((state) => state.setTimeRange);
    const setStatus = useLogFilterStore((state) => state.setStatus);
    const setModelName = useLogFilterStore((state) => state.setModelName);
    const setChannelName = useLogFilterStore((state) => state.setChannelName);
    const resetFilters = useLogFilterStore((state) => state.reset);

    const modelOptions = useMemo(() => Array.from(new Set((models ?? []).map((model) => model.name))).sort((a, b) => a.localeCompare(b)), [models]);
    const channelOptions = useMemo(() => Array.from(new Set((channels ?? []).map((channel) => channel.raw.name))).sort((a, b) => a.localeCompare(b)), [channels]);
    const hasActiveFilters = modelName !== '' || channelName !== '' || status !== '' || timeRange !== '24h';

    return (
        <motion.div
            className="flex flex-wrap items-center gap-2"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, ease: EASING.easeOutExpo, delay: 0.08 }}
        >
            <Select value={timeRange} onValueChange={(value) => setTimeRange(value as LogTimeRange)}>
                <SelectTrigger className="w-auto min-w-[100px] rounded-xl h-8 text-xs">
                    <SelectValue placeholder={filterT('timeRange')} />
                </SelectTrigger>
                <SelectContent className="rounded-xl">
                    <SelectItem value="15m" className="rounded-lg">{filterT('timeRanges.15m')}</SelectItem>
                    <SelectItem value="1h" className="rounded-lg">{filterT('timeRanges.1h')}</SelectItem>
                    <SelectItem value="24h" className="rounded-lg">{filterT('timeRanges.24h')}</SelectItem>
                    <SelectItem value="7d" className="rounded-lg">{filterT('timeRanges.7d')}</SelectItem>
                    <SelectItem value="all" className="rounded-lg">{filterT('timeRanges.all')}</SelectItem>
                </SelectContent>
            </Select>
            <Select value={status || 'all'} onValueChange={(value) => setStatus(value === 'all' ? '' : (value as 'success' | 'error'))}>
                <SelectTrigger className="w-auto min-w-[80px] rounded-xl h-8 text-xs">
                    <SelectValue placeholder={filterT('status')} />
                </SelectTrigger>
                <SelectContent className="rounded-xl">
                    <SelectItem value="all" className="rounded-lg">{filterT('all')}</SelectItem>
                    <SelectItem value="success" className="rounded-lg">{filterT('success')}</SelectItem>
                    <SelectItem value="error" className="rounded-lg">{filterT('error')}</SelectItem>
                </SelectContent>
            </Select>
            <Select value={modelName || 'all'} onValueChange={(value) => setModelName(value === 'all' ? '' : value)}>
                <SelectTrigger className="w-auto min-w-[100px] rounded-xl h-8 text-xs">
                    <SelectValue placeholder={filterT('modelName')} />
                </SelectTrigger>
                <SelectContent className="rounded-xl">
                    <SelectItem value="all" className="rounded-lg">{filterT('all')}</SelectItem>
                    {modelOptions.map((model) => (
                        <SelectItem key={model} value={model} className="rounded-lg">{model}</SelectItem>
                    ))}
                </SelectContent>
            </Select>
            <Select value={channelName || 'all'} onValueChange={(value) => setChannelName(value === 'all' ? '' : value)}>
                <SelectTrigger className="w-auto min-w-[100px] rounded-xl h-8 text-xs">
                    <SelectValue placeholder={filterT('channelName')} />
                </SelectTrigger>
                <SelectContent className="rounded-xl">
                    <SelectItem value="all" className="rounded-lg">{filterT('all')}</SelectItem>
                    {channelOptions.map((channel) => (
                        <SelectItem key={channel} value={channel} className="rounded-lg">{channel}</SelectItem>
                    ))}
                </SelectContent>
            </Select>
            <button
                type="button"
                onClick={resetFilters}
                disabled={!hasActiveFilters}
                className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-muted-foreground transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
            >
                <RotateCcw className="size-3" />
                {filterT('clear')}
            </button>
        </motion.div>
    );
}
