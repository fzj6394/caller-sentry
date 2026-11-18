# 来电哨兵（CallerSentry）

给**非国行 / 类原生安卓**（Pixel、港版三星等）补上"来电归属地 + 号码库拦截"的轻量工具。
思路参考 [Pixel Telo](https://github.com/Pixel-Tailor-CN/PixelTelo)：**本地库优先，联网只做更新和兜底**。

- 纯 Java，**不依赖 Gradle、AndroidX、Kotlin**，用 `aapt2 + javac + d8 + apksigner` 手工构建
- APK 约 1.4 MB（号段库另 4.3 MB，构建时下载）
- 最低 Android 8.0（API 26），targetSdk 34
- 号码不出手机：除"更新库"和可选的在线兜底查询外，不发任何请求

<p align="center"><img src="docs/icon.png" width="160" alt="图标"></p>

<p align="center">
  作者 <a href="https://github.com/fzj6394">@fzj6394</a> · 许可 GPL-3.0 · 欢迎提 issue / PR
</p>

## 功能

| 模块 | 说明 |
|---|---|
| 来电归属地 | 离线号段库（49.7 万条）二分查找，精确到市 + 运营商；支持固话区号、港澳台与国际区号 |
| 来电提示 | 顶部悬浮条（`TYPE_APPLICATION_OVERLAY`），不可用时降级通知栏 |
| 三档处理 | 仅提示 / 静音放行 / 直接拒接（`CallScreeningService`） |
| 重复来电放行 | 同一号码 5 分钟内再打，正常响铃（防误拦） |
| 我的号码库 | 完整号码全等 + `95*` 前缀匹配，导入导出 |
| 内置黄页 | 116 条客服短号（快递 / 航空 / 银行 / 保险 / 运营商 / 政务 / 紧急），SQLite 存储、内存查询 |
| 联网更新 | 号段库整包替换、号码库合并、黄页合并，均支持自定义 URL；也可从文件导入 |
| 在线兜底查询 | 可配置 URL 模板（`{num}` 占位），结果缓存本地，断网可用，失败一律放行 |
| 三套主题 | 液态玻璃风格：冰川蓝 / 晨雾白 / 暗夜灰 |
| 自我诊断 | 崩溃堆栈落盘 + 界面可分享；每通电话的「判定 / respond / 归属地 / 合计」耗时可在 App 内查看 |

## 快速开始（Windows + Git Bash）

### 1. 准备工具链

需要三样东西，任选安装方式：

| 组件 | 用途 |
|---|---|
| JDK 17 | `javac` / `java` / `keytool` / 运行 `d8` |
| Android SDK Build-Tools 34.0.0 | `aapt2` / `d8` / `zipalign` / `apksigner` |
| Android SDK Platform 34（`android.jar`） | 编译期的 `-bootclasspath` |

本项目不依赖 Gradle，只要能找到上面这些可执行文件即可。`build.sh` 顶部的这几个变量需要按你的路径改：

```bash
ROOT="E:/AI-WORKBUDDY/2026-11-15-17-36-04"      # 工程根目录
ENV="$ROOT/build-env"                            # 工具链目录
BT="$ENV/sdk/build-tools/34.0.0"
PLATFORM="$ENV/sdk/platforms/android-34/android.jar"
JAVA_BIN="$ENV/jdk/jdk-17.0.21+9/bin"
```

### 2. 下载号段库

仓库里**不含** `assets/phone.dat`（4.3 MB，GPL-3.0 第三方数据），构建前跑一次：

```bash
bash tools/fetch-data.sh
```

### 3. 配置签名

签名密钥和口令都**不进仓库**。第一次构建会自动生成密钥，口令从环境变量或 `keystore/pass.txt` 读取：

```bash
# 方式一：环境变量
export SENTRY_KS_PASS='你的口令'

# 方式二：写文件（已在 .gitignore 中）
printf '你的口令' > keystore/pass.txt
```

> 升级安装必须用同一个密钥，**密钥或口令丢了就只能卸载重装**。

### 4. 构建

```bash
bash build.sh
```

成功会打印 `23 个类全部生成`，产物是根目录的 `CallerSentry.apk`。
编译失败会直接退出并打印 javac 错误——**不要**把它改成 `|| true`，之前吃过这个亏（打出了缺类的包，一启动就闪退）。

## 目录结构

```
caller-sentry/
├── AndroidManifest.xml
├── build.sh                  手工构建脚本
├── src/cn/fzj/callersentry/  23 个类
│   ├── App.java              崩溃捕获 + 预热
│   ├── MainActivity.java     首页
│   ├── RulesActivity.java    我的号码库
│   ├── SourcesActivity.java  数据源与更新（二级页面）
│   ├── AppearanceActivity.java  主题
│   ├── RecordsActivity.java  记录 / 耗时 / 诊断
│   ├── ScreeningService.java 来电判定与拦截（核心）
│   ├── CallerIdProvider.java Directory Provider（通话记录可见性的尝试）
│   ├── PhoneLookup.java      号段库（二分）
│   ├── YellowStore.java      黄页（SQLite + 内存）
│   ├── Blacklist.java        号码库规则
│   ├── MarkCache / OnlineLookup / Update / Timing / LogStore
│   ├── Prefs.java            设置（内存快照）
│   └── Ui / UiTheme / Overlay / NumUtil / BasePageActivity
├── res/                      无 layout XML，界面全部代码构建
├── assets/yellow.txt         内置黄页（116 条）
├── test/                     JVM 单元测试（查表 / 解析）
└── tools/fetch-data.sh       下载号段库
```

## 测试

不需要设备，直接跑 JVM 测试（用 `android.jar` 当 classpath）：

```bash
JB="path/to/jdk/bin"; PL="path/to/android-34/android.jar"
"$JB/javac" -encoding UTF-8 -cp "$PL;build/classes" -d build/test test/TestLookup.java
"$JB/java" -cp "build/test;build/classes" TestLookup assets/phone.dat   # 16 个用例
```

## 数据来源与许可

| 数据 | 来源 | 许可 |
|---|---|---|
| 归属地号段库 `phone.dat` | [xluohome/phonedata](https://github.com/xluohome/phonedata) | **GPL-3.0** |
| 骚扰号段（运行时下载，不内置） | [vlongen/BanHarassment](https://github.com/vlongen/BanHarassment) | 见其仓库 |
| 内置黄页 116 条 | 政府公示与运营商/企业公开客服号（陕西/天津邮政管理局、民航局投诉公示等） | 公开信息，手工整理 |

因为内置依赖了 GPL-3.0 的数据，**本项目整体以 GPL-3.0 发布**（见 `LICENSE`）。
如果你希望改用 MIT，需要把 `phone.dat` 从分发物里去掉、改为用户自行导入——代码层面 `PhoneLookup` 已经支持从 `filesDir` 读取。

## 隐私

- 没有申请 `INTERNET` 之外的任何网络相关权限；联网只用于下载你指定的库和可选的兜底查询
- 不上传联系人、通话记录、号码
- 在线兜底查询默认**关闭**，开启后也只会把当前来电号码发给你自己填的地址
- `READ_CALL_LOG` 仅用于 Directory Provider（让拨号器能查名字），不读取你的通话内容

## 已知限制

- **三星原生「电话」不查第三方 Directory**，所以通话记录里通常看不到归属地；AOSP / Google 拨号器可以。这个已经验证过，不是 bug。
- 往 10086、95338 这类号码**拨出去**不会显示名字：`CallScreeningService` 只筛来电，系统不给我们外拨事件。
- 归属地靠号段推断，只精确到市；携号转网后运营商可能不准。
- 快递员、中介的**个人手机号**无法离线识别，只能靠在线兜底或自己标记。
- 三星需把 App 电池设为「不受限制」，否则悬浮条会被杀（App 内有引导按钮）。

## 致谢

这个项目是站在一堆别人铺好的路上做的，没有它们根本起不来。

### 开源项目

| 作者 | 项目 | 用在了哪 |
|---|---|---|
| [@xluohome](https://github.com/xluohome) | [phonedata](https://github.com/xluohome/phonedata) | 核心的归属地号段库，49.7 万条，`PhoneLookup` 的解析格式与二分索引都按它的结构实现 |
| [@vlongen](https://github.com/vlongen) | [BanHarassment](https://github.com/vlongen/BanHarassment) | 国内骚扰号段数据集，作为「号码库」的默认更新源 |
| [@Pixel-Tailor-CN](https://github.com/Pixel-Tailor-CN) | [Pixel Telo](https://github.com/Pixel-Tailor-CN/PixelTelo) | **设计蓝本**：本地库优先 + 联网兜底、三档处理（仅提示/静音/拒接）、重复来电放行、`CallScreeningService` 的角色适配，以及 Directory Provider 的用法，都是从这个项目学来的 |
| [@jhonsu01](https://github.com/jhonsu01) | [OpenCallShield](https://github.com/jhonsu01/OpenCallShield) | 公开号码列表的 JSON 结构（`{number, tag}`）与"规则引擎 + 定期同步"的思路参考 |
| [@xdtianyu](https://github.com/xdtianyu) | [PhoneNumber](https://github.com/xdtianyu/PhoneNumber) | 早期调研时参考了它的号码模型（归属地 / 标记 / 常用号码分层） |

### 数据与公开信息

- 快递客服号：陕西省邮政管理局、天津市政务网的寄递企业客服公示
- 航空公司客服号：民航局服务质量监督投诉公示（12326）、武汉市交通运输局、襄阳机场公告
- 黄页分类整理：公开的"常用电话"页面（sojson、查号吧等）

### 关于呼叫角色

特别感谢 AOSP 与 Google 的 `CallScreeningService` / `ContactsContract.Directory` 文档——虽然三星原生「电话」不查第三方目录（见[已知限制](#已知限制)），但 Android 至少留了这条官方通道。

## 更新日志

- **v2.5** 黄页扩到 116 条（航空 25 / 快递 18 / 国际快递 4），加种子版本号让老用户自动补导
- **v2.4** 黄页转 SQLite（内存热缓存）、黄页热更新、电池保活引导、签名口令移出代码
- **v2.3** 来电延迟优化：无锁 `peek()`、设置内存快照、日志异步、悬浮条先显示后补字、内置耗时统计
- **v2.2** Directory Provider（通话记录可见性尝试）
- **v2.1** 顺序改为"本地判定 → 立刻 respond → 再算归属地"
- **v2.0** 液态玻璃 UI、三套主题、新图标、数据源收进二级页面
- **v1.5** 内置黄页 79 条
- **v1.0** 首个可用版本
