# 地形水力侵蚀演示工具

面向地理科普的浏览器端小工具：用多层噪声生成光秃秃的隆起地表，再**真实逐颗模拟雨滴**
沿坡面下滑、侵蚀、搬运、沉积的物理过程，看着山脊被一点点啃出河谷与沟壑。
所有计算都在后端实时完成，不是预渲染动画；参数可以现场改、现场跑。

## 快速开始

```bash
docker compose up --build
```

然后打开 <http://localhost> 即可（前端静态资源由 nginx 托管，`/api` 反向代理到后端）。
若 80 端口被占用，把 `docker-compose.yml` 里前端的端口映射改成如 `"8080:80"` 再访问对应端口。

### 本地开发（不用 Docker）

```bash
# 后端（Java 17 + Maven）
cd backend && mvn spring-boot:run          # http://localhost:8080

# 前端（Node 20+），开发服务器已配置 /api 代理到本机 8080
cd frontend && npm install && npm run dev  # http://localhost:5173
```

### 运行后端测试

```bash
cd backend && mvn test
```

## 玩法说明

1. 左侧面板调噪声参数（分辨率、层数、频率倍增、振幅衰减、种子），点 **生成地形**；
2. 调侵蚀参数（雨滴数量、侵蚀率、沉积率、蒸发率等），点 **侵蚀一轮** —
   在当前地形上追加模拟一轮，可连点观察沟壑逐渐加深；
3. 拖动旋转、滚轮缩放、右键平移查看；光照方位角/高度角可调；
4. 地形按高度与坡度分层着色：低处偏绿、高处偏白、陡坡偏岩褐；
5. **快照另存**把当前高度场连同参数按名字存进内存，可随时从列表**加载**回看
   （进程重启即丢失，不做持久化）；
6. 每轮侵蚀后面板显示整图总高度变化 —— 质量守恒偏差只有浮点误差量级。

## 仓库结构

```
backend/                          Java 17 + Spring Boot
  src/main/java/com/geoviz/terrain/
    noise/NoiseGenerator.java     多层 Perlin 噪声高度场生成（频率倍増、振幅衰减）
    noise/NoiseParams.java
    erosion/ErosionSimulator.java 雨滴式水力侵蚀核心（单线程、种子可控、质量守恒）
    erosion/ErosionParams.java
    erosion/DropletListener.java  雨滴生命周期监听（测试用来观测不变量）
    erosion/ErosionResult.java / ErosionStats.java
    snapshot/SnapshotStore.java   快照存取：内存键值存储，按名存取
    snapshot/Snapshot.java / SnapshotMeta.java
    validation/ParameterValidator.java  参数校验：计算前拦截非法参数
    validation/InvalidParameterException.java
    api/TerrainController.java    POST /api/terrain/generate · /api/terrain/erode
    api/SnapshotController.java   POST/GET /api/snapshots[/{name}]
    api/ApiExceptionHandler.java  非法参数 → 400 + 具体原因
  src/test/java/...               锁定核心不变量的自动化测试（见下）
frontend/                         Vue 3 + Three.js + Vite
  src/components/TerrainViewer.vue   三维渲染：网格、轨道相机、方向光、分层着色
  src/components/ParameterPanel.vue  参数面板：滑块、按钮、快照入口
  src/api/terrainApi.js              与后端的全部请求交互
  src/App.vue                        状态编排（当前高度场、快照、统计）
docker-compose.yml               一并拉起前后端，暴露 http://localhost
```

## 物理模型

**噪声地形**：若干层 2D Perlin 噪声叠加，第 i 层频率 = 基础频率 × lacunarityⁱ、
振幅 = persistenceⁱ，形成有主体起伏又带细节的地表。

**雨滴侵蚀**（每颗雨滴）：

1. 落在随机位置（落点由种子化的随机数决定）；
2. 每步用双线性插值取脚下高度与梯度，沿「惯性 + 最陡下降」混合方向前进一格；
3. 携带能力 = (流速 + 基础流量) × 水量 × 携带系数：
   含沙量超过携带能力（或在爬坡）→ 按沉积率把超出部分沉回地表；
   否则按侵蚀率从地表带走泥沙（以小刷子分摊，沟壑因此有宽度）；
4. 速度随下滑落差累积（speed² += 落差 × 重力），水量随路程蒸发
   （water ×= 1 − 蒸发率）；
5. 水量低于阈值、步数耗尽或滑出地图时消亡，消亡前把剩余泥沙全部摊回地表。

## 自动化测试锁住的不变量

`backend/src/test/java/com/geoviz/terrain/`：

| 不变量 | 测试 |
| --- | --- |
| 只放大侵蚀率 → 高度方差更大（冲刷更狠、起伏更剧烈） | `ErosionInvariantsTest.higherErosionRateYieldsLargerHeightVariance` |
| 固定种子同参数跑两次 → 高度场逐比特一致 | `ErosionInvariantsTest.sameSeedAndParamsReproduceIdenticalHeightmap` |
| 雨滴水量单调不升，耗尽立即终止，不会"空水"继续侵蚀 | `ErosionInvariantsTest.dropletWaterIsMonotonicAndDepletionTerminatesDroplet` |
| 一轮模拟前后整图总高度和只差浮点误差量级（质量守恒） | `ErosionInvariantsTest.totalHeightIsConservedUpToFloatingPointError` |
| 噪声层数调多 → 地表梯度整体波动幅度上升 | `NoiseGeneratorTest.moreOctavesIncreaseGradientVariation` |
| 分辨率/层数/衰减系数越界、速率为负等 → 计算前带原因拒绝 | `ParameterValidatorTest`、`TerrainApiTest` |
| 快照按名存取、同名覆盖、列表元信息 | `SnapshotStoreTest`、`TerrainApiTest` |

> 实现备注：携带能力刻意**不**直接乘落差（只通过速度积分间接体现坡度），
> 否则「坑越深 → 侵蚀越狠」的正反馈会让多轮模拟发散；侵蚀用刷子分摊、
> 中途沉积分双线性集中，是同时满足「沟壑连贯」「多轮有界」
> 「侵蚀率越高方差越大」与「质量严格守恒」的关键结构。

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/terrain/generate` | 按噪声参数生成高度场 |
| POST | `/api/terrain/erode` | 在提交的高度场上追加一轮侵蚀，返回新高度场与统计 |
| POST | `/api/snapshots` | 命名保存当前高度场与参数 |
| GET | `/api/snapshots` | 列出全部快照（元信息） |
| GET | `/api/snapshots/{name}` | 取回某份快照的完整数据 |

参数非法（分辨率越界、层数/衰减系数不合理、速率为负、高度场长度不符等）
一律在计算前返回 `400 {"error": "具体原因"}`。
