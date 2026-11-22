# Perch

一个 Android 原生（Kotlin + Jetpack Compose）聊天界面应用。

## 怎么编

推到 `main` / `master` 分支，或在仓库的 Actions 页面点「Build APK」手动运行。
用 Java 17 + Gradle 8.13。不需要本地安装 Android SDK，也不需要任何签名密钥。

## APK 在哪拿

Actions 页面最新一次运行 → 拉到底部 Artifacts → 下载 `Perch-debug-apk`。

## 界面图标

界面图标全部是 24×24 的 SVG 线条图标，定义在 `app/src/main/java/com/example/ap/ui/AppIcons.kt`，共 20 个。

其中 `UP`、`PLUS`、`CHEVRON_DOWN` 三个（向上箭头、加号、向下折角）与 Lucide 图标库的对应图标几何一致，来自 Lucide；其余 17 个为自绘。

启动图标（`res/mipmap-*` 下的 `ic_launcher*`）为自制，未使用第三方素材。

### Lucide

本项目使用了 [Lucide](https://lucide.dev) 图标库中的 `arrow-up`、`plus`、`chevron-down` 三个图标。

Lucide 以 ISC 许可发布。其中部分图标源自 [Feather](https://feathericons.com) 项目（MIT 许可），`arrow-up`、`plus`、`chevron-down` 三者均在 Feather 派生列表内，因此同时适用下述 MIT 条款。

以下为 Lucide 官方 LICENSE 原文（全文照录，未作改动）：

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

---

The following Lucide icons are derived from the Feather project:

airplay, alert-circle, alert-octagon, alert-triangle, aperture, arrow-down-circle, arrow-down-left, arrow-down-right, arrow-down, arrow-left-circle, arrow-left, arrow-right-circle, arrow-right, arrow-up-circle, arrow-up-left, arrow-up-right, arrow-up, at-sign, calendar, cast, check, chevron-down, chevron-left, chevron-right, chevron-up, chevrons-down, chevrons-left, chevrons-right, chevrons-up, circle, clipboard, clock, code, columns, command, compass, corner-down-left, corner-down-right, corner-left-down, corner-left-up, corner-right-down, corner-right-up, corner-up-left, corner-up-right, crosshair, database, divide-circle, divide-square, dollar-sign, download, external-link, feather, frown, hash, headphones, help-circle, info, italic, key, layout, life-buoy, link-2, link, loader, lock, log-in, log-out, maximize, meh, minimize, minimize-2, minus-circle, minus-square, minus, monitor, moon, more-horizontal, more-vertical, move, music, navigation-2, navigation, octagon, pause-circle, percent, plus-circle, plus-square, plus, power, radio, rss, search, server, share, shopping-bag, sidebar, smartphone, smile, square, table-2, tablet, target, terminal, trash-2, trash, triangle, tv, type, upload, x-circle, x-octagon, x-square, x, zoom-in, zoom-out

The MIT License (MIT) (for the icons listed above)

Copyright (c) 2013-present Cole Bemis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 许可证

本项目以 GNU Affero General Public License v3.0（AGPL-3.0）发布，完整条款见 `LICENSE` 文件。

Copyright (C) 2026 luoluo

本项目按现状提供，不提供任何担保。
