import math
import wave
import struct
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "resources"
ANDROID = RES / "android"

ACCENT = (91, 75, 245)
BG_DARK = (13, 15, 28)
WHITE = (255, 255, 255)

BUBBLE_PATH = [
    ("cubic", (256, 130), (183, 130), (124, 180), (124, 242)),
    ("cubic", (124, 242), (124, 274), (140, 303), (165, 323)),
    ("line", (165, 323), (151, 370)),
    ("line", (151, 370), (203, 345)),
    ("cubic", (203, 345), (220, 351), (237, 354), (256, 354)),
    ("cubic", (256, 354), (329, 354), (388, 304), (388, 242)),
    ("cubic", (388, 242), (388, 180), (329, 130), (256, 130)),
]


def cubic_point(p0, p1, p2, p3, t):
    u = 1 - t
    return (
        u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
        u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1],
    )


def bubble_points():
    points = []
    for segment in BUBBLE_PATH:
        if segment[0] == "line":
            points.append(segment[2])
            continue
        for i in range(1, 25):
            points.append(cubic_point(*segment[1:], i / 24))
    return [BUBBLE_PATH[0][1]] + points


def glyph(size, fill, offset=(0, 0), scale=1.0, box=512):
    factor = 4
    canvas = Image.new("RGBA", (size * factor, size * factor), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    k = size * factor / box * scale
    cx = size * factor / 2 + offset[0] * factor
    cy = size * factor / 2 + offset[1] * factor
    pts = [((px - 256) * k + cx, (py - 256) * k + cy) for px, py in bubble_points()]
    draw.polygon(pts, fill=fill + (255,))
    return canvas.resize((size, size), Image.LANCZOS)


def flat(size, color):
    return Image.new("RGBA", (size, size), color + (255,))


def compose(background, size, scale):
    image = background.copy()
    image.alpha_composite(glyph(size, WHITE, scale=scale))
    return image


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def chime(path):
    rate = 22050
    notes = [(880.0, 0.0, 0.16), (1318.5, 0.14, 0.30)]
    total = int(rate * 0.55)
    samples = [0.0] * total
    for freq, start, length in notes:
        begin = int(start * rate)
        count = int(length * rate)
        for i in range(count):
            if begin + i >= total:
                break
            t = i / rate
            envelope = min(1.0, i / (rate * 0.008)) * math.exp(-t * 9.0)
            samples[begin + i] += math.sin(2 * math.pi * freq * t) * envelope * 0.45
            samples[begin + i] += math.sin(2 * math.pi * freq * 2 * t) * envelope * 0.12
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))


def main():
    accent_bg = flat(1024, ACCENT)
    save(compose(accent_bg, 1024, 1.0), RES / "icon-only.png")
    save(accent_bg, RES / "icon-background.png")
    foreground = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    foreground.alpha_composite(glyph(1024, WHITE, scale=0.62))
    save(foreground, RES / "icon-foreground.png")

    splash = flat(2732, BG_DARK)
    logo = compose(flat(560, ACCENT), 560, 1.0)
    mask = Image.new("L", (560, 560), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 559, 559), fill=255)
    splash.paste(logo, ((2732 - 560) // 2, (2732 - 560) // 2), mask)
    save(splash, RES / "splash.png")
    save(splash, RES / "splash-dark.png")

    splash_icon = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    disc = Image.new("L", (512, 512), 0)
    ImageDraw.Draw(disc).ellipse((0, 0, 511, 511), fill=255)
    splash_icon.paste(compose(flat(512, ACCENT), 512, 1.0), (0, 0), disc)
    save(splash_icon, ANDROID / "splash_icon.png")

    sizes = {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72, "xxxhdpi": 96}
    for density, size in sizes.items():
        save(glyph(size, WHITE, scale=0.92), ANDROID / "notification-icon" / (density + ".png"))

    chime(ANDROID / "raw" / "kotha_message.wav")


if __name__ == "__main__":
    main()
