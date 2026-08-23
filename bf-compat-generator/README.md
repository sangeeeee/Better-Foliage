# Better Foliage 自动兼容包生成器

这个工具读取 Minecraft 模组 JAR，查找所有 `assets/<命名空间>/textures/block/**/**leaves.png`，生成圆形透明 fluff，并重写对应的 blockstate/model JSON，使其使用 Better Foliage 的 `betterfoliage:leaves` loader。

## 安装

需要 Python 3.10 或更高版本。

```powershell
cd bf-compat-generator
python -m pip install -e .
```

## 使用

```powershell
bf-compat-generator "D:\mods\example-mod.jar" --output "D:\resourcepacks\Example BF Compat"
```

也可以不安装，直接在本目录运行：

```powershell
$env:PYTHONPATH = "src"
python -m bf_compat_generator "D:\mods\example-mod.jar" --output "generated\Example BF Compat"
```

常用参数：

```text
--pack-format 34          输出资源包格式，默认适用于 Minecraft 1.21/1.21.1
--namespace MODID         只处理指定命名空间，可重复使用
--include-minecraft       同时处理 assets/minecraft（默认跳过）
--texture-list FILE       额外处理 TXT 中列出的非标准树叶贴图
--zip                     另外生成同名 ZIP 资源包
--force                   明确覆盖已有的非空输出目录或 ZIP
--grayscale-tolerance 4   灰度检测允许的 RGB 通道误差
--grayscale-ratio 0.98    至少多少比例的非透明像素满足灰度条件
```

### 非标准树叶文件名

如果某些树叶贴图不以 `leaves.png` 结尾，可以建立一个 UTF-8 TXT：

```text
# 纯文件名：匹配任意 block 子目录中的同名文件
cold_domain_foliage.png

# textures/block 下的相对路径，Windows 反斜杠也可以
frost\cold_needles.png

# 带命名空间的精确路径
examplemod:block/special/canopy.png
```

然后运行：

```powershell
bf-compat-generator "D:\mods\example-mod.jar" `
  --output "generated\Example BF Compat" `
  --texture-list "extra-leaves.txt"
```

TXT 条目会与默认的 `*leaves.png` 扫描结果合并；不传该参数时行为与以前完全相同。空行和以 `#` 开头的注释会被忽略，未找到的条目记录在 `generation-report.json` 的 `requested_extra_textures_not_found` 中。非标准名称默认追加 `_fluff`，例如 `cold_needles.png` 生成 `cold_needles_fluff.png`。

## 输出结构

对于源命名空间 `examplemod`，工具使用独立的 `betterfoliage_examplemod` 命名空间保存生成内容：

```text
pack.mcmeta
generation-report.json
assets/
  examplemod/
    blockstates/...                         # 保留原条件，只改模型引用
  betterfoliage_examplemod/
    models/block/...                        # Better Foliage loader JSON
    textures/block/..._fluff.png            # 生成的 32×32 fluff
```

独立命名空间可以避免覆盖原模组贴图。blockstate 必须留在原命名空间，因为方块注册 ID 的命名空间不能改变。

## 映射策略

工具优先解析原 JAR 的模型和 blockstate：

1. 解析模型的 `parent` 与 `textures`，找出实际引用叶子贴图的模型。
2. 在 blockstate 的 `variants` 或 `multipart` 中替换对应模型引用，同时保留旋转、权重、条件等字段。
3. 如果原模型关系无法解析，则按约定推断。例如 `block/ebony/leaves.png` 会推断为方块 `ebony_leaves`。
4. 所有精确映射、推断映射和未解析贴图都会记录在 `generation-report.json`。

即使某张叶子贴图无法可靠映射到方块，工具仍会生成对应的 fluff PNG；它只会跳过不确定的 blockstate/model 自动接线，便于之后手工补充 JSON。

由于“贴图文件名”与“方块注册 ID”之间没有 Minecraft 强制标准，推断条目建议进游戏验证。复杂的 CTM、Fusion、随机模型、连接纹理或自定义渲染器仍可能需要手工适配。

## 图像处理

- 16×16 静态叶子会以 2×2 平铺扩展成 32×32。
- 使用像素级、不抗锯齿的轻微不规则圆形蒙版裁剪，背景保持透明。
- 灰度贴图会在生成的模型 JSON 中写入 `"tintLeaves": true`；彩色贴图写入 `false`。
- 对带 `.png.mcmeta` 的纵向方形帧动画，会逐帧生成 fluff 并复制动画元数据。
