// Java 后端尚未实现这些更新相关接口：
// GET /api/v1/update
// GET /api/v1/update/now-version
// POST /api/v1/update
// 因此当前文件中的相关类型与 hooks 先注释停用，待后端实现后再恢复。

// import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
// import { apiClient } from '../client';
// import { logger } from '@/lib/logger';

/**
 * 后端 /api/v1/update 返回的最新发布信息
 */
// export interface LatestInfo {
//     tag_name: string;
//     published_at: string;
//     body: string;
//     message: string;
// }

/**
 * 获取最新发布信息 Hook
 * 
 * @example
 * const { data: latestInfo, isLoading, error } = useLatestInfo();
 * 
 * if (isLoading) return <Loading />;
 * if (error) return <Error message={error.message} />;
 * 
 * console.log('Latest tag:', latestInfo?.tag_name);
 */
// export function useLatestInfo() {
//     return useQuery({
//         queryKey: ['update', 'latest'],
//         queryFn: async () => {
//             return apiClient.get<LatestInfo>('/api/v1/update');
//         },
//         refetchInterval: 3600000, // 1 小时
//         refetchOnMount: 'always',
//     });
// }

/**
 * 获取后端当前版本 Hook
 *
 * 后端: GET /api/v1/update/now-version -> string
 */
// export function useNowVersion() {
//     return useQuery({
//         queryKey: ['update', 'now-version'],
//         queryFn: async () => {
//             return apiClient.get<string>('/api/v1/update/now-version');
//         },
//         refetchInterval: 3600000, // 1 小时
//         refetchOnMount: 'always',
//     });
// }

/**
 * 执行更新 Hook
 * 
 * @example
 * const updateCore = useUpdateCore();
 * 
 * updateCore.mutate(undefined, {
 *   onSuccess: () => {
 *     console.log('Update started successfully');
 *   },
 * });
 */
// export function useUpdateCore() {
//     const queryClient = useQueryClient();

//     return useMutation({
//         mutationFn: async () => {
//             return apiClient.post<string>('/api/v1/update');
//         },
//         onSuccess: (data) => {
//             logger.log('更新成功:', data);
//             queryClient.invalidateQueries({ queryKey: ['update', 'latest'] });
//             queryClient.invalidateQueries({ queryKey: ['update', 'now-version'] });
//         },
//         onError: (error) => {
//             logger.error('更新失败:', error);
//         },
//     });
// }

