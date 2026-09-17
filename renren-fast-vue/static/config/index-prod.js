/**
 * 生产环境
 */
;(function () {
  window.SITE_CONFIG = {};

  // api接口请求地址
  // 【旧】renren 官方演示服务器
  // window.SITE_CONFIG['baseUrl'] = 'http://demo.open.renren.io/renren-fast-server';
  // 【旧】本地开发直连网关
  // window.SITE_CONFIG['baseUrl'] = 'http://localhost:88/gulimall';
  // 【新】走 Nginx 统一入口（admin.gulimall.com）
  window.SITE_CONFIG['baseUrl'] = 'http://admin.gulimall.com/gulimall';

  // cdn地址 = 域名 + 版本号
  window.SITE_CONFIG['domain']  = './'; // 域名
  window.SITE_CONFIG['version'] = '';   // 版本号(年月日时分)
  window.SITE_CONFIG['cdnUrl']  = window.SITE_CONFIG.domain + window.SITE_CONFIG.version;
})();
