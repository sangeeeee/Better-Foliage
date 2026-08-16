from __future__ import annotations

import json
import tempfile
import unittest
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

from bf_compat_generator.generator import GenerationOptions, generate_pack


class GeneratorTest(unittest.TestCase):
    def test_generates_colored_and_nested_grayscale_leaves(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            jar_path = root / "example.jar"
            output = root / "pack"
            with ZipFile(jar_path, "w") as jar:
                jar.writestr(
                    "assets/example/textures/block/orange_maple_leaves.png",
                    _png((180, 80, 10, 255)),
                )
                jar.writestr(
                    "assets/example/textures/block/ebony/leaves.png",
                    _png((100, 100, 100, 255)),
                )
                jar.writestr(
                    "assets/example/textures/block/leaves.png",
                    _png((80, 80, 80, 255)),
                )
                jar.writestr(
                    "assets/example/models/block/orange_maple_leaves.json",
                    json.dumps({"textures": {"all": "example:block/orange_maple_leaves"}}),
                )
                jar.writestr(
                    "assets/example/models/block/ebony/leaves.json",
                    json.dumps({"textures": {"all": "example:block/ebony/leaves"}}),
                )
                jar.writestr(
                    "assets/example/blockstates/orange_maple_leaves.json",
                    json.dumps({"variants": {"": {"model": "example:block/orange_maple_leaves"}}}),
                )
                jar.writestr(
                    "assets/example/blockstates/ebony_leaves.json",
                    json.dumps({"variants": {"": {"model": "example:block/ebony/leaves"}}}),
                )

            report = generate_pack(jar_path, output, GenerationOptions())

            colored_model = _read_json(output / "assets/betterfoliage_example/models/block/orange_maple_leaves.json")
            gray_model = _read_json(output / "assets/betterfoliage_example/models/block/ebony/leaves.json")
            self.assertFalse(colored_model["tintLeaves"])
            self.assertTrue(gray_model["tintLeaves"])
            self.assertEqual(gray_model["fluff"], "betterfoliage_example:block/ebony/fluff")

            rewritten = _read_json(output / "assets/example/blockstates/ebony_leaves.json")
            self.assertEqual(
                rewritten["variants"][""]["model"],
                "betterfoliage_example:block/ebony/leaves",
            )

            fluff_path = output / "assets/betterfoliage_example/textures/block/ebony/fluff.png"
            with Image.open(fluff_path) as fluff:
                self.assertEqual(fluff.size, (32, 32))
                self.assertEqual(fluff.convert("RGBA").getpixel((0, 0))[3], 0)
                self.assertGreater(fluff.convert("RGBA").getpixel((16, 16))[3], 0)

            self.assertEqual(len(report["generated_namespaces"]["example"]["textures"]), 3)
            self.assertTrue(
                any(item["blockstate"] == "example:leaves" for item in report["inferred_mappings"])
            )


def _png(color: tuple[int, int, int, int]) -> bytes:
    image = Image.new("RGBA", (16, 16), color)
    output = BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


def _read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
