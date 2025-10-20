import { createStore } from 'vuex';
import auth from './modules/auth';
import ui from './modules/ui';
import tasks from './modules/tasks';
import nodes from './modules/nodes';

const store = createStore({
  modules: {
    auth,
    ui,
    tasks,
    nodes
  }
});

export default store;
