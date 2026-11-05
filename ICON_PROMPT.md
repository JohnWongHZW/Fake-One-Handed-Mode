# 图标生成记录

模式：内置 image_gen 图像生成工具（未使用 CLI）。

应用图标：`app/res/drawable-nodpi/hand_icon.png`。生成结果原样复制到项目，保留绿色背景；自适应图标由 Android 按桌面形状裁切。旧功能图标仍使用原始 `portrait.webp`。

最终生成提示词：

```text
Use case: logo-brand. Create a square Android launcher icon, no text, for a one-handed phone mode app. Minimal, friendly emoji-style illustration with clean rounded shapes and subtle soft 3D shading. A single anatomically readable RIGHT hand in warm golden-yellow emoji skin tone holds one upright smartphone from its right edge and below; thumb curls over right edge without obscuring the screen. Front-facing phone with dark charcoal rounded frame. Phone screen is medium gray; ONLY a smaller rounded rectangle anchored at the LOWER RIGHT of the screen is bright white, representing a scaled one-handed workspace; the entire rest of the phone screen remains gray. No app glyphs or screen text. Rich fresh green background filling the whole square, slightly soft tonal variation allowed. Center the hand and phone with generous green padding for adaptive circle/squircle cropping, all essential details inside the middle 65 percent. Bold recognizable silhouette, crisp smooth edges, very few details, polished playful emoji look. No logos, lettering, extra fingers, second hand, transparent margins or baked-in outer rounded-square border.
```
