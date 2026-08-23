from __future__ import annotations

import argparse
from pathlib import Path
from zipfile import BadZipFile

from .generator import GenerationOptions, generate_pack


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="bf-compat-generator",
        description="从 Minecraft 模组 JAR 自动生成 Better Foliage 兼容资源包。",
    )
    parser.add_argument("jar", type=Path, help="待扫描的模组 JAR")
    parser.add_argument("-o", "--output", type=Path, required=True, help="输出资源包目录")
    parser.add_argument("--pack-format", type=int, default=34, help="pack.mcmeta 的 pack_format（默认：34）")
    parser.add_argument("--description", help="资源包描述")
    parser.add_argument("--namespace", action="append", default=[], help="只处理指定命名空间，可重复")
    parser.add_argument("--include-minecraft", action="store_true", help="允许扫描 assets/minecraft")
    parser.add_argument(
        "--texture-list",
        type=Path,
        help="额外树叶贴图列表 TXT；每行一个文件名、block 相对路径或命名空间路径",
    )
    parser.add_argument("--grayscale-tolerance", type=int, default=4, help="灰度检测 RGB 误差（默认：4）")
    parser.add_argument("--grayscale-ratio", type=float, default=0.98, help="灰度像素最低比例（默认：0.98）")
    parser.add_argument("--scale", type=int, default=2, help="fluff 相对原始单帧尺寸的倍率（默认：2）")
    parser.add_argument("--zip", action="store_true", help="同时生成 ZIP 资源包")
    parser.add_argument("--force", action="store_true", help="覆盖已有的非空输出目录或 ZIP")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.scale < 1:
        raise SystemExit("--scale 必须至少为 1")
    if not 0.0 <= args.grayscale_ratio <= 1.0:
        raise SystemExit("--grayscale-ratio 必须在 0 到 1 之间")
    if args.grayscale_tolerance < 0:
        raise SystemExit("--grayscale-tolerance 不能为负数")

    try:
        extra_textures = _read_texture_list(args.texture_list) if args.texture_list else ()
        options = GenerationOptions(
            pack_format=args.pack_format,
            description=args.description,
            namespaces={value.lower() for value in args.namespace} or None,
            include_minecraft=args.include_minecraft,
            grayscale_tolerance=args.grayscale_tolerance,
            grayscale_ratio=args.grayscale_ratio,
            scale=args.scale,
            make_zip=args.zip,
            force=args.force,
            extra_textures=extra_textures,
        )
        report = generate_pack(args.jar, args.output, options)
    except (OSError, ValueError, BadZipFile) as exc:
        print(f"错误：{exc}")
        return 2
    texture_count = sum(
        len(namespace["textures"])
        for namespace in report["generated_namespaces"].values()
    )
    model_count = sum(
        len(namespace["models"])
        for namespace in report["generated_namespaces"].values()
    )
    print(f"已生成：{args.output.resolve()}")
    print(f"fluff 贴图：{texture_count}")
    print(f"兼容模型：{model_count}")
    print(f"未解析贴图：{len(report['unresolved_textures'])}")
    if report["requested_extra_textures_not_found"]:
        print(f"列表中未找到：{len(report['requested_extra_textures_not_found'])}")
    if report.get("zip"):
        print(f"ZIP：{report['zip']}")
    if report["warnings"]:
        print(f"警告：{len(report['warnings'])}，详情见 generation-report.json")
    return 0


def _read_texture_list(path: Path) -> tuple[str, ...]:
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    result: list[str] = []
    seen: set[str] = set()
    for line in lines:
        value = line.strip()
        if not value or value.startswith("#"):
            continue
        normalized = value.replace("\\", "/").lower()
        if normalized not in seen:
            seen.add(normalized)
            result.append(value)
    return tuple(result)
