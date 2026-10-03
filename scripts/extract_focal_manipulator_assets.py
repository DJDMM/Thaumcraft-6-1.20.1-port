"""Copy byte-exact BETA26 focus editor assets; no generated replacements."""
from pathlib import Path
import hashlib
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
JAR = PROJECT.parents[1] / "work/Thaumcraft-1.12.2-6.1.BETA26.jar"
PINNED_SHA = "9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f"
assert hashlib.sha256(JAR.read_bytes()).hexdigest() == PINNED_SHA
GUI_NAMES = {"gui_wandtable.png", "gui_wandtable2.png", "gui_wandtable3.png", "complex.png", "costxp.png", "costvis.png"}
with zipfile.ZipFile(JAR) as archive:
    names = [name for name in archive.namelist() if name.startswith("assets/thaumcraft/textures/foci/") and name.endswith(".png")]
    names += [f"assets/thaumcraft/textures/gui/{name}" for name in sorted(GUI_NAMES)]
    for name in names:
        target = PROJECT / "src/main/resources" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read(name))
print(f"Copied {len(names)} byte-exact BETA26 focus editor textures")
