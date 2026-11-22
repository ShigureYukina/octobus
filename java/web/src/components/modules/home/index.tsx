'use client';

import dynamic from 'next/dynamic';
import { Total } from './total';
import { PageWrapper } from '@/components/common/PageWrapper';

const Activity = dynamic(() => import('./activity').then(mod => ({ default: mod.Activity })), {
    ssr: false,
    loading: () => <div className="rounded-3xl bg-card border-card-border border h-[120px] animate-pulse" />,
});

const StatsChart = dynamic(() => import('./chart').then(mod => ({ default: mod.StatsChart })), {
    ssr: false,
    loading: () => <div className="rounded-3xl bg-card border-card-border border h-[300px] animate-pulse" />,
});

const Rank = dynamic(() => import('./rank').then(mod => ({ default: mod.Rank })), {
    ssr: false,
    loading: () => <div className="rounded-3xl bg-card border-card-border border h-[400px] animate-pulse" />,
});

export function Home() {
    return (
        <PageWrapper className="h-full min-h-0 overflow-y-auto overscroll-contain space-y-6 pb-24 md:pb-4 rounded-t-3xl">
            <Total />
            <Activity />
            <StatsChart />
            <Rank />
        </PageWrapper>
    );
}
