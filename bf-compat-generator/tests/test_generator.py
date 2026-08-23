from __future__ import annotations

import json
import tempfile
import unittest
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

from bf_compat_generator.cli import _read_texture_list
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
                    "assets/example/textures/block/frost/cold_needles.png",
                    _png((70, 90, 110, 255)),
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
                    "assets/example/models/block/frost/cold_needles.json",
                    json.dumps({"textures": {"all": "example:block/frost/cold_needles"}}),
                )
                jar.writestr(
                    "assets/example/blockstates/orange_maple_leaves.json",
                    json.dumps({"variants": {"": {"model": "example:block/orange_maple_leaves"}}}),
                )
                jar.writestr(
                    "assets/example/blockstates/ebony_leaves.json",
                    json.dumps({"variants": {"": {"model": "example:block/ebony/leaves"}}}),
                )
                jar.writestr(
                    "assets/example/blockstates/cold_needles.json",
                    json.dumps({"variants": {"": {"model": "example:block/frost/cold_needles"}}}),
                )

            report = generate_pack(
                jar_path,
                output,
                GenerationOptions(extra_textures=("cold_needles.png", "missing_foliage.png")),
            )

            colored_model = _read_json(output / "assets/betterfoliage_example/models/block/orange_maple_leaves.json")
            gray_model = _read_json(output / "assets/betterfoliage_example/models/block/ebony/leaves.json")
            self.assertFalse(colored_model["tintLeaves"])
            self.assertTrue(gray_model["tintLeaves"])
            self.assertEqual(gray_model["fluff"], "betterfoliage_example:block/ebony/fluff")
            custom_model = _read_json(output / "assets/betterfoliage_example/models/block/frost/cold_needles.json")
            self.assertEqual(
                custom_model["fluff"],
                "betterfoliage_example:block/frost/cold_needles_fluff",
            )

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

            custom_fluff = output / "assets/betterfoliage_example/textures/block/frost/cold_needles_fluff.png"
            self.assertTrue(custom_fluff.is_file())
            self.assertEqual(len(report["generated_namespaces"]["example"]["textures"]), 4)
            self.assertEqual(report["requested_extra_textures_not_found"], ["missing_foliage.png"])
            self.assertTrue(
                any(item["blockstate"] == "example:leaves" for item in report["inferred_mappings"])
            )

    def test_nonstandard_texture_is_ignored_without_texture_list(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            jar_path = root / "example.jar"
            output = root / "pack"
            with ZipFile(jar_path, "w") as jar:
                jar.writestr(
                    "assets/example/textures/block/ordinary_leaves.png",
                    _png((60, 80, 60, 255)),
                )
                jar.writestr(
                    "assets/example/textures/block/special/canopy.png",
                    _png((30, 90, 50, 255)),
                )

            report = generate_pack(jar_path, output, GenerationOptions())

            textures = report["generated_namespaces"]["example"]["textures"]
            self.assertEqual([item["source"] for item in textures], ["example:block/ordinary_leaves"])
            self.assertFalse(
                (output / "assets/betterfoliage_example/textures/block/special/canopy_fluff.png").exists()
            )

    def test_texture_list_ignores_comments_and_duplicate_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "textures.txt"
            path.write_text(
                "\ufeff# comment\nCold_Needles.png\n\nfrost\\canopy.png\nCOLD_NEEDLES.PNG\n",
                encoding="utf-8",
            )
            self.assertEqual(
                _read_texture_list(path),
                ("Cold_Needles.png", "frost\\canopy.png"),
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
