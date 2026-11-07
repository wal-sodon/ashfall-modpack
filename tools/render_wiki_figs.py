#!/usr/bin/env python3
"""Render the field manual's diagrams to PNGs for the GitHub wiki (stdlib + a local Chrome).

The GitHub wiki can't show the field manual's live HTML/SVG figures, so tools/gh_wiki.py uses pictures of them from
docs/wiki/figs/<section>-<n>.png: the n-th diagram (a <figure> holding a .fig box) of that section. This cuts each
one out of docs/wiki/ashfall-wiki.html together with the page's stylesheet, in the light theme, has headless Chrome
screenshot it at 2x in a desktop-width window, and crops it to 760 CSS px wide (the width the wiki pictures
always had) with the white below it trimmed.

Usage: python3 -I tools/render_wiki_figs.py [<section>-<n> ...]     e.g. blasters-1 districts-1 districts-2
With no arguments every diagram in the manual is rendered. Needs Google Chrome in /Applications.
"""
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WIKI = ROOT / "docs/wiki/ashfall-wiki.html"
IMAGES = ROOT / "docs/wiki/images.json"
FIGS = ROOT / "docs/wiki/figs"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
WIDTH = 760
# a desktop-width window, so the page's narrow-screen rules (which re-flow some diagrams) don't apply
VIEWPORT = 1280
SCALE = 2

sys.path.insert(0, str(ROOT / "tools" / "textures"))
sys.path.insert(0, str(ROOT / "tools" / "models"))
from sprite import Sprite  # noqa: E402
from preview import read_png  # noqa: E402


def diagrams(page):
    """{"<section>-<n>": figure html} for every figure that holds a .fig box, numbered per section like gh_wiki."""
    out = {}
    for sec in re.finditer(r'<section id="([^"]+)">(.*?)</section>', page, re.S):
        n = 0
        for fig in re.finditer(r"<figure>(.*?)</figure>", sec.group(2), re.S):
            if 'class="fig"' not in fig.group(1):
                continue
            n += 1
            body = re.sub(r"<figcaption>.*?</figcaption>", "", fig.group(1), flags=re.S)
            out["%s-%d" % (sec.group(1), n)] = body
    return out


def standalone(page, body, imap):
    head = re.search(r"<head>(.*?)</head>", page, re.S)
    head = head.group(1) if head else "".join(re.findall(r"<link[^>]*>|<style>.*?</style>", page, re.S))
    # local images by absolute file URL, so the screenshot finds them
    def src(m):
        path = imap.get(m.group(1))
        return 'src="%s"' % ((ROOT / path).as_uri() if path else m.group(1))
    body = re.sub(r'src="(img/[^"]+)"', src, body)
    return ('<!doctype html><html data-theme="light"><head><meta charset="utf-8">%s'
			'<style>html,body{background:#fff !important;margin:0}body{padding:12px;width:%dpx;box-sizing:border-box}'
			'figure{max-width:none}.flow{flex-wrap:nowrap}.flow .st{flex:none}</style></head><body><figure>%s</figure></body></html>') % (head, WIDTH, body)


def trim(png):
    """Crops the screenshot to the figure's 760 px column plus a 12 px white margin below it."""
    w, h, rows = read_png(png)
    w = min(w, WIDTH * SCALE)
    last = max((y for y in range(h) if any(px[:3] != (255, 255, 255) for px in rows[y][:w])), default=h - 1)
    keep = min(h, last + 1 + 12 * SCALE)
    out = Sprite(w, keep)
    out.px = [list(r[:w]) for r in rows[:keep]]
    png.write_bytes(out.png())
    return w, keep


def main():
    page = WIKI.read_text()
    imap = json.loads(IMAGES.read_text())
    figs = diagrams(page)
    wanted = sys.argv[1:] or sorted(figs)
    unknown = [n for n in wanted if n not in figs]
    if unknown:
        raise SystemExit("no such diagram: %s (have: %s)" % (", ".join(unknown), ", ".join(sorted(figs))))
    FIGS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name in wanted:
            doc = Path(tmp) / (name + ".html")
            doc.write_text(standalone(page, figs[name], imap))
            out = FIGS / (name + ".png")
            subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
							"--user-data-dir=" + str(Path(tmp) / "profile"), "--force-device-scale-factor=%d" % SCALE,
							"--window-size=%d,1400" % VIEWPORT, "--virtual-time-budget=4000", "--screenshot=" + str(out),
							doc.as_uri()], check=True, capture_output=True, timeout=120)
            w, h = trim(out)
            print("wrote %s (%dx%d)" % (out.relative_to(ROOT), w, h))


if __name__ == "__main__":
    main()
