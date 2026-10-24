# Genera windows/build/icon.png e icon.ico (ícono de Claudeck). Requiere Pillow.
import os
from PIL import Image, ImageDraw

S = 4  # supersampling
N = 512 * S
BG, SAGE, AMBER, CORAL = (43, 38, 34, 255), (129, 178, 154, 255), (242, 165, 65, 255), (224, 122, 95, 255)
CREAM, DOT = (255, 248, 240, 255), (194, 90, 62, 255)

def card(color, angle):
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([156 * S, 110 * S, 356 * S, 400 * S], radius=28 * S, fill=color, outline=BG, width=6 * S)
    if angle:
        layer = layer.rotate(angle, resample=Image.BICUBIC, center=(256 * S, 400 * S))
    return layer

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
ImageDraw.Draw(img).rounded_rectangle([0, 0, N - 1, N - 1], radius=112 * S, fill=BG)
for color, angle in ((SAGE, 20), (AMBER, -20), (CORAL, 0)):  # PIL gira en sentido antihorario
    img = Image.alpha_composite(img, card(color, angle))
d = ImageDraw.Draw(img)
d.rounded_rectangle([196 * S, 175 * S, 316 * S, 255 * S], radius=24 * S, fill=CREAM)
d.polygon([(222 * S, 252 * S), (212 * S, 284 * S), (250 * S, 252 * S)], fill=CREAM)
for cx in (226, 256, 286):
    d.ellipse([(cx - 9) * S, (215 - 9) * S, (cx + 9) * S, (215 + 9) * S], fill=DOT)
img = img.resize((512, 512), Image.LANCZOS)
out = os.path.join(os.path.dirname(__file__), "..", "windows", "build")
os.makedirs(out, exist_ok=True)
img.save(os.path.join(out, "icon.png"))
img.save(os.path.join(out, "icon.ico"), sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("ok")
