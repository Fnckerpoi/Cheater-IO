# Cheater-IO（原 Turbo-IO）· 我的 RayNeo / 雷鸟 iO 开发项目

这是我基于原作者 Turbo-IO 项目持续整理和开发的个人项目仓库，现已更名为 **Cheater-IO**。我把 Android、iOS V1、iOS V2 的个人开发工作放在独立分支中，便于各路线分别迭代。

> **原作者与上游来源**：本项目基于 [Turbo1123/Turbo-IO](https://github.com/Turbo1123/Turbo-IO)。上游 `main` 是原作者维护的完整项目主线；本仓库的 `main` 跟踪 `upstream/main`，用于接收上游更新。原项目及第三方内容的权利与许可仍以各自声明为准；本仓库个人改动遵循仓库许可证。

## 开发路线

| 分支 | 路线 | 说明 |
| --- | --- | --- |
| [`main`](https://github.com/Fnckerpoi/Cheater-IO/tree/main) | 上游同步 | 跟踪原作者 `upstream/main`，保留上游项目内容；不在这里进行个人路线开发。 |
| [`android`](https://github.com/Fnckerpoi/Cheater-IO/tree/android) | Android | Android 宿主扩展、SCADA 事实字幕及 ESP32 固件。该分支的 README 独立维护。 |
| [`ios-v1`](https://github.com/Fnckerpoi/Cheater-IO/tree/ios-v1) | iOS V1 | 独立 SDK 与示例客户端路线。该分支的 README 独立维护。 |
| [`ios-v2`](https://github.com/Fnckerpoi/Cheater-IO/tree/ios-v2) | iOS V2 | 兼容官方 iOS App 副本中的扩展路线。该分支的 README 独立维护。 |

V1 与 V2 是不同的技术路线；1.0.6、1.0.7 等数字表示官方宿主 App 版本，不表示 V1/V2。每条路线的功能、依赖、构建和使用说明请查看对应分支 README。

## 开发与同步

克隆个人仓库后，先切换到要开发的路线分支，例如：

```powershell
git switch android
# 或 git switch ios-v1
# 或 git switch ios-v2
```

提交个人开发后推送当前路线：

```powershell
git push
```

同步原作者更新时，在 `main` 获取并合并上游主线，然后将 `main` 合并到需要更新的路线分支：

```powershell
git switch main
git fetch upstream
git merge upstream/main

git switch android
git merge main
```

把示例中的 `android` 换成 `ios-v1` 或 `ios-v2` 即可更新对应路线。解决冲突并完成该路线所需验证后再推送。路线分支的 README 和个人提交各自独立维护，不要把个人路线提交合并回 `main`。
