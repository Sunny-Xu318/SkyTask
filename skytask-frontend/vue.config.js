const path = require('path');

module.exports = {
  outputDir: 'dist',
  productionSourceMap: false,
  css: {
    loaderOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`
      }
    }
  },
  devServer: {
    port: 8090,
    proxy: {
      '^/api': {
        target: 'http://localhost:8081',  // scheduler 服务 (skytask-scheduler)
        changeOrigin: true
      },
      '^/auth': {
        target: 'http://localhost:8084',  // auth 服务 (skytask-auth)
        changeOrigin: true
      }
    }
  },
  configureWebpack: {
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    }
  }
};
