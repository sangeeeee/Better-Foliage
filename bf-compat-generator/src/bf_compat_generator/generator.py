from __future__ import annotations

import json
import re
import shutil
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from zipfile import ZIP_DEFLATED, ZipFile

from PIL import Image

from .image_ops import encode_png, generate_fluff, is_grayscale


LEAF_TEXTURE_RE = re.compile(
    r"^assets/(?P<namespace>[a-z0-9_.-]+)/textures/block/(?P<path>.*leaves)\.png$",
    re.IGNORECASE,
)
BLOCK_TEXTURE_RE = re.compile(
    r"^assets/(?P<namespace>[a-z0-9_.-]+)/textures/block/(?P<path>.+)\.png$",
    re.IGNORECASE,
)
MODEL_RE = re.compile(
    r"^assets/(?P<namespace>[a-z0-9_.-]+)/models/(?P<path>.+)\.json$",
    re.IGNORECASE,
)
BLOCKSTATE_RE = re.compile(
    r"^assets/(?P<namespace>[a-z0-9_.-]+)/blockstates/(?P<path>.+)\.json$",
    re.IGNORECASE,
)


@dataclass(frozen=True, order=True)
class ResourceId:
    namespace: str
    path: str

    def __post_init__(self) -> None:
        if not re.fullmatch(r"[a-z0-9_.-]+", self.namespace):
            raise ValueError(f"非法资源命名空间：{self.namespace}")
        if not re.fullmatch(r"[a-z0-9/._-]+", self.path):
            raise ValueError(f"非法资源路径：{self.path}")
        if self.path.startswith("/") or ".." in PurePosixPath(self.path).parts:
            raise ValueError(f"不安全的资源路径：{self.path}")

    @classmethod
    def parse(cls, value: str, default_namespace: str) -> "ResourceId":
        if ":" in value:
            namespace, path = value.split(":", 1)
        else:
            namespace, path = default_namespace, value
        return cls(namespace.lower(), path.lower())

    def __str__(self) -> str:
        return f"{self.namespace}:{self.path}"


@dataclass(frozen=True)
class LeafTexture:
    resource: ResourceId
    entry_name: str
    metadata_entry: str | None

    @property
    def relative_path(self) -> str:
        return self.resource.path.removeprefix("block/")

    @property
    def fluff_relative_path(self) -> str:
        path = PurePosixPath(self.relative_path)
        name = path.name
        if name.endswith("leaves"):
            fluff_name = f"{name[:-len('leaves')]}fluff"
        else:
            fluff_name = f"{name}_fluff"
        return (path.parent / fluff_name).as_posix()

    @property
    def inferred_blockstate(self) -> str:
        return self.relative_path.replace("/", "_")


@dataclass
class GenerationOptions:
    pack_format: int = 34
    description: str | None = None
    namespaces: set[str] | None = None
    include_minecraft: bool = False
    grayscale_tolerance: int = 4
    grayscale_ratio: float = 0.98
    scale: int = 2
    make_zip: bool = False
    force: bool = False
    extra_textures: tuple[str, ...] = ()


def generate_pack(jar_path: Path, output_dir: Path, options: GenerationOptions) -> dict[str, Any]:
    jar_path = jar_path.resolve()
    output_dir = output_dir.resolve()
    if not jar_path.is_file():
        raise FileNotFoundError(f"找不到输入 JAR：{jar_path}")
    if output_dir == jar_path or jar_path in output_dir.parents:
        raise ValueError("输出目录不能覆盖输入 JAR")

    if output_dir.exists() and not output_dir.is_dir():
        raise FileExistsError(f"输出路径已经是文件：{output_dir}")
    if output_dir.exists() and any(output_dir.iterdir()):
        if not options.force:
            raise FileExistsError(f"输出目录不是空目录；如需覆盖请使用 --force：{output_dir}")
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True)

    report: dict[str, Any] = {
        "input": str(jar_path),
        "generated_namespaces": {},
        "warnings": [],
    }

    with ZipFile(jar_path) as jar:
        entries = {info.filename.replace("\\", "/"): info for info in jar.infolist() if not info.is_dir()}
        leaves, unmatched_extra_textures = _find_leaf_textures(entries, options)
        report["requested_extra_textures"] = list(options.extra_textures)
        report["requested_extra_textures_not_found"] = unmatched_extra_textures
        models, model_errors = _read_json_entries(jar, entries, MODEL_RE)
        blockstates, blockstate_errors = _read_json_entries(jar, entries, BLOCKSTATE_RE)
        report["warnings"].extend(model_errors)
        report["warnings"].extend(blockstate_errors)

        model_to_texture, ambiguous_models = _map_models_to_textures(models, leaves)
        mappings, unresolved, inferred_mappings = _map_blockstates(
            blockstates,
            model_to_texture,
            leaves,
        )

        generated_texture_keys: set[ResourceId] = set(leaves)
        for blockstate_key, replacements in sorted(mappings.items()):
            source_json = blockstates.get(blockstate_key)
            if source_json is None:
                source_json = {
                    "variants": {
                        "": {"model": str(next(iter(replacements.values()))[0])}
                    }
                }
            rewritten = _rewrite_models(source_json, replacements, blockstate_key.namespace)
            _write_json(
                output_dir / "assets" / blockstate_key.namespace / "blockstates" / f"{blockstate_key.path}.json",
                rewritten,
            )

            for source_model, (generated_model, texture) in replacements.items():
                generated_texture_keys.add(texture.resource)
                generated_namespace = generated_model.namespace
                generated_model_path = output_dir / "assets" / generated_namespace / "models" / f"{generated_model.path}.json"
                _write_json(
                    generated_model_path,
                    {
                        "loader": "betterfoliage:leaves",
                        "leaves": str(texture.resource),
                        "fluff": f"{generated_namespace}:block/{texture.fluff_relative_path}",
                        "tintLeaves": _texture_is_grayscale(jar, texture, options),
                    },
                )

        namespace_reports: dict[str, dict[str, Any]] = {}
        for texture in sorted(leaves.values(), key=lambda item: str(item.resource)):
            if texture.resource not in generated_texture_keys:
                continue
            generated_namespace = _generated_namespace(texture.resource.namespace)
            source_image = Image.open(BytesIO(jar.read(texture.entry_name)))
            animated = texture.metadata_entry is not None
            fluff = generate_fluff(
                source_image,
                key=str(texture.resource),
                scale=options.scale,
                animated=animated,
            )
            texture_path = output_dir / "assets" / generated_namespace / "textures" / "block" / f"{texture.fluff_relative_path}.png"
            _write_bytes(texture_path, encode_png(fluff))
            if texture.metadata_entry:
                _write_bytes(Path(f"{texture_path}.mcmeta"), jar.read(texture.metadata_entry))

            ns_report = namespace_reports.setdefault(
                texture.resource.namespace,
                {
                    "generated_namespace": generated_namespace,
                    "textures": [],
                    "blockstates": [],
                    "models": [],
                },
            )
            ns_report["textures"].append(
                {
                    "source": str(texture.resource),
                    "fluff": f"{generated_namespace}:block/{texture.fluff_relative_path}",
                    "size": list(fluff.size),
                    "tintLeaves": _texture_is_grayscale(jar, texture, options),
                    "animated": animated,
                }
            )

        for blockstate_key, replacements in sorted(mappings.items()):
            ns_report = namespace_reports.setdefault(
                blockstate_key.namespace,
                {
                    "generated_namespace": _generated_namespace(blockstate_key.namespace),
                    "textures": [],
                    "blockstates": [],
                    "models": [],
                },
            )
            ns_report["blockstates"].append(str(blockstate_key))
            ns_report["models"].extend(
                {
                    "source": str(source),
                    "generated": str(generated),
                    "texture": str(texture.resource),
                }
                for source, (generated, texture) in sorted(replacements.items())
            )

        for ns_report in namespace_reports.values():
            ns_report["models"] = _unique_dicts(ns_report["models"])
        report["generated_namespaces"] = namespace_reports
        report["unresolved_textures"] = [str(texture.resource) for texture in sorted(unresolved, key=lambda item: str(item.resource))]
        report["inferred_mappings"] = inferred_mappings
        report["ambiguous_models"] = ambiguous_models

    description = options.description or f"Better Foliage compatibility for {jar_path.stem}"
    _write_json(
        output_dir / "pack.mcmeta",
        {"pack": {"pack_format": options.pack_format, "description": description}},
    )
    _write_json(output_dir / "generation-report.json", report)

    if options.make_zip:
        _zip_directory(output_dir, overwrite=options.force)
        report["zip"] = str(output_dir.with_suffix(".zip"))
    return report


def _find_leaf_textures(
    entries: dict[str, Any],
    options: GenerationOptions,
) -> tuple[dict[ResourceId, LeafTexture], list[str]]:
    result: dict[ResourceId, LeafTexture] = {}
    names_lower = {name.lower(): name for name in entries}
    extra_specs = tuple(_normalize_texture_spec(value) for value in options.extra_textures)
    matched_specs: set[int] = set()
    for entry_name in entries:
        normalized_entry = entry_name.lower()
        match = BLOCK_TEXTURE_RE.match(normalized_entry)
        if not match:
            continue
        namespace = match.group("namespace")
        if namespace == "minecraft" and not options.include_minecraft:
            continue
        if options.namespaces and namespace not in options.namespaces:
            continue
        relative_png = f"{match.group('path')}.png"
        matched_extra = {
            index
            for index, spec in enumerate(extra_specs)
            if _texture_spec_matches(spec, namespace, relative_png)
        }
        if not LEAF_TEXTURE_RE.match(normalized_entry) and not matched_extra:
            continue
        matched_specs.update(matched_extra)
        texture = ResourceId(namespace, f"block/{match.group('path')}")
        metadata_entry = names_lower.get(f"{entry_name.lower()}.mcmeta")
        result[texture] = LeafTexture(texture, entry_name, metadata_entry)
    unmatched = [options.extra_textures[index] for index in range(len(extra_specs)) if index not in matched_specs]
    return result, unmatched


def _normalize_texture_spec(value: str) -> str:
    spec = value.strip().replace("\\", "/").lower()
    while spec.startswith("./"):
        spec = spec[2:]
    assets_match = re.fullmatch(
        r"assets/([a-z0-9_.-]+)/textures/block/(.+)",
        spec,
    )
    if assets_match:
        spec = f"{assets_match.group(1)}:{assets_match.group(2)}"
    elif ":" in spec:
        namespace, path = spec.split(":", 1)
        spec = f"{namespace}:{path.removeprefix('textures/block/').removeprefix('block/')}"
    else:
        spec = spec.removeprefix("textures/block/").removeprefix("block/")
    if not spec.endswith(".png"):
        spec += ".png"
    return spec


def _texture_spec_matches(spec: str, namespace: str, relative_png: str) -> bool:
    if ":" in spec:
        spec_namespace, spec_path = spec.split(":", 1)
        return spec_namespace == namespace and spec_path == relative_png
    if "/" in spec:
        return spec == relative_png
    return spec == PurePosixPath(relative_png).name


def _read_json_entries(
    jar: ZipFile,
    entries: dict[str, Any],
    pattern: re.Pattern[str],
) -> tuple[dict[ResourceId, Any], list[str]]:
    result: dict[ResourceId, Any] = {}
    errors: list[str] = []
    for entry_name in entries:
        match = pattern.match(entry_name.lower())
        if not match:
            continue
        key = ResourceId(match.group("namespace"), match.group("path"))
        try:
            text = jar.read(entry_name).decode("utf-8-sig")
            result[key] = json.loads(_strip_json_comments(text))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            errors.append(f"无法解析 JSON {entry_name}: {exc}")
    return result, errors


def _map_models_to_textures(
    models: dict[ResourceId, Any],
    leaves: dict[ResourceId, LeafTexture],
) -> tuple[dict[ResourceId, LeafTexture], list[dict[str, Any]]]:
    cache: dict[ResourceId, dict[str, str]] = {}
    result: dict[ResourceId, LeafTexture] = {}
    ambiguous: list[dict[str, Any]] = []
    for model_id in models:
        textures = _resolve_model_textures(model_id, models, cache, set())
        candidates: set[ResourceId] = set()
        for value in textures.values():
            resolved = _resolve_texture_alias(value, textures)
            if not resolved or resolved.startswith("#"):
                continue
            texture_id = ResourceId.parse(resolved, model_id.namespace)
            if texture_id in leaves:
                candidates.add(texture_id)
        if len(candidates) == 1:
            result[model_id] = leaves[next(iter(candidates))]
        elif len(candidates) > 1:
            ambiguous.append(
                {"model": str(model_id), "textures": sorted(map(str, candidates))}
            )
    return result, ambiguous


def _resolve_model_textures(
    model_id: ResourceId,
    models: dict[ResourceId, Any],
    cache: dict[ResourceId, dict[str, str]],
    visiting: set[ResourceId],
) -> dict[str, str]:
    if model_id in cache:
        return cache[model_id]
    if model_id in visiting:
        return {}
    visiting.add(model_id)
    model = models.get(model_id)
    if not isinstance(model, dict):
        visiting.remove(model_id)
        return {}
    result: dict[str, str] = {}
    parent = model.get("parent")
    if isinstance(parent, str):
        parent_id = ResourceId.parse(parent, model_id.namespace)
        result.update(_resolve_model_textures(parent_id, models, cache, visiting))
    own_textures = model.get("textures")
    if isinstance(own_textures, dict):
        result.update({str(key): value for key, value in own_textures.items() if isinstance(value, str)})
    visiting.remove(model_id)
    cache[model_id] = result
    return result


def _resolve_texture_alias(value: str, textures: dict[str, str]) -> str | None:
    visited: set[str] = set()
    while value.startswith("#"):
        alias = value[1:]
        if alias in visited:
            return None
        visited.add(alias)
        next_value = textures.get(alias)
        if not isinstance(next_value, str):
            return None
        value = next_value
    return value


def _map_blockstates(
    blockstates: dict[ResourceId, Any],
    model_to_texture: dict[ResourceId, LeafTexture],
    leaves: dict[ResourceId, LeafTexture],
) -> tuple[
    dict[ResourceId, dict[ResourceId, tuple[ResourceId, LeafTexture]]],
    list[LeafTexture],
    list[dict[str, Any]],
]:
    mappings: dict[ResourceId, dict[ResourceId, tuple[ResourceId, LeafTexture]]] = {}
    used: set[ResourceId] = set()
    inferred_mappings: list[dict[str, Any]] = []

    for blockstate_id, data in blockstates.items():
        replacements: dict[ResourceId, tuple[ResourceId, LeafTexture]] = {}
        for model_ref in _iter_model_references(data):
            model_id = ResourceId.parse(model_ref, blockstate_id.namespace)
            texture = model_to_texture.get(model_id)
            if texture is None and model_id.path.startswith("block/"):
                texture = leaves.get(ResourceId(model_id.namespace, model_id.path))
            if texture is None:
                continue
            generated = ResourceId(_generated_namespace(texture.resource.namespace), model_id.path)
            replacements[model_id] = (generated, texture)
            used.add(texture.resource)
        if replacements:
            mappings[blockstate_id] = replacements

    for texture in leaves.values():
        if texture.resource in used:
            continue
        inferred_state = ResourceId(texture.resource.namespace, texture.inferred_blockstate)
        if inferred_state in mappings:
            continue
        if inferred_state in blockstates:
            source_models = {
                ResourceId.parse(value, inferred_state.namespace)
                for value in _iter_model_references(blockstates[inferred_state])
            }
            if not source_models:
                source_models = {ResourceId(texture.resource.namespace, texture.resource.path)}
        else:
            source_models = {ResourceId(texture.resource.namespace, texture.resource.path)}
        replacements = {
            model_id: (
                ResourceId(_generated_namespace(texture.resource.namespace), model_id.path),
                texture,
            )
            for model_id in source_models
        }
        mappings[inferred_state] = replacements
        used.add(texture.resource)
        inferred_mappings.append(
            {
                "texture": str(texture.resource),
                "blockstate": str(inferred_state),
                "source_blockstate_found": inferred_state in blockstates,
                "source_models": sorted(map(str, source_models)),
            }
        )

    unresolved = [texture for texture in leaves.values() if texture.resource not in used]
    return mappings, unresolved, inferred_mappings


def _iter_model_references(value: Any) -> Iterable[str]:
    if isinstance(value, dict):
        model = value.get("model")
        if isinstance(model, str):
            yield model
        for child in value.values():
            yield from _iter_model_references(child)
    elif isinstance(value, list):
        for child in value:
            yield from _iter_model_references(child)


def _rewrite_models(
    value: Any,
    replacements: dict[ResourceId, tuple[ResourceId, LeafTexture]],
    default_namespace: str | None = None,
) -> Any:
    if isinstance(value, dict):
        result: dict[str, Any] = {}
        for key, child in value.items():
            if key == "model" and isinstance(child, str):
                namespace = default_namespace or next(iter(replacements)).namespace
                model_id = ResourceId.parse(child, namespace)
                replacement = replacements.get(model_id)
                result[key] = str(replacement[0]) if replacement else child
            else:
                result[key] = _rewrite_models(child, replacements, default_namespace)
        return result
    if isinstance(value, list):
        return [_rewrite_models(child, replacements, default_namespace) for child in value]
    return value


def _texture_is_grayscale(jar: ZipFile, texture: LeafTexture, options: GenerationOptions) -> bool:
    with Image.open(BytesIO(jar.read(texture.entry_name))) as image:
        return is_grayscale(
            image,
            tolerance=options.grayscale_tolerance,
            required_ratio=options.grayscale_ratio,
        )


def _generated_namespace(source_namespace: str) -> str:
    return f"betterfoliage_{source_namespace}".replace("-", "_").replace(".", "_")


def _strip_json_comments(text: str) -> str:
    output: list[str] = []
    index = 0
    in_string = False
    escaped = False
    while index < len(text):
        char = text[index]
        if in_string:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == '"':
            in_string = True
            output.append(char)
            index += 1
            continue
        if char == "/" and index + 1 < len(text) and text[index + 1] == "/":
            index += 2
            while index < len(text) and text[index] not in "\r\n":
                index += 1
            continue
        if char == "/" and index + 1 < len(text) and text[index + 1] == "*":
            index += 2
            while index + 1 < len(text) and text[index:index + 2] != "*/":
                index += 1
            index += 2
            continue
        output.append(char)
        index += 1
    return "".join(output)


def _write_json(path: Path, value: Any) -> None:
    _write_bytes(path, (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def _write_bytes(path: Path, value: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(value)


def _unique_dicts(values: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for value in values:
        key = json.dumps(value, sort_keys=True)
        if key not in seen:
            seen.add(key)
            result.append(value)
    return result


def _zip_directory(directory: Path, *, overwrite: bool) -> None:
    zip_path = directory.with_suffix(".zip")
    if zip_path.exists():
        if not overwrite:
            raise FileExistsError(f"ZIP 已存在；如需覆盖请使用 --force：{zip_path}")
        zip_path.unlink()
    with ZipFile(zip_path, "w", ZIP_DEFLATED) as archive:
        for path in sorted(directory.rglob("*")):
            if path.is_file():
                archive.write(path, PurePosixPath(path.relative_to(directory)).as_posix())
