#!/usr/bin/env python3
"""
Generate a GovStack-styled logo for the Form Quality Admin app.

Outputs:
  resources/qa_logo_120.png   — 120x120, used as App Center thumbnail
  resources/qa_favicon_32.png — 32x32, used as browser favicon

Design: rounded square with the GovStack blue gradient (#0F4C75 → #3282B8),
a white "form + checkmark" glyph centred. Mirrors the banner style used in
spProgramMain so the visual identity is consistent across the system.
"""

from PIL import Image, ImageDraw, ImageFilter
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RESOURCE_DIR = os.path.join(HERE, "resources")
os.makedirs(RESOURCE_DIR, exist_ok=True)

# GovStack-aligned palette (matches the in-form banner #0F4C75 → #3282B8)
TOP_LEFT_RGB     = (15, 76, 117)      # #0F4C75
BOTTOM_RIGHT_RGB = (50, 130, 184)     # #3282B8
WHITE            = (255, 255, 255)
SHADOW           = (0, 0, 0, 60)


def make_gradient(size, top_left, bottom_right):
    """Diagonal linear gradient image."""
    base = Image.new("RGB", (size, size), top_left)
    top = Image.new("RGB", (size, size), bottom_right)
    mask = Image.new("L", (size, size))
    px = mask.load()
    for y in range(size):
        for x in range(size):
            # 0..255 along the top-left → bottom-right diagonal
            t = (x + y) / (2 * (size - 1))
            px[x, y] = int(255 * t)
    base.paste(top, (0, 0), mask)
    return base


def round_corners(img, radius):
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def draw_form_with_check(draw, size):
    """Stylised document outline + bold checkmark, centred."""
    cx, cy = size // 2, size // 2

    # Document body — rounded rectangle
    doc_w = int(size * 0.46)
    doc_h = int(size * 0.56)
    doc_x = cx - doc_w // 2 - int(size * 0.04)
    doc_y = cy - doc_h // 2
    radius = int(size * 0.05)

    # Subtle inner shadow band first (just under top of doc)
    draw.rounded_rectangle(
        (doc_x, doc_y, doc_x + doc_w, doc_y + doc_h),
        radius=radius,
        fill=WHITE,
        outline=None,
    )

    # Three "lines of text" inside the doc
    line_x1 = doc_x + int(size * 0.06)
    line_x2 = doc_x + int(size * 0.30)
    line_h = max(2, int(size * 0.018))
    gap = int(size * 0.07)
    first_line_y = doc_y + int(size * 0.12)
    line_color = (15, 76, 117, 220)
    for i in range(3):
        y = first_line_y + i * gap
        draw.rounded_rectangle(
            (line_x1, y, line_x2, y + line_h),
            radius=line_h // 2,
            fill=line_color,
        )

    # Big checkmark badge over bottom-right of document
    badge_r = int(size * 0.20)
    badge_cx = doc_x + doc_w + int(size * 0.02)
    badge_cy = doc_y + doc_h - int(size * 0.05)
    # White rim for contrast
    draw.ellipse(
        (badge_cx - badge_r - 4, badge_cy - badge_r - 4,
         badge_cx + badge_r + 4, badge_cy + badge_r + 4),
        fill=WHITE,
    )
    # Filled accent circle
    draw.ellipse(
        (badge_cx - badge_r, badge_cy - badge_r,
         badge_cx + badge_r, badge_cy + badge_r),
        fill=(34, 167, 116),  # GovStack-aligned green accent
    )
    # The check itself
    cw = int(badge_r * 0.55)
    ch = int(badge_r * 0.65)
    p1 = (badge_cx - cw, badge_cy + int(ch * 0.05))
    p2 = (badge_cx - int(cw * 0.20), badge_cy + int(ch * 0.45))
    p3 = (badge_cx + int(cw * 0.95), badge_cy - int(ch * 0.45))
    stroke = max(4, int(size * 0.038))
    draw.line([p1, p2], fill=WHITE, width=stroke, joint="curve")
    draw.line([p2, p3], fill=WHITE, width=stroke, joint="curve")


def render(size, out_path):
    canvas = make_gradient(size, TOP_LEFT_RGB, BOTTOM_RIGHT_RGB)
    canvas = round_corners(canvas, radius=int(size * 0.20))

    # Draw glyph on a transparent overlay so we get clean compositing
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    if size >= 64:
        draw_form_with_check(draw, size)
    else:
        # 32x32 favicon: simplified — just a check on gradient bg
        cx, cy = size // 2, size // 2
        cw, ch = int(size * 0.30), int(size * 0.30)
        stroke = max(2, int(size * 0.10))
        draw.line([(cx - cw, cy + 1), (cx - 2, cy + ch)], fill=WHITE, width=stroke, joint="curve")
        draw.line([(cx - 2, cy + ch), (cx + cw, cy - ch)], fill=WHITE, width=stroke, joint="curve")

    canvas.alpha_composite(overlay)
    canvas.save(out_path, "PNG", optimize=True)
    print(f"  wrote {out_path}  ({os.path.getsize(out_path)} bytes)")


if __name__ == "__main__":
    render(120, os.path.join(RESOURCE_DIR, "qa_logo_120.png"))
    render(32,  os.path.join(RESOURCE_DIR, "qa_favicon_32.png"))
    print("done.")
