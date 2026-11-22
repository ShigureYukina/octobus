'use client';

import { useMemo } from 'react';
import { GroupCard } from './Card';
import { useGroupList } from '@/api/endpoints/group';
import { useChannelList } from '@/api/endpoints/channel';
import { useSearchStore, useToolbarViewOptionsStore } from '@/components/modules/toolbar';
import { VirtualizedGrid } from '@/components/common/VirtualizedGrid';

export function Group() {
    const { data: groups } = useGroupList();
    const { data: channels } = useChannelList();
    const pageKey = 'group' as const;
    const searchTerm = useSearchStore((s) => s.getSearchTerm(pageKey));
    const sortField = useToolbarViewOptionsStore((s) => s.getSortField(pageKey));
    const sortOrder = useToolbarViewOptionsStore((s) => s.getSortOrder(pageKey));
    const filter = useToolbarViewOptionsStore((s) => s.groupFilter);

    const channelLastUsedMap = useMemo(() => {
        const map = new Map<number, number>();
        for (const ch of channels ?? []) {
            map.set(ch.raw.id, ch.last_used_time || 0);
        }
        return map;
    }, [channels]);

    const groupLastUsedMap = useMemo(() => {
        const map = new Map<number, number>();
        for (const group of groups ?? []) {
            let maxTime = 0;
            for (const item of group.items ?? []) {
                const t = channelLastUsedMap.get(item.channel_id) || 0;
                if (t > maxTime) maxTime = t;
            }
            map.set(group.id ?? 0, maxTime);
        }
        return map;
    }, [groups, channelLastUsedMap]);

    const sortedGroups = useMemo(() => {
        if (!groups) return [];
        return [...groups].sort((a, b) => {
            if (sortField === 'name') {
                const diff = a.name.localeCompare(b.name);
                return sortOrder === 'asc' ? diff : -diff;
            }
            if (sortField === 'last_used') {
                const diff = (groupLastUsedMap.get(a.id ?? 0) || 0) - (groupLastUsedMap.get(b.id ?? 0) || 0);
                return sortOrder === 'asc' ? diff : -diff;
            }
            const diff = (a.id || 0) - (b.id || 0);
            return sortOrder === 'asc' ? diff : -diff;
        });
    }, [groups, sortField, sortOrder, groupLastUsedMap]);

    const visibleGroups = useMemo(() => {
        const term = searchTerm.toLowerCase().trim();
        const byName = !term ? sortedGroups : sortedGroups.filter((g) => g.name.toLowerCase().includes(term));

        if (filter === 'with-members') return byName.filter((g) => (g.items?.length || 0) > 0);
        if (filter === 'empty') return byName.filter((g) => (g.items?.length || 0) === 0);

        return byName;
    }, [sortedGroups, searchTerm, filter]);

    return (
        <VirtualizedGrid
            items={visibleGroups}
            columns={{ default: 1, md: 2, lg: 3 }}
            estimateItemHeight={520}
            getItemKey={(group, index) => group.id ?? `group-${index}`}
            renderItem={(group) => <GroupCard group={group} />}
        />
    );
}
