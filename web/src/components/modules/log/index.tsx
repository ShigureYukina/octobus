'use client';

import { useCallback, useMemo } from 'react';
import { useLogs, type RelayLog } from '@/api/endpoints/log';
import { useModelList } from '@/api/endpoints/model';
import { LogCard } from './Item';
import { Loader2, Zap, Activity, Database } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { VirtualizedGrid } from '@/components/common/VirtualizedGrid';
import { getDisplayUsage } from './usage';

/**
 * 计算近 N 条日志的统计指标
 */
function computeStats(logs: RelayLog[], modelPriceMap: Map<string, import('@/api/endpoints/model').LLMInfo>, count: number = 100) {
    const recentLogs = logs.slice(0, count);
    if (recentLogs.length === 0) {
        return { avgFtut: 0, tps: 0, cacheHitRate: 0, count: 0 };
    }

    let totalFtut = 0;
    let totalOutputTokens = 0;
    let totalUseTime = 0;
    let totalCacheHitTokens = 0;
    let totalInputTokens = 0;
    let validFtutCount = 0;

    for (const log of recentLogs) {
        const usage = getDisplayUsage(log, modelPriceMap.get(log.actual_model_name.toLowerCase()));
        if (log.ftut > 0) {
            totalFtut += log.ftut;
            validFtutCount++;
        }
        totalOutputTokens += usage.outputTokens;
        totalUseTime += log.use_time;
        totalCacheHitTokens += usage.cacheHitTokens ?? 0;
        totalInputTokens += usage.inputTokens;
    }

    const avgFtut = validFtutCount > 0 ? totalFtut / validFtutCount : 0;
    const tps = totalUseTime > 0 ? (totalOutputTokens / (totalUseTime / 1000)) : 0;
    const cacheHitRate = totalInputTokens > 0 ? (totalCacheHitTokens / totalInputTokens) : 0;

    return { avgFtut, tps, cacheHitRate, count: recentLogs.length };
}

/**
 * 统计卡片组件
 */
function LogStats({ logs, modelPriceMap }: { logs: RelayLog[]; modelPriceMap: Map<string, import('@/api/endpoints/model').LLMInfo> }) {
    const t = useTranslations('log.stats');
    const stats = useMemo(() => computeStats(logs, modelPriceMap), [logs, modelPriceMap]);

    const formatDuration = (ms: number) => {
        if (ms < 1000) return `${Math.round(ms)}ms`;
        return `${(ms / 1000).toFixed(2)}s`;
    };

    if (stats.count === 0) return null;

    return (
        <div className="grid grid-cols-3 gap-3 mb-4">
            <div className="flex items-center gap-3 rounded-2xl border border-border bg-card p-4">
                <div className="flex items-center justify-center size-10 rounded-xl bg-amber-500/10">
                    <Zap className="size-5 text-amber-500" />
                </div>
                <div className="flex flex-col">
                    <span className="text-xs text-muted-foreground">{t('avgFtut')}</span>
                    <span className="text-lg font-semibold tabular-nums">{formatDuration(stats.avgFtut)}</span>
                </div>
            </div>
            <div className="flex items-center gap-3 rounded-2xl border border-border bg-card p-4">
                <div className="flex items-center justify-center size-10 rounded-xl bg-blue-500/10">
                    <Activity className="size-5 text-blue-500" />
                </div>
                <div className="flex flex-col">
                    <span className="text-xs text-muted-foreground">{t('tps')}</span>
                    <span className="text-lg font-semibold tabular-nums">{stats.tps.toFixed(1)} <span className="text-xs font-normal text-muted-foreground">token/s</span></span>
                </div>
            </div>
            <div className="flex items-center gap-3 rounded-2xl border border-border bg-card p-4">
                <div className="flex items-center justify-center size-10 rounded-xl bg-cyan-500/10">
                    <Database className="size-5 text-cyan-500" />
                </div>
                <div className="flex flex-col">
                    <span className="text-xs text-muted-foreground">{t('cacheHitRate')}</span>
                    <span className="text-lg font-semibold tabular-nums">{(stats.cacheHitRate * 100).toFixed(1)}%</span>
                </div>
            </div>
        </div>
    );
}

/**
 * 日志页面组件
 * - 初始加载 pageSize 条历史日志
 * - SSE 实时推送新日志
 * - 滚动自动加载更多
 */
export function Log() {
    const t = useTranslations('log');
    const { logs, hasMore, isLoading, isLoadingMore, loadMore } = useLogs({ pageSize: 10 });
    const { data: models } = useModelList();

    const modelPriceMap = useMemo(() => {
        const map = new Map<string, import('@/api/endpoints/model').LLMInfo>();
        for (const model of models ?? []) {
            map.set(model.name.toLowerCase(), model);
        }
        return map;
    }, [models]);

    const canLoadMore = hasMore && !isLoading && !isLoadingMore && logs.length > 0;
    const handleReachEnd = useCallback(() => {
        if (!canLoadMore) return;
        void loadMore();
    }, [canLoadMore, loadMore]);

    const footer = useMemo(() => {
        if (hasMore && (isLoading || isLoadingMore)) {
            return (
                <div className="flex justify-center py-4">
                    <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
            );
        }
        if (!hasMore && logs.length > 0) {
            return (
                <div className="flex justify-center py-4">
                    <span className="text-sm text-muted-foreground">{t('list.noMore')}</span>
                </div>
            );
        }
        return null;
    }, [hasMore, isLoading, isLoadingMore, logs.length, t]);

    return (
        <div className="flex flex-col h-full min-h-0">
            <LogStats logs={logs} modelPriceMap={modelPriceMap} />
            <VirtualizedGrid
                items={logs}
                layout="list"
                columns={{ default: 1 }}
                estimateItemHeight={80}
                overscan={8}
                getItemKey={(log) => `log-${log.id}`}
                renderItem={(log) => <LogCard log={log} modelPrice={modelPriceMap.get(log.actual_model_name.toLowerCase())} />}
                footer={footer}
                onReachEnd={handleReachEnd}
                reachEndEnabled={canLoadMore}
                reachEndOffset={2}
            />
        </div>
    );
}
