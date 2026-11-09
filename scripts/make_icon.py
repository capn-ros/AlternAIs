# Genera el ícono de AlternAIs: windows-native/.../Assets/icon.ico y los gráficos del instalador (BMP). Requiere Pillow.
# Un círculo partido en dos mitades desfasadas: coral (Claude) y crema (Replit), sobre fondo carbón.
# El ícono de Android (ic_launcher_foreground.xml / ic_logo.xml) y el logo de Windows son copias del mismo diseño.
import os
from PIL import Image, ImageDraw

S = 4  # supersampling
N = 512 * S
U = 512 / 120  # el diseño base está en un lienzo de 120
BG = (28, 25, 23, 255)
CORAL, CREAM = (217, 119, 87, 255), (245, 239, 230, 255)
R = 32 * U  # radio del círculo
C = 256


def logo(background=True):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if background:
        d.rounded_rectangle([0, 0, N - 1, N - 1], radius=int(28 * U * S), fill=BG)
    # mitad izquierda (coral) un poco abajo y mitad derecha (crema) un poco arriba
    for (dx, dy), a0, a1, col in (((-1, 5), 90, 270, CORAL), ((1, -5), 270, 90, CREAM)):
        cx, cy = C + dx * U, C + dy * U
        d.pieslice([(cx - R) * S, (cy - R) * S, (cx + R) * S, (cy + R) * S], a0, a1, fill=col)
    return img


if __name__ == "__main__":
    root = os.path.join(os.path.dirname(__file__), "..", "windows-native")
    assets = os.path.join(root, "src", "AlternAIs.App", "Assets")
    os.makedirs(assets, exist_ok=True)
    icon = logo().resize((512, 512), Image.LANCZOS)
    icon.save(os.path.join(assets, "icon.ico"), sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])

    inst = os.path.join(root, "installer")
    if os.path.isdir(inst):
        panel = Image.new("RGB", (164, 314), (28, 25, 23))
        px = panel.load()
        for y in range(314):
            t = y / 313
            for x in range(164):
                px[x, y] = (int(28 + 12 * t), int(25 + 9 * t), int(23 + 7 * t))
        mark = logo(False).resize((150, 150), Image.LANCZOS)
        panel.paste(mark, (7, 82), mark)
        ImageDraw.Draw(panel).rectangle([55, 252, 108, 254], fill=(217, 119, 87))
        panel.save(os.path.join(inst, "claudeck-wizard.bmp"))
        small = Image.new("RGB", (55, 55), (28, 25, 23))
        m = logo(True).resize((55, 55), Image.LANCZOS)
        small.paste(m, (0, 0), m)
        small.save(os.path.join(inst, "claudeck-wizard-small.bmp"))
    print("ok")
