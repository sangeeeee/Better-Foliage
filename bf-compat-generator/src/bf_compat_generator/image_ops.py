from __future__ import annotations

from hashlib import sha256
from io import BytesIO
from math import atan2, cos, hypot, pi, sin

from PIL import Image


def is_grayscale(
    image: Image.Image,
    *,
    tolerance: int = 4,
    required_ratio: float = 0.98,
) -> bool:
    """Return whether almost all visible pixels have near-equal RGB channels."""
    rgba = image.convert("RGBA")
    visible = 0
    grayscale = 0
    pixels = rgba.tobytes()
    for offset in range(0, len(pixels), 4):
        red, green, blue, alpha = pixels[offset:offset + 4]
        if alpha == 0:
            continue
        visible += 1
        if max(red, green, blue) - min(red, green, blue) <= tolerance:
            grayscale += 1
    return visible > 0 and grayscale / visible >= required_ratio


def generate_fluff(
    source: Image.Image,
    *,
    key: str,
    scale: int = 2,
    animated: bool = False,
) -> Image.Image:
    """Tile source pixels and crop every frame with a jagged round pixel mask."""
    rgba = source.convert("RGBA")
    if animated and rgba.height % rgba.width == 0:
        frame_size = rgba.width
        frames = [
            rgba.crop((0, y, frame_size, y + frame_size))
            for y in range(0, rgba.height, frame_size)
        ]
        output_size = frame_size * scale
        result = Image.new("RGBA", (output_size, output_size * len(frames)), (0, 0, 0, 0))
        for index, frame in enumerate(frames):
            result.alpha_composite(
                _generate_frame(frame, output_size, f"{key}#{index}"),
                (0, index * output_size),
            )
        return result

    output_size = max(rgba.width, rgba.height) * scale
    return _generate_frame(rgba, output_size, key)


def encode_png(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=True)
    return output.getvalue()


def _generate_frame(source: Image.Image, size: int, key: str) -> Image.Image:
    tiled = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(0, size, source.height):
        for x in range(0, size, source.width):
            tiled.alpha_composite(source, (x, y))

    mask = _jagged_circle_mask(size, key)
    source_alpha = tiled.getchannel("A")
    combined_alpha = bytes(
        min(source_value, mask_value)
        for source_value, mask_value in zip(source_alpha.tobytes(), mask.tobytes())
    )
    tiled.putalpha(Image.frombytes("L", (size, size), combined_alpha))
    return tiled


def _jagged_circle_mask(size: int, key: str) -> Image.Image:
    digest = sha256(key.encode("utf-8")).digest()
    phase_a = digest[0] / 255.0 * 2.0 * pi
    phase_b = digest[1] / 255.0 * 2.0 * pi
    phase_c = digest[2] / 255.0 * 2.0 * pi
    center = (size - 1) / 2.0
    base_radius = size * 0.455
    amplitude = max(0.75, size / 32.0)
    pixels = bytearray(size * size)

    for y in range(size):
        for x in range(size):
            dx = x - center
            dy = y - center
            angle = atan2(dy, dx)
            variation = amplitude * (
                0.55 * sin(7.0 * angle + phase_a)
                + 0.30 * sin(11.0 * angle + phase_b)
                + 0.15 * cos(17.0 * angle + phase_c)
            )
            if hypot(dx, dy) <= base_radius + variation:
                pixels[y * size + x] = 255

    return Image.frombytes("L", (size, size), bytes(pixels))
