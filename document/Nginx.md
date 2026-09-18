# gulimall 访问流程说明

> **一次请求从浏览器发出后，具体经过哪些环节到达最终的服务**。

---

## 一、本项目（虚拟机）的访问流程

本地环境一共三条链路：**页面/接口**、**图片**、**后端服务间调用**。前两条来自浏览器，第三条只在服务器内部发生。

其中**页面有两个来源**，Nginx 按域名区分：

| 入口域名 | 页面由谁提供 | 前端类型 |
|---|---|---|
| `gulimall.com` | `gulimall-product` | **Thymeleaf**，服务端现场渲染 HTML |
| `admin.gulimall.com` | Nginx 直接返回磁盘文件 | **Vue 静态 dist**（`renren-fast-vue` 构建产物） |

这个区别决定了 Nginx 里 `location /` 怎么写：前者用 `proxy_pass`，后者用 `root` + `try_files`。

### 1. 页面 / 接口访问

```
浏览器
  │
  │ ① http://gulimall.com
  ↓
Windows hosts
  │
  │ ② gulimall.com → 192.168.10.200
  ↓
Linux 虚拟机
  │
  │ ③ 到达 Nginx :80
  ↓
Nginx
  │
  ├── ④ location /            → 反向代理到 gulimall-product :10000
  │                              （客户端页面是 Thymeleaf，由 product 现场渲染出 HTML）
  │
  └── ⑤ location /gulimall/   → 转发给 Gateway :88
                                    │
                                    ↓
                                  Nacos（服务注册中心：按服务名查出实例列表）
                                    │
                                    ↓
                              gulimall-order 服务
                                    │
                                    ↓
                              私有IP : 服务端口
```

**逐步说明：**

| 步骤 | 做了什么 | 为什么需要 |
|---|---|---|
| ① | 浏览器发起请求 | 用户操作 |
| ② | **hosts 把域名解析到 IP** | `gulimall.com` 是真实注册的公网域名，不配 hosts 会解析到公网那台服务器。配了才能指向我们自己的机器 |
| ③ | 请求到达虚拟机的 **80 端口** | Nginx 是**唯一对外暴露**的端口 |
| ④ / ⑤ | Nginx 按 **路径** 分流 | `/` 是客户端页面，`/gulimall/**` 是后端接口 —— 同一个域名、同一个端口，靠路径区分 |
| ④ | `/` 转发给 **`gulimall-product:10000`** | 客户端页面是 **Thymeleaf 现场渲染**的（页面文件在 product 里，不是磁盘上的静态 HTML），必须由 product 生成 HTML 再返回 |
| ⑤ | Gateway 收到 `/gulimall/order/**` | 网关的路由配置写的是 `uri: lb://gulimall-order`，**只写服务名，不写 IP** |
| — | Gateway 向 **Nacos** 查询 `gulimall-order` 的实例列表 | 拿到当前可用的 `IP:端口` 清单（可能有多个实例） |
| — | Gateway 转发到选中的实例 | 负载均衡（`lb` = LoadBalancer） |

> **④ 这里是 `proxy_pass` 而不是 `root`**，区别很关键：
>
> | 前端类型 | 页面文件在哪 | Nginx 怎么配 |
> |---|---|---|
> | **Thymeleaf**（本项目客户端） | product 的 `templates/`，**打包在服务里** | `proxy_pass` 转给 product 渲染 |
> | **静态 Vue dist**（如管理端） | 磁盘上的 `html/admin/` | `root` + `try_files`，Nginx 直接返回文件 |
>
> 配错了的典型症状：Nginx 去磁盘找 `index.html`，找不到就报
> `open() "/usr/share/nginx/html/index.html" failed (2: No such file or directory)`，最终返回 **403**。

> **关键点：从 Gateway 到微服务这一段，地址是"查出来的"，不是"写死的"。**
> 微服务启动时把自己的 `IP:端口` 注册到 Nacos，Gateway 只认服务名。
> 所以微服务扩容、迁移、换端口，**Gateway 的配置一个字都不用改** —— 这就是注册中心存在的意义。

**管理端（`admin.gulimall.com`）的链路完全同理**，只有 `location /` 那一步不同：

```
浏览器
  │
  │ ① http://admin.gulimall.com
  ↓
Windows hosts
  │
  │ ② admin.gulimall.com → 192.168.10.200
  ↓
Linux 虚拟机
  │
  │ ③ 到达 Nginx :80
  ↓
Nginx
  │
  ├── ④ location /            → 直接返回磁盘上的 html/admin/（Vue 静态文件）
  │                              try_files 做 history 回退，刷新子路由不 404
  │
  └── ⑤ location /gulimall/   → 转发给 Gateway :88（业务接口 + renren-fast 自身的接口）
```

> 管理端的接口**全部走网关**（包括 renren-fast 自己的 `/sys/*` 接口）——
> 网关里有条 `admin_route` 兜底路由（`Path=/gulimall/**`，order 99），
> 会把没被业务路由匹配的路径重写成 `/renren-fast/*` 转给后台服务。
> 所以管理端前端只需要配**一个** `baseUrl`（`http://admin.gulimall.com/gulimall`）就够了。

### 2. 图片访问

```
浏览器
  │
  │ ① http://img.gulimall.com/gulimall/brand/2026-09-07/xxx.png
  ↓
Windows hosts
  │
  │ ② img.gulimall.com → 192.168.10.200
  ↓
Linux 虚拟机
  │
  │ ③ 到达 Nginx :80
  ↓
Nginx
  │
  │ ④ proxy_pass → http://minio:9000
  ↓
MinIO（对象存储）
  │
  │ ⑤ 按路径取出对象，返回图片二进制
  ↓
浏览器
```

**与页面链路的区别：**

- 图片这条**不经过 Gateway、不经过 Nacos、不经过任何微服务** —— Nginx 直接把请求转给 MinIO
- 转发目标写的是 `minio:9000`，即 **compose 网络内的服务名**（MinIO 和 Nginx 都在 Docker 里，同一网络可直接用服务名互访）
- MinIO 的路径规则是 `/<bucket>/<object>`，所以 URL 里的 `/gulimall/brand/xxx.png` 正好对应 **bucket=`gulimall`，object=`brand/2026-09-07/xxx.png`**
- Nginx 上这条只放行了 `GET`/`HEAD`（`limit_except`）—— 图片只需要读

> **为什么要域名化？** 如果直接用 `http://192.168.10.200:19000/gulimall/xxx.png` 存进数据库，
> 图片地址就和 MinIO 的"IP + 端口"绑死了。换服务器、上云、换对象存储，**数据库里的存量地址全要改**。
> 用 `img.gulimall.com` 这一层域名隔开，后端换什么都不影响对外的地址。

### 3. 后端服务之间的调用（不经过 Nginx）

```
gulimall-order 服务
  │
  │ 需要查库存 → 调用 gulimall-ware
  ↓
OpenFeign（@FeignClient("gulimall-ware")）
  │
  │ 按【服务名】查询
  ↓
Nacos
  │
  │ 返回 gulimall-ware 的实例列表
  ↓
gulimall-ware 的私有IP:端口
```

这条链路**完全在服务器内部**，不经过 Nginx，也不经过 Gateway。跨服务的调用靠 **OpenFeign + Nacos 服务发现**直接完成。

### 4. 域名 → IP → 端口的对应关系

```
gulimall.com
      ↓
192.168.10.200:80
      ↓
    Nginx
      │
      ├── /                → gulimall-product :10000（Thymeleaf 现场渲染页面）
      │
      └── /gulimall/**     → Gateway :88
                                ↓
                              Nacos
                                ↓
                        具体微服务（私有IP:端口）

admin.gulimall.com
      ↓
192.168.10.200:80
      ↓
    Nginx
      │
      ├── /                → 磁盘静态文件 html/admin/（Vue dist）
      │
      └── /gulimall/**     → Gateway :88（所有接口，含 renren-fast 自身的）

img.gulimall.com
      ↓
192.168.10.200:80
      ↓
    Nginx
      ↓
    MinIO
```

**三个域名最终都落在 `192.168.10.200:80`** —— 同一个 Nginx 进程。
区分它们的是 HTTP 请求头里的 **`Host`**，Nginx 用 `server_name` 匹配：

| 请求的域名 | 命中的 server 块 | 走哪条链路 |
|---|---|---|
| `gulimall.com` | `server_name gulimall.com;` | `/` → product；`/gulimall/**` → Gateway |
| `admin.gulimall.com` | `server_name admin.gulimall.com;` | `/` → 静态 `html/admin/`；`/gulimall/**` → Gateway |
| `img.gulimall.com` | `server_name img.gulimall.com;` | MinIO |

这也是**为什么必须用域名、不能直接用 IP**：用 IP 访问时 `Host` 头里是 IP，三个 `server_name` 都不匹配，
请求会落到**第一个 server 块**（即 `gulimall.com`）。所以主站"看起来能用"，
但**管理端和图片站永远访问不到** —— 它们完全依赖 `Host` 匹配。

### 5. 各服务端口一览（对外 vs 仅内网）

| 服务 | 端口 | 是否对外暴露 |
|---|---|---|
| **Nginx** | 80 | ✅ **唯一对外的入口** |
| Gateway | 88 | ❌ 只给 Nginx 转发，直接访问没有意义 |
| **gulimall-product** | **10000** | ❌ 被访问两次：① Nginx 走 `/` 直接反代它（客户端页面）；② 走 `/gulimall/product/**` 经网关调它的接口 |
| 其余微服务 | 7000 / 8000 / 9000 / 11000 / 12000 / 30000 | ❌ 私有端口，只由 Gateway 通过 Nacos 发现 |
| Nacos | 8848 / 8849 | ⚠️ 控制台直连（本地环境方便，生产不对外） |
| MinIO | 19000 / 19001 | ⚠️ 19000 供程序上传，19001 是控制台 |
| MySQL / Redis / ES / Kibana | 3306 / 6379 / 9200 / 5601 | ⚠️ 同上 |

> **原则：只有 Nginx 的 80 是"给用户访问"的，其余端口都是"运维/开发自己用"的。**
> 本地环境为了方便，这些端口都在防火墙上放行了；生产环境不是这样（见下一节）。

---

## 二、生产环境的访问流程

```
浏览器
   │
   │ ① https://gulimall.com      （真实域名，走 HTTPS）
   ↓
    DNS
   │
   │ ② 解析到公网 IP（如 47.x.x.x）
   ↓
  公网 IP
   │
   │ ③ 到达负载均衡 / Nginx（443，HTTPS 在这里终结）
   ↓
Nginx / SLB / ALB
   │
   │ ④ 转发到内网的 Gateway
   ↓
  Gateway
   │
   │ ⑤ 按服务名查 Nacos
   ↓
   Nacos
   │
   │ ⑥ 返回实例列表
   ↓
微服务（私有IP:端口）
```

### 与本地环境的四处差别

**① DNS 替代 hosts**

本地靠 hosts 文件"手写"解析关系，生产靠真正的 DNS 服务（云解析 / 自建 DNS）。
作用一样（域名 → IP），但生产是**全局生效、可管理、可切换**的。

**② 公网 IP + 负载均衡**

本地只有一台虚拟机，`192.168.10.200` 就是全部。
生产通常是：一个域名指向**负载均衡器**（云上的 SLB/ALB，或自建的 LVS/Nginx 集群），后面挂**多台** Nginx / 应用服务器。

```
                    ┌── Nginx-1
浏览器 → SLB(公网IP) ┼── Nginx-2
                    └── Nginx-3
```

这样做是为了**横向扩容**和**高可用**：一台挂了，负载均衡把流量切到其他机器。

**③ 微服务彻底藏在私有网络里**

这是最本质的差别：

| | 本地环境 | 生产环境 |
|---|---|---|
| 微服务监听的地址 | `192.168.10.200:10000` | `10.0.x.x:10000`（VPC 私有地址） |
| 公网能否直接访问 | 能（同网段 + 端口放行） | **不能**。私有地址在公网上不可路由，安全组也不放行 |
| 谁能访问它 | 任何人 | **只有内网的 Nginx / Gateway** |

也就是说，生产环境里 `10.0.x.x:10000` 这个地址，**你从家里直接输是打不开的** —— 不是端口没开，而是**公网的路由器根本不认识 `10.x` 开头的地址**，包在传输途中就被丢弃了。

**④ HTTPS 终结在入口**

证书只装在 Nginx（或负载均衡器）上，对外是 `https://`；Nginx 到 Gateway、Gateway 到微服务，**内部仍然走 http 明文**。
因为这一段在私有网络里，不经过公网。

### 那生产环境怎么访问 Nacos / Kibana 这些控制台？

**默认是访问不了的，这是设计如此。** 这些控制台是管理入口，暴露在公网是严重的安全问题（历史上 ES、Kibana、Nacos 都出过未授权访问导致数据泄露甚至被入侵的事件）。

要访问，有三种方式：

| 方式 | 你要做什么 | 访问地址 |
|---|---|---|
| **VPN** | 电脑装 VPN 客户端，连上公司内网 | `http://10.0.x.x:8848`（原样，跟本地一样用） |
| **SSH 隧道** | `ssh -L 8848:10.0.x.x:8848 user@跳板机`，终端别关 | `http://localhost:8848` |
| **安全组放行你的 IP** | 云控制台加一条入站规则，来源填你的公网 IP | `http://<服务器公网IP>:8848` |

三者的共同点：**都是在"网络层"解决可达性**，而不是改地址形式。

如果确实必须从公网用浏览器访问，那就必须加访问控制（Nginx 反代 + Basic Auth + IP 白名单），**绝对不能裸奔**。

---

## 三、本地 vs 生产 对照表

| 环节 | 本地（虚拟机） | 生产 |
|---|---|---|
| 域名解析 | hosts 文件（手写） | DNS（云解析 / 自建） |
| 对外端口 | 很多（3306/6379/8848/9200…） | 只有 443（+80 跳转） |
| 入口层 | 单台 Nginx | 负载均衡 + Nginx 集群 |
| 协议 | http | https（在入口终结） |
| 微服务网络 | 和你在同一网段，可直接访问 | VPC 私有网络，公网不可达 |
| 控制台访问 | 直接 IP:端口 | VPN / 跳板机 / 安全组白名单 |
| 数据存储 | 单机 Docker 容器 | 云数据库 / 主从 / 集群 |

**一句话总结差异：**

> 本地是"**所有端口都摊在一个网段里，谁都能连**"；
> 生产是"**只留一个入口对外，其余全部藏在私有网络里**"。

本地为了调试方便，牺牲了安全性；生产反过来。
**不是地址形式变了，而是网络边界变了。**

---

## 四、常见疑问

**Q：为什么不能直接用 `http://192.168.10.200/gulimall/product/list` 访问接口？**

可以，但要理解它的含义：用 IP 访问时 `Host` 头是 `192.168.10.200`，三个 `server_name` 都匹配不上，Nginx 会把请求交给**第一个 server 块**（即 `gulimall.com` 那个）。所以功能上能用，但**管理端和图片站永远访问不到**（它们完全依赖 `Host: admin.gulimall.com` / `img.gulimall.com`）。这也是必须配 hosts 的原因。

**Q：Gateway 为什么不能去掉，直接让 Nginx 转发到微服务？**

技术上可以，但会失去：
- **统一的路由/断言/过滤器**（鉴权、限流、跨域、日志）
- **服务发现** —— Nginx 必须写死微服务的地址，微服务扩容或迁移就要改配置并 reload
- **动态路由** —— Gateway 的路由可以放 Nacos 配置中心，改了自动生效，不用重启

**Q：为什么 Nginx 转发图片不经过 Gateway？**

因为 Gateway 是**微服务的入口**，负责路由到 Java 服务。图片是 MinIO 的对象存储，跟微服务无关，让 Nginx 直接转给 MinIO 少一跳、更快。

**Q：微服务端口 10000+ 都放行了，安全吗？**

本地环境图方便，可以。**生产环境绝不能这样** —— 微服务应该只监听内网地址，安全组只放行来自 Nginx/Gateway 的流量。

**Q：改了 hosts 为什么浏览器还是不行？**

本地开发用真实域名（`gulimall.com`）有两个特有的坑：
1. **本机代理工具**（Clash / Mihomo 等）如果不读系统 hosts，会把域名解析到公网
2. **浏览器可能缓存了该域名的 HSTS**，把 `http://` 强制升级成 `https://`

排查方法和修复步骤见 `deploy/DEPLOY.md` 的「八、常见问题」。
