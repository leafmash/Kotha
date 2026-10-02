import math
import wave
import struct
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "resources"
ANDROID = RES / "android"

BG_DARK = (13, 15, 28)
WHITE = (255, 255, 255)
BG_STOPS = [(0.0, (138, 125, 255)), (0.55, (91, 75, 245)), (1.0, (58, 44, 196))]
C_STOPS = [(0.0, (125, 111, 255)), (1.0, (63, 49, 214))]

BUBBLE_ORIGIN = 222
BUBBLE_SIZE = 580
BUBBLE_CORNER = 40
RING_CENTER = 508
RING_RADIUS = 118
RING_WIDTH = 64
RING_GAP = 46
SUPERSAMPLE = 4
GRID = 128
DESIGN_CENTER = 512


def polar(cx, cy, radius, degrees):
    angle = math.radians(degrees)
    return cx + radius * math.cos(angle), cy + radius * math.sin(angle)


def arc_points(cx, cy, radius, start, end, steps=90):
    return [polar(cx, cy, radius, start + (end - start) * i / steps) for i in range(steps + 1)]


def bubble_polygon():
    r = BUBBLE_SIZE / 2
    c = DESIGN_CENTER
    far = BUBBLE_ORIGIN + BUBBLE_SIZE
    corner = BUBBLE_CORNER
    points = arc_points(c, c, r, -90, 0)
    points += arc_points(far - corner, far - corner, corner, 0, 90, 20)
    points += arc_points(c, c, r, 90, 270)
    return points


def ring_polygon():
    outer = RING_RADIUS + RING_WIDTH / 2
    inner = RING_RADIUS - RING_WIDTH / 2
    sweep = 360 - 2 * RING_GAP
    points = [polar(RING_CENTER, RING_CENTER, outer, -RING_GAP - sweep * i / 120) for i in range(121)]
    points += [polar(RING_CENTER, RING_CENTER, inner, -RING_GAP - sweep * (120 - i) / 120) for i in range(121)]
    return points


def ring_caps():
    return [polar(RING_CENTER, RING_CENTER, RING_RADIUS, -RING_GAP), polar(RING_CENTER, RING_CENTER, RING_RADIUS, RING_GAP)]


def masks(canvas, scale):
    big = canvas * SUPERSAMPLE
    k = canvas / 1024 * scale * SUPERSAMPLE
    mid = big / 2

    def project(x, y):
        return (x - DESIGN_CENTER) * k + mid, (y - DESIGN_CENTER) * k + mid

    bubble = Image.new("L", (big, big), 0)
    ImageDraw.Draw(bubble).polygon([project(x, y) for x, y in bubble_polygon()], fill=255)
    ring = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(ring)
    draw.polygon([project(x, y) for x, y in ring_polygon()], fill=255)
    cap = RING_WIDTH / 2 * k
    for x, y in ring_caps():
        px, py = project(x, y)
        draw.ellipse((px - cap, py - cap, px + cap, py + cap), fill=255)
    bubble = bubble.resize((canvas, canvas), Image.LANCZOS)
    ring = ring.resize((canvas, canvas), Image.LANCZOS)
    return {"bubble": bubble, "ring": ring, "cutout": ImageChops.subtract(bubble, ring)}


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if t <= b:
            f = (t - a) / (b - a)
            return tuple(round(ca[i] + (cb[i] - ca[i]) * f) for i in range(3))
    return stops[-1][1]


def gradient(canvas, stops, mapper):
    small = Image.new("RGB", (GRID, GRID))
    pixels = small.load()
    for j in range(GRID):
        for i in range(GRID):
            pixels[i, j] = ramp(stops, mapper(i / (GRID - 1), j / (GRID - 1)))
    return small.resize((canvas, canvas), Image.BICUBIC)


def background(canvas):
    base = gradient(canvas, BG_STOPS, lambda u, v: (u + v) / 2)
    glow = Image.new("L", (GRID, GRID))
    pixels = glow.load()
    for j in range(GRID):
        for i in range(GRID):
            d = math.hypot(i / (GRID - 1) - 0.25, j / (GRID - 1) - 0.15) / 0.8
            pixels[i, j] = round(255 * 0.22 * max(0.0, 1 - d))
    glow = glow.resize((canvas, canvas), Image.BICUBIC)
    return Image.composite(Image.new("RGB", (canvas, canvas), WHITE), base, glow)


def ring_fill(canvas, scale):
    k = canvas / 1024 * scale
    x0 = RING_CENTER - RING_RADIUS - RING_WIDTH / 2
    span = 2 * (RING_RADIUS + RING_WIDTH / 2)

    def mapper(u, v):
        x = (u * canvas - canvas / 2) / k + DESIGN_CENTER
        y = (v * canvas - canvas / 2) / k + DESIGN_CENTER
        return ((x - x0) + (y - x0)) / (2 * span)

    return gradient(canvas, C_STOPS, mapper)


def glyph(canvas, scale):
    m = masks(canvas, scale)
    base = Image.new("RGBA", (canvas, canvas), WHITE + (255,))
    base.putalpha(m["bubble"])
    letter = ring_fill(canvas, scale).convert("RGBA")
    letter.putalpha(m["ring"])
    base.alpha_composite(letter)
    return base


def icon(canvas, scale):
    base = background(canvas).convert("RGBA")
    base.alpha_composite(glyph(canvas, scale))
    return base


def silhouette(canvas, scale):
    image = Image.new("RGBA", (canvas, canvas), WHITE + (255,))
    image.putalpha(masks(canvas, scale)["cutout"])
    return image


def rounded(image, radius_ratio):
    size = image.size[0]
    big = Image.new("L", (size * SUPERSAMPLE, size * SUPERSAMPLE), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        (0, 0, size * SUPERSAMPLE - 1, size * SUPERSAMPLE - 1),
        radius=size * SUPERSAMPLE * radius_ratio,
        fill=255,
    )
    clipped = image.copy()
    clipped.putalpha(big.resize((size, size), Image.LANCZOS))
    return clipped


def disc(image):
    size = image.size[0]
    big = Image.new("L", (size * SUPERSAMPLE, size * SUPERSAMPLE), 0)
    ImageDraw.Draw(big).ellipse((0, 0, size * SUPERSAMPLE - 1, size * SUPERSAMPLE - 1), fill=255)
    clipped = image.copy()
    clipped.putalpha(big.resize((size, size), Image.LANCZOS))
    return clipped


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
    master = icon(1024, 1.12).convert("RGB")
    save(master, RES / "icon-only.png")
    save(background(1024), RES / "icon-background.png")
    save(glyph(1024, 0.8), RES / "icon-foreground.png")

    save(master.resize((512, 512), Image.LANCZOS), ROOT / "icon-512.png")
    save(master.resize((192, 192), Image.LANCZOS), ROOT / "icon-192.png")
    save(icon(512, 0.95).convert("RGB"), ROOT / "icon-maskable-512.png")

    splash = Image.new("RGBA", (2732, 2732), BG_DARK + (255,))
    tile = rounded(icon(780, 1.12), 0.2237)
    splash.alpha_composite(tile, ((2732 - 780) // 2, (2732 - 780) // 2))
    save(splash, RES / "splash.png")
    save(splash, RES / "splash-dark.png")

    save(disc(icon(512, 1.12)), ANDROID / "splash_icon.png")

    sizes = {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72, "xxxhdpi": 96}
    for density, size in sizes.items():
        save(silhouette(size, 1024 / 580 * 0.92), ANDROID / "notification-icon" / (density + ".png"))

    chime(ANDROID / "raw" / "kotha_message.wav")


if __name__ == "__main__":
    main()
