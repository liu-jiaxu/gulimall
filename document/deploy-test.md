# gulimall 部署记录（jar 方式）

> 本地开发机（Windows）打 jar → 上传到 Linux 虚拟机 → `nohup java -jar` 启动。
>
> 本次部署的范围：**gulimall-product、gulimall-gateway、renren-fast（后端）+ renren-fast-vue（管理端前端）**。

---

## 一、目标架构

```
浏览器
  │
  ├─ http://gulimall.com/            客户端（Thymeleaf，由 gulimall-product 渲染）
  ├─ http://gulimall.com/gulimall/** 接口（→ Nginx → Gateway → 微服务）
  ├─ http://admin.gulimall.com/      管理端（Vue dist，Nginx 静态托管）
  └─ http://img.gulimall.com/**      图片（→ Nginx → MinIO）

宿主机（192.168.10.200）
  ├─ Java 进程（本次部署）
  │    ├─ gulimall-product  :10000
  │    ├─ gulimall-gateway  :88
  │    └─ renren-fast       :8080（context-path /renren-fast）
  │
  └─ Docker（deploy/docker-compose.yaml）
       ├─ mysql 3306 / redis 6379 / nacos 8848+8849
       ├─ minio 19000+19001
       ├─ elasticsearch 9200 / kibana 5601
       └─ nginx 80        ← 对外唯一入口
```

---

## 二、构建产物

| 产物 | 路径 | 大小 |
|---|---|---|
| gulimall-product | `gulimall-product/target/gulimall-product-0.0.1-SNAPSHOT.jar` | 82 MB |
| gulimall-gateway | `gulimall-gateway/target/gulimall-gateway-0.0.1-SNAPSHOT.jar` | 69 MB |
| renren-fast | `renren-fast/target/renren-fast.jar` | 94 MB |
| 管理端前端 | `renren-fast-vue/dist/` | 9.4 MB |

---

## 三、构建命令

### 3.1 后端（Maven）

```bash
cd D:\SoftwareInstallation\gulimall

# product + gateway（-am 会连带构建依赖的 gulimall-common、gulimall-api）
mvn clean package -DskipTests -pl gulimall-product,gulimall-gateway -am

# renren-fast 是独立工程，单独构建
cd renren-fast && mvn clean package -DskipTests
```

**⚠️ 构建前必须停掉 IDEA 里正在运行的 product**，否则会报：

```
failed with FileSystemException: ... 另一个程序正在使用此文件，进程无法访问
```

原因：运行中的应用锁住了 `target/` 下的文件。停掉进程后重跑即可（有时杀毒软件也会短暂锁文件，重试一次通常就好）。

### 3.2 前端（Node 16）

**⚠️ 必须用 Node 16**。`node-sass 6.0.1` 是原生模块，只支持到 Node 16；用 Node 18/20/24 会报：

```
Error: Node Sass does not yet support your current environment:
Windows 64-bit with Unsupported runtime (137)
```

`nvm` 里已有 v16.20.2，临时切过去构建：

```bash
export PATH="/d/SoftwareInstallation/nvm/nvm/v16.20.2:$PATH"
node -v          # 应显示 v16.20.2

cd D:\SoftwareInstallation\gulimall\renren-fast-vue
npm run build    # 实际执行 gulp，产物在 dist/
```

### 3.3 ⚠️ 构建读的是 `index-prod.js`，不是 `index.js`

`gulpfile.js` 里：

```js
var env = process.env.npm_config_qa ? 'qa' : process.env.npm_config_uat ? 'uat' : 'prod';
```

默认 `prod`，所以构建**合并的是 `static/config/index-prod.js`**。

| 文件 | 何时生效 | baseUrl 应设为 |
|---|---|---|
| `static/config/index.js` | `npm run dev`（本地开发） | `http://localhost:88/gulimall` |
| **`static/config/index-prod.js`** | **`npm run build`（部署）** | **`http://admin.gulimall.com/gulimall`** |

两份配置相互独立，改错了构建出来的 dist 就连不上后端（默认值是指向 renren 官方演示服务器的）。

**构建后务必验证**：

```bash
grep baseUrl dist/config/index.js
# 应显示：window.SITE_CONFIG['baseUrl'] = 'http://admin.gulimall.com/gulimall';
```

---

## 四、服务器准备（只做一次）

```bash
# ① JDK 21（系统是 el10）
dnf install -y java-21-openjdk
java -version

# ② 目录
mkdir -p /mydata/app /mydata/logs

# ③ 关闭防火墙（原因见下）
systemctl stop firewalld
systemctl disable firewalld

# ④ 确认（两个都应为 inactive / disabled）
systemctl is-active firewalld
systemctl is-enabled firewalld
```

### ⚠️ 为什么必须处理防火墙

**症状**：Java 应用在宿主机上跑得好好的（`ss -lntp` 能看到监听 `*:10000`），但 Nginx 反代过去返回 **502**：

```
nginx | [error] connect() failed (113: Host is unreachable) while connecting to upstream,
        upstream: "http://172.17.0.1:10000/", host: "gulimall.com"
```

**关键在错误码**：

| 错误码 | 含义 | 说明 |
|---|---|---|
| **113 `Host is unreachable`** | 网络层不通 | **防火墙拦了** ← 本例 |
| 111 `Connection refused` | 端口没人监听 | 服务没启动 |

**原因**：Nginx 在容器里，通过 `host.docker.internal`（→ docker 网桥 IP `172.17.0.1`）访问**宿主机上的 Java 进程**。这段流量走宿主机的 INPUT 链，而 firewalld 默认不放行 —— 注意它和 docker 发布的端口是两条不同的路径：

```
容器 → 宿主机【docker 发布的端口】(3306/8848/80…)   → docker 自己写 iptables，firewalld 管不着 ✅
容器 → 宿主机【普通进程监听的端口】(10000/88)        → 走 INPUT 链，被 firewalld 拦 ❌
```

所以 MySQL / Nacos 从容器访问一直正常，但 product / gateway 就是连不上 —— 这个差异很容易让人误判成"服务没起来"。

### 两种处理方式

**方式 A：关闭防火墙**（本文档采用，适合内网测试机）

```bash
systemctl stop firewalld
systemctl disable firewalld
# 有些服务依赖会把它拉起来，加一条彻底屏蔽：
# systemctl mask firewalld
```

顺带确认 `nftables` 也没开：

```bash
systemctl is-active nftables      # 若为 active，同样 stop + disable
```

> ⚠️ **代价**：这台机器的所有端口都不再被系统防火墙过滤，**同局域网内任何人都能访问它开放的所有端口**。
> 测试机、纯内网环境可以这么干；一旦机器放到公网或办公网（有其他人），**必须换成方式 B**。

**方式 B：只信任 docker 网桥**（保留防火墙，推荐给生产）

```bash
# 找出 docker 相关网桥
ip -o link show | grep -o 'br-[0-9a-f]*'

# 每个网桥 + docker0 都加入 trusted 区
firewall-cmd --permanent --zone=trusted --add-interface=docker0
firewall-cmd --permanent --zone=trusted --add-interface=br-<上面查到的名字>
firewall-cmd --reload
```

**为什么是"信任网桥"而不是"放行端口"**：以后 gateway(88)、其他微服务(7000/8000/9000/11000…) 都会走这条路径，逐个放行端口要放行一大串；信任网桥**一次覆盖所有端口**。

**快速验证是不是防火墙问题**（不改配置）：

```bash
systemctl stop firewalld
curl -I http://gulimall.com/          # 变成 200 → 确认是它
```

---

## 五、上传

在 **Windows 终端**执行：

```bash
cd /d/SoftwareInstallation/gulimall

# 三个 jar
scp gulimall-product/target/gulimall-product-0.0.1-SNAPSHOT.jar root@192.168.10.200:/mydata/app/
scp gulimall-gateway/target/gulimall-gateway-0.0.1-SNAPSHOT.jar root@192.168.10.200:/mydata/app/
scp renren-fast/target/renren-fast.jar root@192.168.10.200:/mydata/app/

# 管理端前端（拷 dist 里的【内容】，不是 dist 目录本身）
ssh root@192.168.10.200 "mkdir -p /mydata/docker/nginx/html/admin"
scp -r renren-fast-vue/dist/* root@192.168.10.200:/mydata/docker/nginx/html/admin/
```

**确认前端目录结构正确**：

```bash
ls /mydata/docker/nginx/html/admin/
# 期望：202609172204  config  index.html
```

> 如果拷成了 `admin/dist/index.html`，nginx 的 `root /usr/share/nginx/html/admin` 就找不到 `index.html` → 403。

---

## 六、前置检查：Nacos 里要有配置

三个服务启动时会从 Nacos 拉配置（`spring.config.import: optional:nacos:xxx.yaml`）。

| 服务 | 需要的 Nacos 配置 |
|---|---|
| gulimall-product | `gulimall-product.yaml` |
| gulimall-gateway | **`application-gateway.yaml`**（路由配置，缺了网关没有任何路由） |
| renren-fast | 不用 Nacos，读本地 `application.yml` |

> `optional:` 前缀意味着**缺失不报错**，但会静默使用空配置 → 表现为"服务起来了但连不上数据库"。别被这个前缀骗了。

---

## 七、启动（顺序不能乱）

```bash
cd /mydata/app

# ① 基础服务：product（客户端页面 + 商品接口）
nohup java -jar gulimall-product-0.0.1-SNAPSHOT.jar > /mydata/logs/product.log 2>&1 &

# ② 后台管理后端
nohup java -jar renren-fast.jar > /mydata/logs/renren-fast.log 2>&1 &

# ③ 网关（最后，它要发现前面注册上去的服务）
nohup java -jar gulimall-gateway-0.0.1-SNAPSHOT.jar > /mydata/logs/gateway.log 2>&1 &
```

**确认启动成功**：

```bash
tail -f /mydata/logs/product.log      # 出现 Started GulimallProductApplication
tail -f /mydata/logs/gateway.log      # 出现 Started GulimallGatewayApplication
```

**Nacos 控制台 → 服务列表**应有：`gulimall-product`、`gulimall-gateway`、`renren-fast`。

---

## 八、Nginx

配置已改好（`deploy/nginx/conf/conf.d/gulimall.conf`）：

| 域名 | location | 指向 |
|---|---|---|
| gulimall.com | `/` | `host.docker.internal:10000`（product，Thymeleaf） |
| gulimall.com | `/gulimall/` | 网关 `:88` |
| gulimall.com | `/es/` | 本地 `html/es/fenci.txt` |
| admin.gulimall.com | `/` | 本地 `html/admin/`（管理端 dist） |
| admin.gulimall.com | `/gulimall/` | 网关 `:88` |
| img.gulimall.com | `/` | `minio:9000` |

生效：

```bash
cd /mydata/docker
docker compose exec nginx nginx -t        # 必须 successful
docker compose restart nginx
```

---

## 九、hosts（Windows 三行）

`C:\Windows\System32\drivers\etc\hosts`（**管理员权限**打开）：

```
192.168.10.200  gulimall.com
192.168.10.200  admin.gulimall.com
192.168.10.200  img.gulimall.com
```

然后 `ipconfig /flushdns`。

> ⚠️ `gulimall.com` 是**真实注册的公网域名**。不配 hosts 会解析到公网上的 AWS 服务器，浏览器还会缓存它的 HSTS 导致后续强制跳 https。排查见 `../deploy/DEPLOY.md`「八、常见问题」。

---

## 十、验证

**服务端**：

```bash
curl -I  http://gulimall.com/                                 # 200
curl     'http://gulimall.com/gulimall/product/brand/list'    # JSON
curl -I  http://admin.gulimall.com/                           # 200
curl     http://gulimall.com/es/fenci.txt                     # IK 词库
```

**浏览器**：

| 地址 | 内容 | 账号 |
|---|---|---|
| `http://gulimall.com/` | 商城前台 | 无 |
| `http://admin.gulimall.com/` | 后台管理 | `admin` / `admin` |

---

## 十一、日常运维

```bash
# 看日志
tail -f /mydata/logs/product.log

# 停止
pkill -f gulimall-product
pkill -f gulimall-gateway
pkill -f renren-fast.jar

# 更新某个服务
pkill -f gulimall-product && sleep 3
nohup java -jar /mydata/app/gulimall-product-0.0.1-SNAPSHOT.jar > /mydata/logs/product.log 2>&1 &
```

---

## 十二、排查速查

| 现象 | 原因 |
|---|---|
| `gulimall.com/` → **502**，日志 `113 Host is unreachable` | **firewalld 拦了容器→宿主的流量**（见 §四） |
| `gulimall.com/` → **502**，日志 `111 Connection refused` | product 没启动 |
| `gulimall.com/` → **502**，日志正常但 upstream 连不上 | product 没启动 / 没注册到 Nacos |
| `/gulimall/**` → **502** | gateway 没启动 |
| `/gulimall/**` → **503** | gateway 在线但目标微服务不在线（多半是注册 IP 挑错网卡，见下） |
| `admin.gulimall.com/` → **403** | dist 没放对位置，检查 `html/admin/index.html` 是否直接存在 |
| 页面能开但接口全失败 | 看 F12 请求地址，baseUrl 应是 `admin.gulimall.com/gulimall` |
| 域名解析到公网 IP | hosts 没配 / 代理工具没读 hosts / 浏览器 HSTS |
| 构建报 `另一个程序正在使用此文件` | 停掉 IDEA 里运行的应用 |
| 构建报 `Node Sass ... Unsupported runtime` | 没用 Node 16 |

---

## 十三、测试完成后要删除的东西

### 13.1 本地（Windows）

| 删什么 | 路径 | 说明 |
|---|---|---|
| Maven 构建产物 | `gulimall-*/target/`、`renren-fast/target/` | 随时可重新构建，占空间大 |
| 前端构建产物 | `renren-fast-vue/dist/` | 同上 |
| 前端依赖（可选） | `renren-fast-vue/node_modules/` | 几百 MB，但重装很慢，**不建议删** |
| IDEA 运行配置 | — | 如果不再本地调试，可删 Run Configuration |

### 13.2 服务器

**先停进程，再删文件**：

```bash
# ① 停掉三个 Java 进程
pkill -f gulimall-product
pkill -f gulimall-gateway
pkill -f renren-fast.jar
jps | grep -i gulimall        # 确认已无残留

# ② 删部署产物
rm -rf /mydata/app/*.jar
rm -rf /mydata/logs/*.log
rm -rf /mydata/docker/nginx/html/admin/

# ③ 删测试期间的临时文件
rm -f /mydata/docker/es/elasticsearch-analysis-ik-*.zip   # IK 安装包（已解压到 plugins，压缩包不用留）
rm -f /tmp/*.sql /tmp/*.ndjson                            # 上传的 SQL / 导入的测试数据
```

**ES 测试索引**（`ES-console.sh` 造的，跟业务无关）：

```
DELETE bank
DELETE newbank
DELETE my_index
DELETE customer
```

### 13.3 环境痕迹

| 痕迹 | 在哪清 | 说明 |
|---|---|---|
| hosts 三行 | `C:\Windows\System32\drivers\etc\hosts` | 不再用域名访问时删；删完 `ipconfig /flushdns` |
| 浏览器 HSTS | `chrome://net-internals/#hsts` | 删 `gulimall.com`、`img.gulimall.com`（留着也无害） |
| Clash 直连规则 | Clash 配置 | 如果测试期间加过 `DOMAIN-SUFFIX,gulimall.com,DIRECT` |
| **firewalld 被关闭** | 服务器 | 测试完**建议重新开启**：`systemctl enable --now firewalld`。若开了防火墙又需要容器访问宿主进程，改用 §四 的**方式 B**（信任 docker 网桥），别直接关 |

### 13.4 ⚠️ 千万不要删的

| 保留什么 | 路径 | 为什么 |
|---|---|---|
| 中间件数据 | `deploy/mysql/data`、`redis/data`、`nacos/data`、`es/data`、`minio/data` | **删了数据全没**。`docker compose down -v` 也会删，慎用 |
| IK 插件 | `deploy/es/plugins/analysis-ik/` | 重装要重新下载解压授权，麻烦 |
| 各服务配置文件 | `deploy/es/config/elasticsearch.yml`、`deploy/kibana/config/kibana.yml`、`deploy/nginx/`、`deploy/seata-config/` | 手工写的，删了要重写 |
| IK 词库 | `deploy/nginx/html/es/fenci.txt` | 自定义分词词表 |
| 数据库 SQL | `sql/*.sql` | 建库脚本 |
