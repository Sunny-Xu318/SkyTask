import authApi from '../../api/auth';

const STORAGE_KEY = 'skytask-auth';

const defaultState = () => ({
  environments: [
    { label: '开发环境', value: 'dev' },
    { label: '测试环境', value: 'test' },
    { label: '生产环境', value: 'prod' }
  ],
  currentEnv: 'dev',
  accessToken: '',
  refreshToken: '',
  profile: null
});

const loadPersistedState = () => {
  if (typeof window === 'undefined') {
    return defaultState();
  }
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (!stored) {
      return defaultState();
    }
    const parsed = JSON.parse(stored);
    return {
      ...defaultState(),
      ...parsed,
      profile: parsed.profile || null
    };
  } catch (err) {
    console.warn('[auth] Failed to parse persisted state', err);
    return defaultState();
  }
};

const persistState = (state) => {
  if (typeof window === 'undefined') {
    return;
  }
  const payload = {
    currentEnv: state.currentEnv,
    accessToken: state.accessToken,
    refreshToken: state.refreshToken,
    profile: state.profile
  };
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
};

const clearPersistedState = () => {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.removeItem(STORAGE_KEY);
};

const state = () => loadPersistedState();

const mutations = {
  SET_ENV(state, env) {
    state.currentEnv = env;
    persistState(state);
  },
  SET_CREDENTIALS(state, { accessToken, refreshToken, profile }) {
    state.accessToken = accessToken;
    state.refreshToken = refreshToken;
    state.profile = profile;
    persistState(state);
  },
  CLEAR_CREDENTIALS(state) {
    const base = defaultState();
    state.accessToken = '';
    state.refreshToken = '';
    state.profile = null;
    state.currentEnv = base.currentEnv;
    clearPersistedState();
  },
  SET_PROFILE(state, profile) {
    state.profile = profile;
    persistState(state);
  }
};

const actions = {
  changeEnv({ commit }, env) {
    commit('SET_ENV', env);
  },
  async login({ commit }, payload) {
    const response = await authApi.login(payload);
    const profile = {
      username: response.username,
      displayName: response.displayName,
      tenantCode: response.tenantCode,
      tenantName: response.tenantName,
      roles: response.roles || [],
      permissions: response.permissions || []
    };
    commit('SET_CREDENTIALS', {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      profile
    });
    return response;
  },
  async refresh({ state, commit }) {
    if (!state.refreshToken) {
      throw new Error('Refresh token missing');
    }
    const response = await authApi.refreshToken(state.refreshToken);
    const profile = state.profile
        ? {
            ...state.profile,
            roles: response.roles || state.profile.roles || [],
            permissions: response.permissions || state.profile.permissions || []
          }
        : {
            username: response.username,
            displayName: response.displayName,
            tenantCode: response.tenantCode,
            tenantName: response.tenantName,
            roles: response.roles || [],
            permissions: response.permissions || []
          };
    commit('SET_CREDENTIALS', {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken ?? state.refreshToken,
      profile
    });
    return response;
  },
  async fetchProfile({ commit }) {
    const response = await authApi.fetchProfile();
    const profile = {
      userId: response.userId,
      username: response.username,
      displayName: response.displayName,
      tenantCode: response.tenantCode,
      tenantName: response.tenantName,
      roles: response.roles || [],
      permissions: response.permissions || []
    };
    commit('SET_PROFILE', profile);
    return profile;
  },
  logout({ commit }) {
    commit('CLEAR_CREDENTIALS');
  }
};

const getters = {
  environments: (state) => state.environments,
  currentEnv: (state) => state.currentEnv,
  token: (state) => state.accessToken,
  refreshToken: (state) => state.refreshToken,
  profile: (state) => state.profile,
  tenantCode: (state) => state.profile?.tenantCode || '',
  tenantName: (state) => state.profile?.tenantName || '',
  isAuthenticated: (state) => Boolean(state.accessToken && state.profile),
  hasPermission: (state) => (permission) =>
    Boolean(permission && state.profile?.permissions?.includes(permission)),
  hasAnyPermission: (state) => (permissions = []) =>
    permissions.some((permission) => state.profile?.permissions?.includes(permission)),
  hasRole: (state) => (role) => Boolean(role && state.profile?.roles?.includes(role)),
  roles: (state) => state.profile?.roles || [],
  permissions: (state) => state.profile?.permissions || []
};

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
};
