'use client';

import type { RelayLog } from '@/api/endpoints/log';
import type { LLMInfo } from '@/api/endpoints/model';

export type UsageSource = 'stored' | 'response' | 'estimated' | 'missing';

export type DisplayUsage = {
    inputTokens: number;
    outputTokens: number;
    cacheHitTokens?: number;
    usageRecorded: boolean;
    costRecorded: boolean;
    inputSource: UsageSource;
    outputSource: UsageSource;
    cacheSource: UsageSource;
    costSource: UsageSource;
    estimatedCost: number;
};

function estimateTextTokens(text: string): number {
    const normalized = text.replace(/\s+/g, ' ').trim();
    if (!normalized) return 0;

    const chars = Array.from(normalized);
    let cjkCount = 0;
    for (const ch of chars) {
        if (/\p{Script=Han}|\p{Script=Hiragana}|\p{Script=Katakana}|\p{Script=Hangul}/u.test(ch)) {
            cjkCount++;
        }
    }
    const otherCount = chars.length - cjkCount;
    return Math.max(1, Math.round(cjkCount + otherCount / 4));
}

function sumTokensFromTexts(texts: string[]): number {
    return texts.reduce((sum, text) => sum + estimateTextTokens(text), 0);
}

function collectMessageTexts(message: unknown): string[] {
    if (!message || typeof message !== 'object') return [];
    const msg = message as Record<string, unknown>;
    const texts: string[] = [];

    if (typeof msg.content === 'string') {
        texts.push(msg.content);
    } else if (Array.isArray(msg.content)) {
        for (const part of msg.content) {
            if (!part || typeof part !== 'object') continue;
            const p = part as Record<string, unknown>;
            if (p.type === 'text' && typeof p.text === 'string') {
                texts.push(p.text);
            }
        }
    } else if (msg.content && typeof msg.content === 'object') {
        const content = msg.content as Record<string, unknown>;
        if (typeof content.content === 'string') texts.push(content.content);
        if (Array.isArray(content.multiple_content)) {
            for (const part of content.multiple_content) {
                if (!part || typeof part !== 'object') continue;
                const p = part as Record<string, unknown>;
                if (p.type === 'text' && typeof p.text === 'string') {
                    texts.push(p.text);
                }
            }
        }
    }

    if (typeof msg.reasoning_content === 'string') {
        texts.push(msg.reasoning_content);
    }
    if (typeof msg.reasoning === 'string') {
        texts.push(msg.reasoning);
    }

    if (Array.isArray(msg.tool_calls)) {
        for (const toolCall of msg.tool_calls) {
            if (!toolCall || typeof toolCall !== 'object') continue;
            const tc = toolCall as Record<string, unknown>;
            const fn = tc.function;
            if (!fn || typeof fn !== 'object') continue;
            const fnObj = fn as Record<string, unknown>;
            if (typeof fnObj.name === 'string') texts.push(fnObj.name);
            if (typeof fnObj.arguments === 'string') texts.push(fnObj.arguments);
        }
    }

    return texts;
}

function collectRequestTexts(parsed: unknown): string[] {
    if (!parsed || typeof parsed !== 'object') return [];
    const data = parsed as Record<string, unknown>;
    const texts: string[] = [];

    if (Array.isArray(data.messages)) {
        for (const message of data.messages) {
            texts.push(...collectMessageTexts(message));
        }
    }

    if (typeof data.input === 'string') {
        texts.push(data.input);
    } else if (Array.isArray(data.input)) {
        for (const item of data.input) {
            texts.push(...collectMessageTexts(item));
        }
    }

    return texts;
}

function collectResponseTexts(parsed: unknown): string[] {
    if (!parsed || typeof parsed !== 'object') return [];
    const data = parsed as Record<string, unknown>;
    const texts: string[] = [];

    if (Array.isArray(data.choices)) {
        for (const choice of data.choices) {
            if (!choice || typeof choice !== 'object') continue;
            const c = choice as Record<string, unknown>;
            texts.push(...collectMessageTexts(c.message));
            texts.push(...collectMessageTexts(c.delta));
        }
    }

    if (Array.isArray(data.output)) {
        for (const item of data.output) {
            texts.push(...collectMessageTexts(item));
        }
    }

    return texts;
}

function parseUsageFromResponseContent(content: string | undefined): Partial<DisplayUsage> | null {
    if (!content) return null;
    try {
        const parsed = JSON.parse(content) as { usage?: Record<string, unknown> };
        const usage = parsed.usage;
        if (!usage) return null;

        const inputTokens = Number(usage.prompt_tokens ?? usage.input_tokens ?? 0);
        const outputTokens = Number(usage.completion_tokens ?? usage.output_tokens ?? 0);
        const promptDetails = usage.prompt_tokens_details as Record<string, unknown> | undefined;
        const cacheHitTokens = Number(promptDetails?.cached_tokens ?? 0);

        if (inputTokens <= 0 && outputTokens <= 0 && cacheHitTokens <= 0) return null;
        return { inputTokens, outputTokens, cacheHitTokens, usageRecorded: true };
    } catch {
        return null;
    }
}

function estimateUsageFromLogContent(log: RelayLog): Partial<DisplayUsage> | null {
    try {
        const requestParsed = log.request_content ? JSON.parse(log.request_content) : null;
        const responseParsed = log.response_content ? JSON.parse(log.response_content) : null;
        const inputTokens = sumTokensFromTexts(collectRequestTexts(requestParsed));
        const outputTokens = sumTokensFromTexts(collectResponseTexts(responseParsed));
        if (inputTokens <= 0 && outputTokens <= 0) return null;
        return {
            inputTokens,
            outputTokens,
            cacheHitTokens: log.cache_hit_tokens,
            usageRecorded: true,
        };
    } catch {
        return null;
    }
}

function hasRecordedUsage(log: RelayLog): boolean {
    return log.usage_recorded || log.input_tokens > 0 || log.output_tokens > 0 || log.cost > 0 || (log.cache_hit_tokens ?? 0) > 0;
}

function estimateCacheHitTokens(log: RelayLog, inputTokens: number): number | undefined {
    if (typeof log.cache_hit_tokens === 'number' && log.cache_hit_tokens > 0) {
        return log.cache_hit_tokens;
    }
    if (!log.request_content || inputTokens <= 0) return undefined;
    try {
        const parsed = JSON.parse(log.request_content) as { cache_control?: unknown; messages?: unknown[]; input?: unknown[] };
        const hasCacheHint = !!parsed.cache_control ||
            parsed.messages?.some((message) => JSON.stringify(message).includes('cache_control')) ||
            parsed.input?.some((item) => JSON.stringify(item).includes('cache_control'));
        if (!hasCacheHint) return undefined;
        return Math.round(inputTokens * 0.9);
    } catch {
        return undefined;
    }
}

function estimateCost(log: RelayLog, inputTokens: number, outputTokens: number, cacheHitTokens: number | undefined, modelPrice?: LLMInfo): number {
    if (!modelPrice) return 0;
    const cachedTokens = Math.max(0, cacheHitTokens ?? 0);
    const nonCachedTokens = Math.max(0, inputTokens - cachedTokens);
    return ((cachedTokens * modelPrice.cache_read) + (nonCachedTokens * modelPrice.input) + (outputTokens * modelPrice.output)) * 1e-6;
}

export function getDisplayUsage(log: RelayLog, modelPrice?: LLMInfo): DisplayUsage {
    const fromLog: DisplayUsage = {
        inputTokens: log.input_tokens,
        outputTokens: log.output_tokens,
        cacheHitTokens: log.cache_hit_tokens,
        usageRecorded: hasRecordedUsage(log),
        costRecorded: true,
        inputSource: hasRecordedUsage(log) ? 'stored' : 'missing',
        outputSource: hasRecordedUsage(log) ? 'stored' : 'missing',
        cacheSource: (log.cache_hit_tokens ?? 0) > 0 ? 'stored' : 'missing',
        costSource: 'stored',
        estimatedCost: log.cost,
    };
    if (fromLog.usageRecorded) {
        if (log.cost <= 0 && modelPrice) {
            const estimated = estimateCost(log, log.input_tokens, log.output_tokens, log.cache_hit_tokens, modelPrice);
            if (estimated > 0) {
                fromLog.estimatedCost = estimated;
                fromLog.costSource = 'estimated';
            }
        }
        return fromLog;
    }

    const fromResponse = parseUsageFromResponseContent(log.response_content);
    if (fromResponse) {
        const cacheHitTokens = fromResponse.cacheHitTokens;
        const estimatedCost = estimateCost(log, fromResponse.inputTokens ?? 0, fromResponse.outputTokens ?? 0, cacheHitTokens, modelPrice);
        return {
            inputTokens: fromResponse.inputTokens ?? 0,
            outputTokens: fromResponse.outputTokens ?? 0,
            cacheHitTokens,
            usageRecorded: true,
            // costRecorded: fromLog.costRecorded || estimatedCost > 0,
            costRecorded: true,
            inputSource: 'response',
            outputSource: 'response',
            cacheSource: cacheHitTokens && cacheHitTokens > 0 ? 'response' : 'missing',
            // costSource: log.cost > 0 ? 'stored' : estimatedCost > 0 ? 'estimated' : 'missing',
            costSource: log.cost > 0 ? 'stored' : 'estimated',
            estimatedCost: log.cost > 0 ? log.cost : estimatedCost,
        };
    }

    const estimated = estimateUsageFromLogContent(log);
    if (estimated) {
        const cacheHitTokens = estimateCacheHitTokens(log, estimated.inputTokens ?? 0);
        const estimatedCost = estimateCost(log, estimated.inputTokens ?? 0, estimated.outputTokens ?? 0, cacheHitTokens, modelPrice);
        return {
            inputTokens: estimated.inputTokens ?? 0,
            outputTokens: estimated.outputTokens ?? 0,
            cacheHitTokens,
            usageRecorded: true,
            // costRecorded: fromLog.costRecorded || estimatedCost > 0,
            costRecorded: true,
            inputSource: 'estimated',
            outputSource: 'estimated',
            cacheSource: cacheHitTokens && cacheHitTokens > 0 ? 'estimated' : 'missing',
            // costSource: log.cost > 0 ? 'stored' : estimatedCost > 0 ? 'estimated' : 'missing',
            costSource: log.cost > 0 ? 'stored' : 'estimated',
            estimatedCost: log.cost > 0 ? log.cost : estimatedCost,
        };
    }

    return fromLog;
}
