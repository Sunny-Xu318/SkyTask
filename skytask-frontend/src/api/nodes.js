import http from './http';

// 与后端 NodeController 的 @RequestMapping("/api/scheduler/nodes") 对应
export const fetchNodeList = () => http.get('/scheduler/nodes');

export const fetchNodeMetrics = () => http.get('/scheduler/nodes/metrics');

export const fetchNodeHeartbeat = (nodeId) =>
  http.get(`/scheduler/nodes/${nodeId}/heartbeat`);

export const offlineNode = (nodeId) =>
  http.post(`/scheduler/nodes/${nodeId}/offline`);

export const rebalanceShards = (nodeId) =>
  http.post(`/scheduler/nodes/${nodeId}/rebalance`);
