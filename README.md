# Perch

一个 Android 原生（Kotlin + Jetpack Compose）聊天界面应用。

## 怎么编

推到 `main` / `master` 分支，或在仓库的 Actions 页面点「Build APK」手动运行。
用 Java 17 + Gradle 8.13。不需要本地安装 Android SDK，也不需要任何签名密钥。

## APK 在哪拿

Actions 页面最新一次运行 → 拉到底部 Artifacts → 下载 `Perch-debug-apk`。

## 界面图标

界面图标全部是 24×24 的 SVG 线条图标，定义在 `app/src/main/java/com/example/ap/ui/AppIcons.kt`，共 20 个。

其中 `UP`、`PLUS`、`CHEVRON_DOWN` 三个简单图形（上箭头、加号、下折角）与 Lucide 图标库的对应图标几何一致，按 ISC 许可使用；其余 17 个为自绘。

### Lucide

本项目使用了 [Lucide](https://lucide.dev) 图标库中的 `arrow-up`、`plus`、`chevron-down` 三个图标。

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

## 许可证

本项目以 GNU Affero General Public License v3.0（AGPL-3.0）发布，完整条款见 `LICENSE` 文件。

Copyright (C) 2026 luoluo

本项目按现状提供，不提供任何担保。
