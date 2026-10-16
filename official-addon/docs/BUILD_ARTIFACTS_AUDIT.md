# 历史编译产物分级清理审计

生成时间：2026-10-16 · 仓库 HEAD 0510d1c（已合并上游 rayneo-ios-1.0.6-218 发布提交）

## 判定规则（机械、可复核）

1. 保留：被仓库内任何 `.md` / `.json` 记录引用的 `build/...` 或 `final-build/...` 条目名（用 `grep -rIoE 'build/[A-Za-z0-9_.-]+' --include='*.md' --include='*.json'` 提取，排除构建目录自身，取 `build/` 后第一段作为条目名）。共 **%d** 个保留条目名。
2. 无条件保留：`final-build/README.md`、`CURRENT-STATUS.json`、`build_modules.py`、`test_recipe.py`、`EndpointPolicyChecks.m`、`current-source-block.json`、`source-gate-after-binary-leak.json`、`final-attempt*.log`、`build-attempt*.log`，以及 `final-build/build/final-task73-20261015-0856`、`frozen-recipe-task73`、`policy-checks`。
3. 删除：不在保留集合内的构建输出条目（被拒的构建尝试、旧的整包解包副本、下载缓存、测试临时目录、嵌套重复冻结副本）。
4. 只列不删：Git 已跟踪的疑似被取代生成物（见最后一节）。未删除任何 Git 跟踪文件。

## 清理前状态

```
official-addon/build        8.85 GB
official-addon/final-build  0.88 GB
build/ 顶层条目 187 个；final-build/build/ 6 个运行目录

```

## 删除清单（135 项，合计 4.81 GB；其中 ≥1MB 的 18 项占 4.80 GB，其余 117 项为测试临时输出与小日志）

| 大小 (MB) | 路径 | 判定依据 |
| --- | --- | --- |
| 860.44 | `build/signature-debug-failed2` | 失败的签名调试副本 |
| 757.71 | `build/amap-download-7iYZs7` | 高德 SDK 下载缓存（正式使用的 build/amap-sdk 已保留） |
| 406.27 | `build/newshub-clean-20261014-135831` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 406.27 | `build/newshub-full-20261014-132303` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 406.21 | `build/official-import-trial-20261014-121133` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 406.21 | `build/official-import-trial-20261014-122518` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 406.20 | `build/aihot-daily-stage-20261013-110220` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 406.20 | `build/aihot-daily-stage-20261013-111120` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 386.32 | `build/amap-download-hTJ6kc` | 高德 SDK 下载缓存（正式使用的 build/amap-sdk 已保留） |
| 154.70 | `build/mapkit-amap-http` | 未被任何 .md/.json 引用 |
| 154.58 | `build/r7-stopfix-final` | 未被任何 .md/.json 引用 |
| 154.53 | `build/subtitle-r3` | 未被任何 .md/.json 引用 |
| 2.88 | `build/navigation-r5-home.png` | 未被任何 .md/.json 引用 |
| 2.84 | `build/news-ui-validation-20261014-1436` | 未被任何 .md/.json 引用 |
| 2.06 | `final-build/build/candidate-task73-20261015-0843` | 被拒的早期构建尝试；final-build/README.md 明确 0856 为从新源码完整重建、未复用被拒产物 |
| 2.05 | `official-addon/final-build/build/final-task73-20261015-0854` | 被拒的早期构建尝试；final-build/README.md 明确 0856 为从新源码完整重建、未复用被拒产物 |
| 1.36 | `build/mapkit-hud` | 未被任何 .md/.json 引用 |
| 1.26 | `build/mapkit-http` | 未被任何 .md/.json 引用 |
| 0.96 | `build/newshub-independent-20261014-143948` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.93 | `final-build/build/candidate-task73-20261015-0841` | 被拒的早期构建尝试；final-build/README.md 明确 0856 为从新源码完整重建、未复用被拒产物 |
| 0.37 | `build/mapkit-tests` | 未被任何 .md/.json 引用 |
| 0.32 | `build/newshub-clean-20261014-135831-link` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.32 | `build/newshub-full-20261014-132303-link` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.23 | `build/navigation-r5-six-tabs.png` | 未被任何 .md/.json 引用 |
| 0.19 | `build/aihot-daily-stage-20261013-111120-link` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.19 | `build/aihot-daily-stage-20261013-110220-link` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.16 | `build/news-hub-unit-tests` | 未被任何 .md/.json 引用 |
| 0.14 | `build/official-import-trial-20261014-121133-link` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.14 | `build/official-import-trial-20261014-122518-link` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.14 | `build/local-http-flow-tests-1` | 未被任何 .md/.json 引用 |
| 0.14 | `build/knowledge-tool-tests` | 未被任何 .md/.json 引用 |
| 0.14 | `build/todo-tool-flow-tests` | 未被任何 .md/.json 引用 |
| 0.13 | `build/aihot-baseline-news-teleprompter-tests` | 未被任何 .md/.json 引用 |
| 0.13 | `build/local-http-flow-tests-0` | 未被任何 .md/.json 引用 |
| 0.13 | `build/news-teleprompter-tests` | 未被任何 .md/.json 引用 |
| 0.13 | `build/web-search-flow-tests` | 未被任何 .md/.json 引用 |
| 0.13 | `build/local-http-flow-tests` | 未被任何 .md/.json 引用 |
| 0.12 | `build/aihot-stage-final-review-tests` | 未被任何 .md/.json 引用 |
| 0.12 | `build/aihot-stage-core-review-sharefix-tests` | 未被任何 .md/.json 引用 |
| 0.12 | `build/navigation-subtitle-live-tests` | 未被任何 .md/.json 引用 |
| 0.12 | `build/navigation-subtitle-tests` | 未被任何 .md/.json 引用 |
| 0.11 | `build/aihot-stage-core-review-tests` | 未被任何 .md/.json 引用 |
| 0.11 | `build/protocol-restart-tests` | 未被任何 .md/.json 引用 |
| 0.11 | `build/subtitle-limit-probe-20261011` | 未被任何 .md/.json 引用 |
| 0.11 | `build/subtitle-startup-integration-tests` | 未被任何 .md/.json 引用 |
| 0.10 | `build/official-import-host-test-run` | 未被任何 .md/.json 引用 |
| 0.09 | `build/official-import-host-lifecycle-review-tests` | 未被任何 .md/.json 引用 |
| 0.09 | `build/web-search-tests` | 未被任何 .md/.json 引用 |
| 0.09 | `build/navigation-subtitle-entry-tests` | 未被任何 .md/.json 引用 |
| 0.09 | `build/official-import-trial-host-review-tests` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.08 | `build/navigation-begin-route-tests` | 未被任何 .md/.json 引用 |
| 0.07 | `build/core-tests` | 未被任何 .md/.json 引用 |
| 0.07 | `build/navigation-map-location-tests` | 未被任何 .md/.json 引用 |
| 0.07 | `build/local-http-endpoint-tests-1` | 未被任何 .md/.json 引用 |
| 0.07 | `build/official-import-core-lifecycle-review-tests` | 未被任何 .md/.json 引用 |
| 0.07 | `build/official-import-core-test-run` | 未被任何 .md/.json 引用 |
| 0.06 | `build/local-http-endpoint-tests-0` | 未被任何 .md/.json 引用 |
| 0.06 | `build/navigation-tele-tests` | 未被任何 .md/.json 引用 |
| 0.06 | `build/private-bootstrap-tests` | 未被任何 .md/.json 引用 |
| 0.06 | `build/subtitle-native-send-tests` | 未被任何 .md/.json 引用 |
| 0.05 | `build/local-http-endpoint-tests` | 未被任何 .md/.json 引用 |
| 0.05 | `build/news-caption-tests` | 未被任何 .md/.json 引用 |
| 0.05 | `build/official-import-trial-core-review-tests` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.05 | `build/todo-protocol-tests` | 未被任何 .md/.json 引用 |
| 0.05 | `build/navigation-core-tests` | 未被任何 .md/.json 引用 |
| 0.05 | `build/navigation-places-tests` | 未被任何 .md/.json 引用 |
| 0.05 | `build/subtitle-lifecycle-tests` | 未被任何 .md/.json 引用 |
| 0.05 | `build/aihot-official-probe-review-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/navigation-auto-plan-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/subtitle-hud-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/home-tab-layout-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/subtitle-restart-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/todo-bridge-client-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/alwayson-audio-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/home-tabs-six-tests` | 未被任何 .md/.json 引用 |
| 0.04 | `build/navigation-background-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/home-tabs-dark-build.log` | 未被任何 .md/.json 引用 |
| 0.03 | `build/navigation-auto-display-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/navigation-auto-restart-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/a2ui-protocol-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/catalog-mapkit-0` | 未被任何 .md/.json 引用 |
| 0.03 | `build/catalog-mapkit-1` | 未被任何 .md/.json 引用 |
| 0.03 | `build/navigation-map-entry-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/navigation-modes-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/navigation-route-session-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/recording-export-tests` | 未被任何 .md/.json 引用 |
| 0.03 | `build/research-catalog-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/aihot-baseline-news-core-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/alwayson-ogg-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/news-core-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/recording-text-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/aihot-baseline-news-presentation-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/host-compatibility-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-stop-presentation-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/news-presentation-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/profile-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.4X89A4` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.54YmFc` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.9cQdmH` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.Bz1tZV` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.GM0L39` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.JqGLTf` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.MQ3Sum` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.QDTnPE` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.QmnuPd` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.SKXnMu` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.TUWy4p` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.VrY1gr` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.XJO0yG` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.ial9j8` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.s9QpCV` | 未被任何 .md/.json 引用 |
| 0.02 | `build/protocol-restart-test.xBC9gC` | 未被任何 .md/.json 引用 |
| 0.02 | `build/r7-stop-action-preview.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/voice-tts-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/http-any-ip-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-r5-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-r5-preview-ui.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-r5-preview.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-r6-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-r7-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-r7-preview.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/navigation-session-tests` | 未被任何 .md/.json 引用 |
| 0.02 | `build/r7-stop-action-sdk-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/r7-stopfix-package-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/route-r4-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/subtitle-r3-final-build.log` | 未被任何 .md/.json 引用 |
| 0.02 | `build/subtitle-r3-preview.log` | 未被任何 .md/.json 引用 |
| 0.01 | `build/home-tabs-dark-tests.log` | 未被任何 .md/.json 引用 |
| 0.01 | `build/http-any-ip-tests.log` | 未被任何 .md/.json 引用 |
| 0.00 | `build/r7-regression-diagnosis-tests.log` | 未被任何 .md/.json 引用 |
| 0.00 | `build/r7-stopfix-package-tests.log` | 未被任何 .md/.json 引用 |
| 0.00 | `build/realtime-subtitle-targeted-tests.log` | 未被任何 .md/.json 引用 |
| 0.00 | `build/subtitle-r3-targeted-tests.log` | 未被任何 .md/.json 引用 |
| 0.00 | `build/newshub-clean-20261014-135719-link` | 早期整包解包/打包副本；被引用的同族条目已保留 |
| 0.00 | `build/signature-debug-failed1` | 失败的签名调试副本 |

## 保留清单（被引用条目中的大项；完整条目名集合见方法 1）

| 大小 (MB) | 路径 | 保留原因 |
| --- | --- | --- |
| 498.43 | `build/amap-sdk` | 被仓库内文档作为证据引用 |
| 421.42 | `build/official-import-trial-20261014-101800-r6` | 被仓库内文档作为证据引用 |
| 410.01 | `build/newshub-final-20261014-144020` | 被仓库内文档作为证据引用 |
| 406.23 | `build/official-import-trial-20261014-094800` | 被仓库内文档作为证据引用 |
| 406.21 | `build/aihot-daily-stage-20261013-111620` | 被仓库内文档作为证据引用 |
| 336.84 | `build/manuscript-lab-input-20261015-original105` | 被仓库内文档作为证据引用 |
| 154.79 | `build/mapkit-amap-http-hud` | 被仓库内文档作为证据引用 |
| 154.68 | `build/mapkit-amap` | 被仓库内文档作为证据引用 |
| 154.58 | `build/local-http-navigation` | 被仓库内文档作为证据引用 |
| 154.58 | `build/navigation-r7-final` | 被仓库内文档作为证据引用 |
| 154.55 | `build/navigation-r6-final` | 被仓库内文档作为证据引用 |
| 154.55 | `build/navigation-r5-final` | 被仓库内文档作为证据引用 |
| 154.55 | `build/route-r4-final` | 被仓库内文档作为证据引用 |
| 154.53 | `build/subtitle-r3-final` | 被仓库内文档作为证据引用 |
| 154.51 | `build/realtime-subtitle` | 被仓库内文档作为证据引用 |
| 154.49 | `build/navigation` | 被仓库内文档作为证据引用 |
| 102.23 | `build/rea-teleprompter-import` | 被仓库内文档作为证据引用 |
| 7.50 | `final-build/build` | 被仓库内文档作为证据引用 |
| 2.67 | `build/rea-tools` | 被仓库内文档作为证据引用 |
| 1.25 | `build/mapkit` | 被仓库内文档作为证据引用 |
| 1.13 | `build/research-preview` | 被仓库内文档作为证据引用 |
| 1.06 | `build/local-http` | 被仓库内文档作为证据引用 |
| 1.05 | `build/embedded` | 被仓库内文档作为证据引用 |
| 0.55 | `build/news-headless-ui-validation-20261014-1533` | 被仓库内文档作为证据引用 |
| 0.23 | `build/navigation-r5-final-six-tabs.png` | 被仓库内文档作为证据引用 |

## 未决（本次未删，留给使用者判断）

| 大小 | 路径 | 为什么没删 |
| --- | --- | --- |
| 850 MB | `final-build/runtime-capture-package/` | 被 2 份文档引用（含其内部 `build_capture.py` 的冻结快照）；是本次最大单项，删除前需确认这些引用是否还需要可复现的构建输入 |
| 337 MB | `build/manuscript-lab-input-20261015-original105/` | 是 1.0.5(201) 宿主输入，被 `docs/NEWSHUB_FINAL_IPA_BUILD_INPUTS.md`、`docs/NEWSHUB_DIRECT_DB_PLAN.md` 与 final-build 的冻结 builder 引用；交付基线已切到 1.0.6(218)，此项由「在用输入」变为「历史输入」，是否保留应由使用者决定 |
| 498 MB | `build/amap-sdk/` | 被 `docs/HTTP_NAVIGATION.md`、`docs/NAVIGATION.md` 多处引用为构建输入 |
| 155 MB ×8 | `build/navigation-r5-final`、`navigation-r6-final`、`navigation-r7-final`、`route-r4-final`、`subtitle-r3-final`、`realtime-subtitle`、`mapkit-amap`、`mapkit-amap-http-hud`、`local-http-navigation`、`navigation` | 各自 dylib 与日志被对应 R5/R6/R7/R4/字幕/MapKit 文档引用为证据；删除会使这些文档的哈希证据不可复现 |

## Git 已跟踪、疑似被取代的生成物（只列清单，未删除）

| 大小 | 路径 | 引用情况 | 建议 |
| --- | --- | --- | --- |
| 620 KB | `docs/v2-implementation.html` | 仅被自身的 `v2-implementation.visual-check.html/json` 引用；README 与其它文档未引用 | 可删（需你确认） |
| 128+124+96+96 KB | `docs/v2-implementation.visual-check.*.png` | 仅被 `docs/v2-implementation.visual-check.json` 引用，属于一次视觉校验的输出 | 可删（需你确认） |
| 8 KB / 4 KB / 4 KB | `docs/v2-implementation.architecture.json`、`docs/v2-implementation.visual-check.{html,json}` | 与上面同族；`architecture.json` 是该可视化的源 | 建议保留源、删渲染输出（需你确认） |
| 476 KB / 12 KB | `docs/memes/rayneo-io-5080-backpack-meme.{png,svg}` | `git grep` 在跟踪的 `.md/.json/.html` 中未发现任何引用 | 可删或改放 Release 资产（需你确认） |
| 2 处 | `firmware-research/strix-1.0.4.12/native-navigation/{,focus/src/}official-addon/research/animation-runtime-v1/assets/encoded-v1/anime-idle-192x176-l8.bin` | 同一份 L8 构建输入在两个快照目录中重复存在；`scripts/check-source.mjs` 把 ANIM60 帧与其精确构建输入登记为已审阅资产 | 建议保留（被源检查脚本登记），仅提示重复 |


## 清理后状态（实际执行结果）

```
删除条目：135 项（134 项 build/ 与 final-build/build/ 条目 + final-build/build/final-task73-20261015-0854）
回收：5,045,280 KB ≈ 4.81 GB
清理前  official-addon/build 9,275,444 KB (8.84 GB)   official-addon/final-build 922,772 KB (0.88 GB)
清理後  official-addon/build 4,235,320 KB (4.04 GB)   official-addon/final-build 917,616 KB (0.87 GB)
git ls-files official-addon/build official-addon/final-build -> 0（这两个目录全部为 .gitignore 排除的本机产物，未删除任何 Git 跟踪文件）
```
