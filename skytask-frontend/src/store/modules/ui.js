const state = () => ({
  breadcrumbs: []
});

const mutations = {
  SET_BREADCRUMBS(state, breadcrumbs) {
    state.breadcrumbs = breadcrumbs;
  }
};

const actions = {
  setBreadcrumbs({ commit }, breadcrumbs) {
    commit('SET_BREADCRUMBS', breadcrumbs);
  }
};

const getters = {
  breadcrumbs: (state) => state.breadcrumbs
};

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
};
