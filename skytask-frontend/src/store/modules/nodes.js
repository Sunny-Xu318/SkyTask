import { fetchNodeList, fetchNodeMetrics, offlineNode, rebalanceShards } from '../../api/nodes';

const state = () => ({
  nodes: [],
  metrics: {
    totalNodes: 0,
    onlineNodes: 0,
    offlineNodes: 0,
    avgCpu: 0,
    avgMemory: 0
  },
  loading: false
});

const mutations = {
  SET_NODES(state, nodes) {
    state.nodes = nodes;
  },
  SET_METRICS(state, metrics) {
    state.metrics = { ...state.metrics, ...metrics };
  },
  SET_LOADING(state, loading) {
    state.loading = loading;
  }
};

const actions = {
  async loadNodeMetrics({ commit }) {
    const metrics = (await fetchNodeMetrics()) || {};
    commit('SET_METRICS', metrics);
  },
  async loadNodes({ commit }) {
    commit('SET_LOADING', true);
    try {
      const nodes = (await fetchNodeList()) || [];
      commit('SET_NODES', nodes);
    } finally {
      commit('SET_LOADING', false);
    }
  },
  async decommissionNode({ dispatch }, nodeId) {
    await offlineNode(nodeId);
    await dispatch('loadNodes');
  },
  async rebalance({ dispatch }, nodeId) {
    await rebalanceShards(nodeId);
    await dispatch('loadNodes');
  }
};

const getters = {
  nodeMetrics: (state) => state.metrics,
  nodeList: (state) => state.nodes,
  nodesLoading: (state) => state.loading
};

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
};
