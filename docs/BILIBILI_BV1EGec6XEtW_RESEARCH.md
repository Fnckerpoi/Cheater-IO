# B站 BV1EGec6XEtW：作者、项目归属、源码及安装包渠道调查

- 调查日期：2026-10-04（UTC；本轮环境时间）。所有动态信息均是此时的观察，不代表视频发布时的状态。
- 对象：[视频 BV1EGec6XEtW](https://www.bilibili.com/video/BV1EGec6XEtW/)。
- 方法：公开视频/作者/评论 API 与 GitHub API、仓库 README、LICENSE、Release 正文及附件**元数据**；未下载、执行、安装任何安装包或固件，未修改项目代码。
- 存放惯例：仓库已有 `docs/WEREAD_RESEARCH.md`、`docs/IOS_IPA_FRAMEWORK_RESEARCH.md`，因此沿用 `docs/*_RESEARCH.md`，没有另建 `notes/research/`。

## 0. 主代理浏览器补充：优先于下文匿名 API 的覆盖不足结论

后台研究完成后，主代理在本轮已通过正常登录的浏览器读取视频评论，找到匿名 API 未返回的关键回复。因此下文涉及“仅有同领域线索”“无法确认二开基础”的旧判断已由本节补充证据纠正；匿名 API 的覆盖限制仍然成立。

来源：[视频评论区](https://www.bilibili.com/video/BV1EGec6XEtW/)（[S11]）。页面显示时间为 UTC+8。作者回复如下：

- 2026-09-16 12:46，回答“请问方便分享一下项目仓库吗”：**“我的这个没公开。但b站up的那个在视频中写了的。其实我这个不复杂，用codex直接做就行”**。主代理额外检查该回复头像链接的 HTML，确认 `href="//space.bilibili.com/3537114593495061"` 与 `data-user-profile-id="3537114593495061"`，与视频作者 UID 一致。
- 2026-09-16 20:51，回答“那套开源工具在哪里可以找到呀，感谢”：**“https://github.com/Turbo1123/Turbo-IO 这个是B站大佬开发的。我也是在这个基础上做二次开发。”**
- 2026-09-16 20:53，回答求分享翻译软件：**“主要是还不够完善，还要再完善一下。另外是在别人的基础上做的二开。如果真要分享，还要征求别人的意见。”**
- 2026-09-17 08:16，作者表示计划在自己的小红书小号介绍开发过程，准备好再通知；该回复本身没有给出已发布的教程或安装包入口。

**修正后的核心结论：该 UP 明确表示视频中的二开版本当时未公开；其本人指出的二开基础是 Turbo1123/Turbo-IO，而不是偶然发现的同领域候选。未找到该 UP 自己二开版本的后续公开仓库或安装包，不能据此断言其永远不会发布，也不能把 Turbo1123 认作该 UP 的 GitHub 账户。原项目确有公开源码和 Android APK，见第 4.3 节。**

## 1. 结论先行

| 问题 | 本轮可支持的结论 | 证据强度 / 边界 |
| --- | --- | --- |
| 视频作者是谁？ | **硬糖玛奇朵加盐travel**，B站 UID **3537114593495061**。 | **已确认**：视频 `owner` 与作者 card API 的 `mid/name` 一致。[S1][S2] |
| 视频展示什么？ | 雷鸟 **iO / io 智能眼镜**的个人体验；作者自述使用 **Codex / Vibe Coding**补充翻译、导航功能。 | **确认作者这样描述**；未独立验证实现效果，也不能由自述推断源码公开。[S1] |
| 作者软件的正式项目名是什么？ | **尚未确认**。可读标题、完整简介、作者签名、可读评论没有给出该软件的正式项目名。 | “雷鸟 iO”是这里的硬件名称；“Codex”是作者所称开发工具。**不能把任一名称当作软件项目名，更不能直接命名为 Turbo IO。** [S1][S2][S3] |
| 作者本人 GitHub 是否开源？ | **作者本人明确表示其二开版本当时未公开；个人 GitHub 账户及后续公开状态尚未确认。** | 浏览器核实作者 UID 和回复；不能扩大为“作者没有 GitHub/所有项目均未开源”。[S11] |
| 作者项目安装包在哪里发布？ | **尚未找到可以归属于该作者、该视频项目的安装包发布渠道。** | 简介无下载入口，可读作者评论未给出软件安装入口；置顶短链按上下文疑似购物推荐，不是已确认的软件分发渠道。评论/动态访问不完整，不能据此证明作者从未发布。[S1][S3][S4] |
| Turbo1123/Turbo-IO 呢？ | **作者本人明确指出，这是其二开基础，由另一位 B站开发者开发。** 它确有公开源码、非商业许可证与 Release 附件。 | 二开关系有作者直接说明，不等于两位作者是同一人，也不等于 UP 的修改已包含在原项目中。[S11][S6]–[S10] |

**最稳妥的回答：UP 的二开版本当时未公开；UP 明确指向的底层项目是 Turbo-IO，该项目已有源码及 Android APK。UP 的个人 GitHub、后续二开发布渠道及用户记忆中的具体安装包来源仍未确认。**

## 2. 视频和作者的一手证据

### 2.1 视频元数据

[S1] B站公开 view API：

<https://api.bilibili.com/x/web-interface/view?bvid=BV1EGec6XEtW>

本轮返回 `code=0`、`message="OK"`，关键字段为：

| 字段 | 返回值 |
| --- | --- |
| `bvid` | `BV1EGec6XEtW` |
| `aid` | `117278628648974` |
| `title` | 雷鸟iO自费体验：官方没做好的，我用AI先做了｜翻译和导航我用Codex给它补上了 |
| `owner.mid` | `3537114593495061` |
| `owner.name` | 硬糖玛奇朵加盐travel |
| `pubdate` | `1789529914`：2026-09-16 03:38:34 UTC / 11:38:34 UTC+8 |
| 第一分P `cid` / `duration` | `41934455781` / `823` 秒（13分43秒） |

简介中的直接自述：

> “虽然我是个旅行博主，但其实我更是一个数码的狂热爱好者，也做了10年的硬件数码编辑。”
>
> “有了AI，有了Vibe Coding之后。我自己能做的事情更多了。我用codex给它补上了更好用的翻译和还没有推出的导航。”
>
> “注：雷鸟现在应该是在软件的快速迭代期。我视频基本做完的时候，已经推出了1.04版。”

`desc` 和 `desc_v2` 的完整返回文本没有 GitHub URL、仓库名或安装包 URL。[S1] 最后一段的“1.04”在原文中没有进一步区分手机 App 与眼镜固件，**不把它认定为作者软件版本**。

### 2.2 作者资料

[S2] 作者主页：<https://space.bilibili.com/3537114593495061>；成功读取的官方 card API：

<https://api.bilibili.com/x/web-interface/card?mid=3537114593495061>

该 API 返回 `code=0`，`data.card.mid="3537114593495061"`、`name="硬糖玛奇朵加盐travel"`。签名前半段为：

> “对世界好奇两个人，看世界各种活法，去历史现场，这就是旅行的意义。”

后半段为商务联系说明；本轮可读签名和 `description` 没有 GitHub 账户、软件项目名或下载地址。[S2] 商务联系方式**不是自动成立的 GitHub 身份证明**；未联系作者。

## 3. 评论与作者发布渠道：读到了什么，没读到什么

### 3.1 评论读取范围

[S3] 成功的公开评论 API：

<https://api.bilibili.com/x/v2/reply?type=1&oid=117278628648974&sort=2&pn=1&ps=20>

本轮该响应显示 `page.count=135`、`acount=135`，但仅返回 **3 个主楼**，另有 UP 置顶评论；**不能说已遍历135条评论**。对这3个主楼，又读取了楼中楼：

- <https://api.bilibili.com/x/v2/reply/reply?type=1&oid=117278628648974&root=317618731952&pn=1&ps=20>：5条回复。
- <https://api.bilibili.com/x/v2/reply/reply?type=1&oid=117278628648974&root=319293780752&pn=1&ps=20>：4条回复。
- <https://api.bilibili.com/x/v2/reply/reply?type=1&oid=117278628648974&root=314253607841&pn=1&ps=20>：2条回复。

按 `mid=3537114593495061` 区分作者和观众，而不是只凭昵称。可读作者回复包括：

- `rpid=314253664529`：“目前混到官方群里，反正我感觉开发人员还是挺努力。”
- `rpid=315729504049`：“它省电的核心原因之一就是简单，都靠本地，电量也就不行了。看取舍吧”。

这些话都**没有**建立 GitHub 身份或软件发布渠道；“官方群”也没有提供群号/软件链接，不能当作作者安装包已在群内发布的证明。[S3]

后续尝试 `sort=2` 的第2–7页及 `sort=0` 的第1–7页，响应虽 `code=0`，但 `page` 为全零且 `replies` 为空。这与首屏可读内容及计数不一致，属于**覆盖不足/响应异常的观察**；未确定其成因，不把空响应解释成完整否定证据。

### 3.2 置顶短链不是已确认的软件下载入口

[S3] `data.upper.top` 的作者 UID 与视频 `owner.mid` 相同，`rpid=317870690512`。置顶文本为：

> “虽然有些突然，但现在有一件事情是现在可以确认的---那就是你一定会喜欢上右边这个东西 https://b23.tv/mall-GCOsvf3Ib-2qijA”

作者确实发布了该短链：<https://b23.tv/mall-GCOsvf3Ib-2qijA>。**按文本与 `mall-` 命名，这更像购物推荐线索；本轮没有读取最终落地页，不声称已确认商品或具体跳转。** 它不是已有证据支持的软件安装包入口。

### 3.3 未能读取的来源

[S4] 下列访问存在明确限制：

- 视频网页返回“验证码_哔哩哔哩”，只得到极少页面文字；网页读取器不会运行 JavaScript。
- <https://api.bilibili.com/x/space/acc/info?mid=3537114593495061> 返回 `code=-799`、“请求过于频繁，请稍后再试”；因此改用成功的 card API，而不是推断主页为空。
- <https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid=3537114593495061> 返回 HTTP **412**；**未覆盖作者动态**。
- <https://api.bilibili.com/x/player/wbi/v2?bvid=BV1EGec6XEtW&cid=41934455781> 返回 `code=0`，但 `subtitle.subtitles=[]`。只能说本轮匿名请求未取得字幕，不能说视频绝无字幕。
- 一般网页搜索工具的 Firecrawl 路由报 HTTP **429 / 免费额度耗尽**，没有取得可用搜索结果。

**没有完整观看或转录视频画面/音轨。** 因而正式项目名、二维码、视频内下载提示若只出现于画面或口播，可能不在本轮已读证据中。

## 4. GitHub：相关项目可以确认，作者归属不能跳步

### 4.1 身份检索结果

[S5] 使用 GitHub 公共搜索 API（未登录、不使用个人令牌）：

| 检索范围 | 查询与可复查链接 | 本轮结果 |
| --- | --- | --- |
| 仓库 README 中的该视频 | [`"BV1EGec6XEtW" in:readme`](https://api.github.com/search/repositories?q=%22BV1EGec6XEtW%22+in%3Areadme&per_page=15) | `total_count=0` |
| 仓库 README 中的作者 UID | [`"3537114593495061" in:readme`](https://api.github.com/search/repositories?q=%223537114593495061%22+in%3Areadme&per_page=15) | `total_count=0` |
| 仓库 README 中的昵称片段 | [`"硬糖玛奇朵" in:readme`](https://api.github.com/search/repositories?q=%22%E7%A1%AC%E7%B3%96%E7%8E%9B%E5%A5%87%E6%9C%B5%22+in%3Areadme&per_page=15) | `total_count=0` |
| 用户搜索 | [`硬糖玛奇朵`](https://api.github.com/search/users?q=%E7%A1%AC%E7%B3%96%E7%8E%9B%E5%A5%87%E6%9C%B5&per_page=20) | `total_count=0` |
| README 中的作者公开商务联系数字部分 | [`"1119056915" in:readme`](https://api.github.com/search/repositories?q=%221119056915%22+in%3Areadme&per_page=15) | `total_count=0` |

这些是检索结果，**不是全 GitHub 内容审计**，更不是“不存在仓库”的证明：账号可能用完全不同名字，仓库可能没有被索引、没有写上述字符串，或仍为私有。

另以 [`雷鸟 io`](https://api.github.com/search/repositories?q=%E9%9B%B7%E9%B8%9F+io&per_page=20)、[`rayneo io`](https://api.github.com/search/repositories?q=rayneo+io&per_page=20) 和 [`"雷鸟" "翻译" "导航" in:readme`](https://api.github.com/search/repositories?q=%22%E9%9B%B7%E9%B8%9F%22+%22%E7%BF%BB%E8%AF%91%22+%22%E5%AF%BC%E8%88%AA%22+in%3Areadme&per_page=15) 搜索发现多个同领域项目。其中 **Turbo1123/Turbo-IO**也就是当前仓库的公开远端项目线索，以下仅对它作来源核验，**不把搜索结果匹配等同于作者身份匹配**。

### 4.2 Turbo IO 的可核验事实（不能移植为该 UP 的结论）

[S6] GitHub 账户：<https://github.com/Turbo1123>；API：<https://api.github.com/users/Turbo1123>。

本轮 `login/name` 都为 `Turbo1123`，`bio/email` 为 `null`、`blog` 为空；没有显示上述 B站 UID/昵称或其主页链接。读取的仓库 README 亦未发现该视频 BV、作者 UID/昵称或 B站主页身份链。**这不足以证明两者不是同一个人，只能说本轮没有证明是同一个人。**

[S7] 仓库：<https://github.com/Turbo1123/Turbo-IO>；API：<https://api.github.com/repos/Turbo1123/Turbo-IO>；[根目录 API](https://api.github.com/repos/Turbo1123/Turbo-IO/contents/)。

本轮远端 `main` 指向提交：

<https://github.com/Turbo1123/Turbo-IO/commit/c8815c0b2ff9db3bcdaeeb75abb3ca0e42251efb>

以下 README / LICENSE 链接固定于该提交，避免仅依赖可变的 `main`：

- [README](https://github.com/Turbo1123/Turbo-IO/blob/c8815c0b2ff9db3bcdaeeb75abb3ca0e42251efb/README.md)：标题为 **“Turbo IO · 雷鸟 iO / RayNeo iO 非官方 SDK”**，公开客户端/SDK/扩展及研究范围。
- [LICENSE](https://github.com/Turbo1123/Turbo-IO/blob/c8815c0b2ff9db3bcdaeeb75abb3ca0e42251efb/LICENSE)：**PolyForm Noncommercial License 1.0.0**；`Noncommercial Purposes` 等条款限制许可目的。可称“公开源码、非商业许可”，**不能简写成无限制可商用的开源授权**。[S8]

[S8] LICENSE API：<https://api.github.com/repos/Turbo1123/Turbo-IO/contents/LICENSE>；本轮 blob SHA `1a71cb64397535482ff19542d1093c0d4a6442d0`。README API：<https://api.github.com/repos/Turbo1123/Turbo-IO/readme>；本轮 blob SHA `033f34c499d4f9be54ad4cfbf90a49dafe3674f3`。

### 4.3 该相关仓库的安装包/附件渠道

[S9] [Releases 总页](https://github.com/Turbo1123/Turbo-IO/releases)与 [Releases API](https://api.github.com/repos/Turbo1123/Turbo-IO/releases?per_page=10)支持以下事实：

| 发布入口 | 可读正文/附件元数据确认的内容 | 边界 |
| --- | --- | --- |
| [Android 1.0.5 · GUARD-07 + TAP1-TEST-01](https://github.com/Turbo1123/Turbo-IO/releases/tag/android-105-guard07-tap1-test01) | 2026-09-27 发布；附件有 `TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk`、`TurboIO-TAP1-TEST-01.zip`、`SHA256SUMS.txt`。 | 正文称 APK 为基于官方1.0.5（201）的**非官方完整修改版**，不是原厂原样 APK；配套固件为高风险实验版。不等于已确认视频作者的安装包。 |
| [雷鸟 AI iOS 1.0.5（201）原版解密 IPA](https://github.com/Turbo1123/Turbo-IO/releases/tag/rayneo-ios-1.0.5-201) | 2026-09-20 发布；[该 Release API](https://api.github.com/repos/Turbo1123/Turbo-IO/releases/tags/rayneo-ios-1.0.5-201)列有 `RayNeo_AI_1.0.5_201_decrypted_original.ipa`。 | 正文明言版权归雷鸟及相关权利人、**不含 Turbo IO 扩展**、不是官方源码；仓库源码许可不适用于 IPA。这里只核对发布者陈述及附件存在，**未核验二进制性质、来源授权或内容**，未下载/提供解密操作。 |
| [TLC1 全天智记共存固件](https://github.com/Turbo1123/Turbo-IO/releases/tag/firmware-strix-1.0.4.12-tlc1)等固件 Release | 有 OTA ZIP、校验和与审计附件元数据。 | 固件 ZIP不是手机安装包；正文有高风险、版本锁定、旧 APK 不兼容等限制。 |

[S10] 固定提交 README 中明确：iOS Turbo IO 扩展需要自行合并/配置/签名，**不提供合并后的 IPA / HAP**；这与单独发布的原厂解密 IPA不是同一个交付对象。

**重要来源差异：** GitHub 仓库 API 的 `description` 仍包含“只提供源码，不提供 IPA/APK/HAP 或签名包”，但当前 README 明确列出发行例外，且上述 Release 的附件元数据确实有 APK 与原厂解密 IPA。应按**具体对象、README及实际 Release**分别判断，不引用旧摘要概括所有发布形态。[S7][S9][S10] 此差异不修改原仓库说明。浏览器补充已确认二开基础关系，但并未将原项目归属该 UP。

## 5. 不确定性与下一步需要的证据

1. **正式项目名未确认。** 需要视频画面/口播中的应用名，或作者本人公开评论/动态中的明确命名；本轮只读元数据不能补出名称。
2. **作者 GitHub 身份未确认。** 优先取得 UID `3537114593495061` 的作者资料、作者评论或动态直接链接 GitHub；再核对目标仓库反向链接该作者 UID/主页或视频。只凭同名、同功能、Codex、雷鸟 iO、相似 UI，不足以认定归属。
3. **开源与包发布要分别核对。** 在身份链建立后，再检查该仓库实际源码目录、README范围、LICENSE条款和 Release附件；公开视频、公开安装包、公开源码、可商用许可是四个不同命题。
4. **评论和动态未完整覆盖。** 可在正常授权浏览器中人工看作者主页及视频评论，或请作者给出正式仓库与下载入口；不要把本轮限流、空分页或无匿名字幕当作“从未发布”。
5. **二开基础关系已确认，个人账户归属仍未确认。** 作者本人给出 Turbo IO 链接并称基于它二开；原项目的源码/Release 不等于该 UP 自己的二开版本已发布。

## 6. 复查示例（只取 JSON 元数据）

无需个人令牌的 B站 view API 示例；仅输出所需字段，不获取媒体或安装包：

```sh
curl -fsS --max-time 25 \
  -A 'Mozilla/5.0' -e 'https://www.bilibili.com/' \
  'https://api.bilibili.com/x/web-interface/view?bvid=BV1EGec6XEtW' \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); x=d.get("data") or {}; print(json.dumps({"code":d.get("code"),"message":d.get("message"),**{k:x.get(k) for k in ("bvid","aid","title","pubdate","owner","desc")}},ensure_ascii=False,indent=2))'
```

GitHub Release 核查也只取 JSON，**不会请求 `browser_download_url` 所指的文件**：

```sh
curl -fsS --max-time 25 -A 'Research-readonly' \
  'https://api.github.com/repos/Turbo1123/Turbo-IO/releases/tags/android-105-guard07-tap1-test01' \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps({k:d.get(k) for k in ("html_url","tag_name","published_at")},ensure_ascii=False)); print(json.dumps([{"name":a["name"],"size":a["size"]} for a in d.get("assets",[])],ensure_ascii=False,indent=2))'
```

API及可变页面未来可能改名、删除、限流或返回不同结果；以明确的调查时点、字段和上述来源链接界定本笔记的结论范围。
