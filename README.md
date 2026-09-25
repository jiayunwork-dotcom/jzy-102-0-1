# 水力侵蚀地形实验室（Hydraulic Erosion Terrain Lab）

一个在浏览器里实时摆弄的地形生成 + 水力侵蚀演示工具：先用多层分形噪声生成隆起地表，
再真实地跑一遍粒子式水力侵蚀物理模拟，看着雨滴沿最陡坡度下滑、边冲边淤，
把光秃秃的山体一点点啃出河谷沟壑、在低地堆出淤积扇。**不是预渲染动画**，
每一次点击都是后端现场计算。

- 后端：Java 17 + Spring Boot（噪声生成、侵蚀模拟、参数校验、内存快照各自独立成模块）
- 前端：Vue 3 + Three.js + Vite（三维渲染、参数面板、后端请求各自独立成组件）
- 部署：Docker Compose 一键拉起，只暴露一个页面地址

---

## 快速开始

### Docker Compose（推荐）

```bash
docker compose up --build
# 打开 http://localhost:8080
```

`frontend` 容器内置 nginx，静态页面在 80 端口对外映射为 `8080`，
并把 `/api/*` 反向代理到 `backend:8080`，浏览器只访问一个源。

### 本地开发

后端（需要 JDK 17、Maven 3.9+）：

```bash
cd backend
mvn spring-boot:run          # http://localhost:8080
```

前端（需要 Node 18+）：

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173 （/api 已代理到 8080）
```

---

## 操作方式

1. 在左侧「地形生成」面板调整 **网格分辨率 / 噪声层数 / 频率倍增 / 振幅衰减 / 基础频率 / 种子**，
   点击 **生成地形**。
2. 在「水力侵蚀」面板调整 **雨滴数量 / 侵蚀率 / 沉积率 / 蒸发率 / 输沙能力 / 惯性 / 重力 / 最大步数**，
   反复点击 **跑一轮侵蚀**，每点一次就在当前高度场上追加一轮降雨冲刷，画面即时刷新。
3. 顶部 HUD 实时显示均值、**高度方差**、梯度能量和高程范围；
   高侵蚀率会让方差明显增大（山脊更尖、沟谷更深、低地淤积）。
4. 鼠标左键旋转、滚轮缩放、右键平移；可调方向光的方位角/高度角和地形垂直夸张。
5. 在「快照」里输入名字 **另存** 当前高度场与全部参数；从列表 **载入** 即可重新摆出。
   快照只存在后端内存里，进程重启清空（符合需求）。

---

## 物理模型

### 地形（fBm 值噪声）

`noise/TerrainGenerator.java` 把多层值噪声叠加：

```
h(x,y) = Σ_o persistence^o · noise_o(x · baseFrequency · lacunarity^o,
                                     y · baseFrequency · lacunarity^o)
```

每多一层，频率乘 `lacunarity`、振幅乘 `persistence`；每层使用由主种子派生出的独立噪声格点。
层数越多，叠加的高频细节越多，地表梯度的整体波动（梯度能量）随之上升。

### 水力侵蚀（`erosion/ErosionSimulator.java`）

每颗雨滴：

1. 落在随机位置（随机数全部来自种子控制的 `Rng`）；
2. 沿**固定路由床面**（对原始地形做几次盒式模糊）的最陡梯度转向，带惯性；
3. 沿落差累积速度 `v ← √(v² + g·Δh)`；
4. 每一步先蒸发 `water ← water·(1−evapRate)`，水量**严格单调不增**，
   一旦 `water ≤ minWater` 立即终止（终止前先把携带泥沙沉积回去）；
5. 输沙能力 `capacity = max(0,−Δh) · speed · water · K`：
   - 携带量超过能力或逆流上坡 → 按沉积率**淤积**（宽锥形刷，模拟扩散的淤积扇）；
   - 否则按侵蚀率**下切**（集中在当前格，刻出窄沟），单步掏蚀量不超过
     `carveFraction · 落差`，保证稳定。
6. 越界、步数耗尽、水量耗尽都会终止；任何终止路径都把残余泥沙归还地面。

**关键设计——固定路由床面 + 非对称刷：**
经典对称侵蚀模型（侵蚀/沉积用同一个刷）在数学上趋向把地表抹平，
高度方差随侵蚀率升高反而下降。这里让水流始终在原始平滑地形上寻路（下切出的窄沟
不会虹吸更多水流、切断自我加深的正反馈），同时用「点下切 + 宽淤积」的非对称刷，
使泥沙从狭窄谷线被剥离、在下游低地扩散沉积——于是侵蚀越强，山脊越尖、河谷越深、
方差越大；而固定床面让任意多轮冲刷都保持有界、可重现（同一批种子的雨滴在固定床面上
行为恒定，每轮增加量相同）。

---

## 被自动化测试锁死的核心不变量

`mvn test`（共 37 个测试），重点：

| 不变量 | 测试 |
| --- | --- |
| **只调大侵蚀率，同种子同雨滴，高度方差更大** | `ErosionInvariantsTest.higherErosionRateProducesGreaterVariance`（多种子） |
| **固定种子重复两次，高度场逐位一致** | `ErosionInvariantsTest.fixedSeedReproducesIdenticalField`（`assertArrayEquals`） |
| **层数增多，地表梯度整体波动上升** | `TerrainGeneratorTest.moreOctavesIncreaseGradientEnergy` |
| **水量严格单调不增；蒸发到阈值必终止，终止后不再侵蚀** | `ErosionInvariantsTest.waterIsNonIncreasing`、`WaterTerminationTest` |
| **局部质量守恒，前后总高度和只差浮点误差（~1e-10）** | `ErosionInvariantsTest.massConservedToFloatingPointEpsilon` |
| **非法参数计算前以 400 拦截（分辨率/层数/衰减系数/负速率）** | `TerrainValidatorTest`、`TerrainApiIntegrationTest` |

快照存取的独立测试见 `SnapshotStoreTest`，生成→侵蚀→另存→列表→载入全链路见
`TerrainApiIntegrationTest`。

---

## 后端模块结构

```
noise/        ValueNoise, Rng, NoiseParams, TerrainGenerator   噪声生成
erosion/      ErosionSimulator, ErosionParams, Brush, BedSurface  侵蚀模拟核心
snapshot/     Snapshot, SnapshotStore                           内存键值快照
validation/   TerrainValidator, InvalidParamsException, GlobalExceptionHandler  参数校验
service/      TerrainService, TerrainStats                      编排与统计
api/          TerrainController, ErosionController, SnapshotController + dto
```

REST 接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/terrain/generate` | 用噪声参数生成新高度场 |
| POST | `/api/erosion` | 在给定高度场上追加一轮侵蚀 |
| POST | `/api/snapshots` | 按名字保存高度场 + 参数 |
| GET  | `/api/snapshots` | 快照列表（不含高度数据） |
| GET  | `/api/snapshots/{name}` | 取某个快照的完整高度数据 |

非法请求统一返回 `400 {"error":"invalid_parameters","message":"..."}`，
重名快照返回 `409`，不存在的快照返回 `404`。

## 前端组件结构

```
src/components/TerrainView.js     Three.js 场景：网格、轨道相机、方向光、分层着色
src/components/ParameterPanel.vue 噪声/侵蚀/光照参数滑块 + 快照入口
src/api/terrainApi.js             与后端的全部 HTTP 交互
src/App.vue                       状态编排、HUD、消息提示
```

分层着色：高程在低处的蓝绿色与高处的浅米色之间插值，坡度超过阈值的陡坡再向
裸露岩土的棕褐色混合。
