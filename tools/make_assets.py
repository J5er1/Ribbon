#!/usr/bin/env python3
"""Generate Ribbon's built assets:

  1. The paper grain tile (§9.2) — per-pixel noise is seamlessly tileable.
  2. The app icon — the Wave (W6) on the unlit ground, the same
     composition as mark/ribbon-mark-on-black-square.svg, rendered from
     the path data below with the knockout done as an erase stroke. (The
     Cesso R. and wordmark masters remain in mark/ for brand use; the
     icon is the mark — docs/deviations.md §1.)
  3. WaveMarkShape.generated.swift — the Wave's two ribbon paths as Swift
     data, so the in-app mark (the way out, onboarding) is drawn natively
     at any size with the knockout done in code.
  4. WaveGeometry.generated.kt — the same two paths as Kotlin data, for
     the Android build's mark. One source of truth: the path data lives
     in this file only, and neither app hand-copies it.
  5. The Android launcher icon — an adaptive icon whose foreground is a
     vector drawable of the same composition, so the icon is resolution
     independent and the knockout is the same erase stroke expressed as
     a ground-coloured stroke drawn between the two fills.
"""

import os
import random
import re

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GROUND = (11, 11, 10)        # #0B0B0A
CHARTREUSE = (214, 228, 92)  # #D6E45C

# The two ribbon paths from mark/ribbon-mark-chartreuse.svg (W6 Wave, 64×64).
# front = drawn last, unmasked; back = masked by a 4.4-wide stroke of front.
FRONT = "M18.77 5.86 Q18.75 5.00 19.61 5.00 L30.64 5.00 Q31.25 5.00 31.26 5.61 L31.26 6.25 L31.33 7.53 L31.44 8.85 L31.60 10.20 L31.81 11.58 L32.06 12.99 L32.36 14.42 L32.68 15.88 L33.05 17.36 L33.44 18.85 L33.86 20.37 L34.30 21.90 L34.76 23.44 L35.24 24.99 L35.73 26.56 L36.22 28.13 L36.72 29.71 L37.21 31.30 L37.71 32.89 L38.19 34.49 L38.65 36.09 L39.10 37.69 L39.53 39.30 L39.93 40.92 L40.30 42.53 L40.63 44.15 L40.91 45.77 L41.16 47.39 L41.34 49.02 L41.47 50.66 L41.53 52.29 L41.51 53.93 L41.42 55.57 L41.23 57.21 L40.95 58.85 L40.75 59.69 Q40.56 60.48 40.19 59.75 L38.24 55.85 Q36.67 52.72 33.76 54.65 L29.82 57.27 Q29.44 57.52 29.54 57.08 L29.64 56.62 L29.78 55.68 L29.87 54.69 L29.91 53.65 L29.89 52.56 L29.83 51.43 L29.71 50.25 L29.55 49.03 L29.34 47.76 L29.08 46.46 L28.78 45.12 L28.44 43.75 L28.07 42.35 L27.66 40.92 L27.23 39.46 L26.77 37.98 L26.28 36.48 L25.79 34.95 L25.27 33.41 L24.75 31.85 L24.23 30.27 L23.70 28.67 L23.18 27.06 L22.66 25.44 L22.16 23.80 L21.67 22.15 L21.20 20.49 L20.76 18.81 L20.35 17.12 L19.97 15.43 L19.64 13.72 L19.35 12.00 L19.11 10.26 L18.92 8.52 L18.80 6.77 Z"
BACK = "M32.74 5.61 Q32.75 5.00 33.36 5.00 L44.40 5.00 Q45.25 5.00 45.23 5.85 L45.20 6.74 L45.09 8.44 L44.92 10.12 L44.69 11.76 L44.41 13.37 L44.09 14.95 L43.73 16.49 L43.34 18.01 L42.92 19.50 L42.48 20.96 L42.02 22.39 L41.54 23.79 L41.06 25.16 L40.57 26.51 L40.08 27.84 L39.59 29.13 L39.11 30.41 L38.64 31.66 L38.19 32.89 L37.76 34.09 L37.34 35.28 L36.96 36.43 L36.60 37.57 L36.27 38.68 L35.98 39.77 L35.72 40.84 L35.50 41.88 L35.33 42.91 L35.19 43.91 L35.10 44.89 L35.06 45.85 L35.06 46.80 L35.10 47.73 L35.20 48.66 L35.35 49.59 L35.46 50.07 Q35.56 50.52 35.17 50.26 L31.24 47.65 Q28.33 45.72 26.76 48.85 L24.80 52.78 Q24.44 53.48 24.26 52.72 L24.06 51.91 L23.77 50.35 L23.57 48.80 L23.45 47.26 L23.42 45.74 L23.45 44.23 L23.56 42.75 L23.72 41.28 L23.93 39.83 L24.20 38.41 L24.50 37.00 L24.84 35.61 L25.21 34.24 L25.61 32.89 L26.03 31.54 L26.46 30.21 L26.90 28.90 L27.36 27.59 L27.81 26.29 L28.27 25.00 L28.72 23.72 L29.16 22.45 L29.59 21.18 L30.01 19.92 L30.41 18.66 L30.79 17.41 L31.14 16.16 L31.46 14.91 L31.76 13.67 L32.02 12.43 L32.24 11.20 L32.43 9.96 L32.58 8.72 L32.68 7.49 L32.74 6.25 Z"
KNOCKOUT_WIDTH = 4.40


def parse_path(d):
    """Parse an M/L/Q/Z path into segments: ('m',x,y) ('l',x,y) ('q',cx,cy,x,y)."""
    tokens = re.findall(r"[MLQZ]|-?[\d.]+", d)
    segs, i = [], 0
    while i < len(tokens):
        cmd = tokens[i]
        if cmd == "M":
            segs.append(("m", float(tokens[i + 1]), float(tokens[i + 2])))
            i += 3
        elif cmd == "L":
            segs.append(("l", float(tokens[i + 1]), float(tokens[i + 2])))
            i += 3
        elif cmd == "Q":
            segs.append(("q", *[float(t) for t in tokens[i + 1:i + 5]]))
            i += 5
        elif cmd == "Z":
            segs.append(("z",))
            i += 1
        else:
            raise ValueError(f"unexpected token {cmd}")
    return segs


def flatten(segs, steps=24):
    """Flatten to a polygon point list."""
    pts = []
    for seg in segs:
        if seg[0] == "m":
            pts.append((seg[1], seg[2]))
        elif seg[0] == "l":
            pts.append((seg[1], seg[2]))
        elif seg[0] == "q":
            x0, y0 = pts[-1]
            cx, cy, x1, y1 = seg[1:]
            for k in range(1, steps + 1):
                t = k / steps
                x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t ** 2 * x1
                y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t ** 2 * y1
                pts.append((x, y))
    return pts


def make_grain():
    rng = random.Random(1121)
    size = 280
    img = Image.new("L", (size, size))
    img.putdata([max(0, min(255, int(rng.gauss(128, 30)))) for _ in range(size * size)])
    out = os.path.join(ROOT, "ios", "Ribbon", "Resources", "PaperGrain.png")
    img.save(out, optimize=True)
    print("wrote", out)


def make_icon():
    """The app icon: the Wave (W6), chartreuse on the unlit ground — the
    mark itself, matching mark/ribbon-mark-on-black-square.svg. No glow,
    no bevel, no gradient. The knockout where the front ribbon crosses the
    back one is an erase stroke, exactly as the SVG's mask does it."""
    big = 4096
    scale = big / 64.0  # the mark's 64×64 design grid
    img = Image.new("RGB", (big, big), GROUND)
    draw = ImageDraw.Draw(img)

    front = [(x * scale, y * scale) for x, y in flatten(parse_path(FRONT))]
    back = [(x * scale, y * scale) for x, y in flatten(parse_path(BACK))]

    # Back ribbon first; then the knockout — occlusion, not transparency:
    # a ground-colored stroke of the front outline; the front ribbon last,
    # unmasked, covering the inner half of its own stroke.
    draw.polygon(back, fill=CHARTREUSE)
    draw.line(front + [front[0]], fill=GROUND,
              width=int(round(KNOCKOUT_WIDTH * scale)), joint="curve")
    draw.polygon(front, fill=CHARTREUSE)

    icon_dir = os.path.join(ROOT, "ios", "Ribbon", "Resources", "Assets.xcassets", "AppIcon.appiconset")
    os.makedirs(icon_dir, exist_ok=True)
    img.resize((1024, 1024), Image.LANCZOS).save(os.path.join(icon_dir, "AppIcon.png"), optimize=True)
    print("wrote", os.path.join(icon_dir, "AppIcon.png"))


def make_swift():
    def emit(name, d):
        lines = [f"    static let {name}: [WaveSegment] = ["]
        for seg in parse_path(d):
            if seg[0] == "m":
                lines.append(f"        .move({seg[1]:.4f}, {seg[2]:.4f}),")
            elif seg[0] == "l":
                lines.append(f"        .line({seg[1]:.4f}, {seg[2]:.4f}),")
            elif seg[0] == "q":
                lines.append(f"        .quad({seg[1]:.4f}, {seg[2]:.4f}, {seg[3]:.4f}, {seg[4]:.4f}),")
            elif seg[0] == "z":
                lines.append("        .close,")
        lines.append("    ]")
        return "\n".join(lines)

    swift = f"""// Generated by tools/make_assets.py from mark/ribbon-mark-chartreuse.svg
// (W6 Wave, 64×64 grid) — do not edit by hand.

import CoreGraphics

enum WaveSegment {{
    case move(CGFloat, CGFloat)
    case line(CGFloat, CGFloat)
    case quad(CGFloat, CGFloat, CGFloat, CGFloat)
    case close
}}

enum WaveGeometry {{
    /// The mark's design grid.
    static let gridSize: CGFloat = 64
    /// Width of the occlusion stroke where the front ribbon crosses the
    /// back one, in grid units. Occlusion, not transparency — one ribbon
    /// passes behind the other with a hard edge.
    static let knockoutWidth: CGFloat = {KNOCKOUT_WIDTH}

{emit("front", FRONT)}

{emit("back", BACK)}
}}
"""
    out = os.path.join(ROOT, "ios", "Ribbon", "DesignSystem", "WaveMarkShape.generated.swift")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        f.write(swift)
    print("wrote", out)


def make_kotlin():
    """The same geometry as make_swift, for Android. Kept in lockstep with
    the Swift emitter above — both read FRONT/BACK from this file, so the
    two apps cannot drift on what the mark is."""
    def emit(name, d):
        lines = [f"    val {name}: List<WaveSegment> = listOf("]
        for seg in parse_path(d):
            if seg[0] == "m":
                lines.append(f"        WaveSegment.Move({seg[1]:.4f}f, {seg[2]:.4f}f),")
            elif seg[0] == "l":
                lines.append(f"        WaveSegment.Line({seg[1]:.4f}f, {seg[2]:.4f}f),")
            elif seg[0] == "q":
                lines.append(
                    f"        WaveSegment.Quad({seg[1]:.4f}f, {seg[2]:.4f}f, "
                    f"{seg[3]:.4f}f, {seg[4]:.4f}f),")
            elif seg[0] == "z":
                lines.append("        WaveSegment.Close,")
        lines.append("    )")
        return "\n".join(lines)

    kotlin = f"""// Generated by tools/make_assets.py from mark/ribbon-mark-chartreuse.svg
// (W6 Wave, 64x64 grid) - do not edit by hand.

package app.readribbon.design

sealed interface WaveSegment {{
    data class Move(val x: Float, val y: Float) : WaveSegment
    data class Line(val x: Float, val y: Float) : WaveSegment
    data class Quad(val cx: Float, val cy: Float, val x: Float, val y: Float) : WaveSegment
    data object Close : WaveSegment
}}

object WaveGeometry {{
    /** The mark's design grid. */
    const val GRID_SIZE: Float = 64f

    /**
     * Width of the occlusion stroke where the front ribbon crosses the back
     * one, in grid units. Occlusion, not transparency - one ribbon passes
     * behind the other with a hard edge.
     */
    const val KNOCKOUT_WIDTH: Float = {KNOCKOUT_WIDTH}f

{emit("front", FRONT)}

{emit("back", BACK)}
}}
"""
    out = os.path.join(ROOT, "android", "app", "src", "main", "kotlin",
                       "app", "readribbon", "design", "WaveGeometry.generated.kt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        f.write(kotlin)
    print("wrote", out)


def make_android_icon():
    """The launcher icon as an adaptive icon (the only kind that matters at
    minSdk 31). The foreground is a vector of the same three-step
    composition the PNG icon uses - back ribbon, ground-coloured knockout
    stroke, front ribbon - so it stays crisp at every density and in the
    themed-icon monochrome slot.

    The 64-unit mark is centred in the 108-unit adaptive viewport. Its
    bounding box is about 27 x 55, a 61-unit diagonal, which sits inside
    the 66-unit safe circle - so no part of the mark can be masked away by
    an aggressive launcher shape."""
    res = os.path.join(ROOT, "android", "app", "src", "main", "res")
    inset = (108 - 64) / 2.0

    def path_d(d):
        # The mark's own SVG path syntax is already valid Android pathData.
        return " ".join(d.split())

    fg = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/make_assets.py from mark/ribbon-mark-chartreuse.svg.
     Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="{inset:g}" android:translateY="{inset:g}">
        <!-- The back ribbon. -->
        <path
            android:pathData="{path_d(BACK)}"
            android:fillColor="#D6E45C" />
        <!-- The knockout: occlusion, not transparency. A ground-coloured
             stroke of the front outline, drawn between the two fills. -->
        <path
            android:pathData="{path_d(FRONT)}"
            android:fillColor="#00000000"
            android:strokeColor="#0B0B0A"
            android:strokeWidth="{KNOCKOUT_WIDTH}"
            android:strokeLineJoin="miter"
            android:strokeMiterLimit="6" />
        <!-- The front ribbon, unmasked, covering the inner half of its
             own knockout stroke. -->
        <path
            android:pathData="{path_d(FRONT)}"
            android:fillColor="#D6E45C" />
    </group>
</vector>
"""
    drawable_dir = os.path.join(res, "drawable")
    os.makedirs(drawable_dir, exist_ok=True)
    with open(os.path.join(drawable_dir, "ic_launcher_foreground.xml"), "w") as f:
        f.write(fg)
    print("wrote", os.path.join(drawable_dir, "ic_launcher_foreground.xml"))

    # The monochrome slot takes the same shape in a single flat colour: a
    # themed icon is tinted by the launcher, so the knockout has to be a
    # real hole rather than a ground-coloured stroke.
    mono = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/make_assets.py. Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="{inset:g}" android:translateY="{inset:g}">
        <path
            android:pathData="{path_d(BACK)}"
            android:fillColor="#FFFFFF" />
        <path
            android:pathData="{path_d(FRONT)}"
            android:fillColor="#FFFFFF" />
    </group>
</vector>
"""
    with open(os.path.join(drawable_dir, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(mono)
    print("wrote", os.path.join(drawable_dir, "ic_launcher_monochrome.xml"))

    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/make_assets.py. Do not edit by hand. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/unlit" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    mip = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(mip, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(mip, name), "w") as f:
            f.write(adaptive)
        print("wrote", os.path.join(mip, name))


if __name__ == "__main__":
    make_grain()
    make_icon()
    make_swift()
    make_kotlin()
    make_android_icon()
