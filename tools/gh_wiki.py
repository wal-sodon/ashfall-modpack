"""Convert docs/wiki/ashfall-wiki.html into GitHub wiki Markdown pages (stdlib only).

Usage: python3 -I tools/gh_wiki.py [<wiki.html> <images.json> <project root> <out dir> <figures dir>]
With no arguments it reads docs/wiki/ashfall-wiki.html and docs/wiki/images.json, takes figures from docs/wiki/figs,
and writes into .cache/release/wikiout (copy that over the wiki clone, see docs/releasing.md).
Each <section id> becomes a page named after its sidebar entry; the sidebar becomes _Sidebar.md. Cards become tables,
figures become the PNGs in <figures dir> (rendered by tools/render_wiki_figs.py), item icons are upscaled 4x with nearest neighbour.
"""
import html
import json
import re
import shutil
import struct
import sys
import zlib
from html.parser import HTMLParser
from pathlib import Path

_HERE = Path(__file__).resolve().parent.parent
_ARGS = sys.argv[1:6] or [_HERE / "docs/wiki/ashfall-wiki.html", _HERE / "docs/wiki/images.json", _HERE,
						 _HERE / ".cache/release/wikiout", _HERE / "docs/wiki/figs"]
SRC, IMAGES, ROOT, OUT, FIGS = (Path(a) for a in _ARGS)
VOID = {"img", "br", "input", "meta", "link", "hr"}


class Node:
    def __init__(self, tag, attrs, parent):
        self.tag, self.attrs, self.parent, self.kids = tag, dict(attrs), parent, []

    def cls(self):
        return self.attrs.get("class", "").split()

    def find_all(self, pred):
        for k in self.kids:
            if isinstance(k, Node):
                if pred(k):
                    yield k
                yield from k.find_all(pred)

    def text(self):
        return "".join(k if isinstance(k, str) else k.text() for k in self.kids)


class Tree(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = self.cur = Node("root", {}, None)

    def handle_starttag(self, tag, attrs):
        n = Node(tag, attrs, self.cur)
        self.cur.kids.append(n)
        if tag not in VOID:
            self.cur = n

    def handle_startendtag(self, tag, attrs):
        self.cur.kids.append(Node(tag, attrs, self.cur))

    def handle_endtag(self, tag):
        n = self.cur
        while n is not None and n.tag != tag:
            n = n.parent
        if n is not None and n.parent is not None:
            self.cur = n.parent

    def handle_data(self, data):
        self.cur.kids.append(data)


src = SRC.read_text()
tree = Tree()
tree.feed(src)
nav = next(tree.root.find_all(lambda n: n.tag == "nav"))
pages, groups = {}, []
for li in nav.find_all(lambda n: n.tag == "li"):
    if "group" in li.cls():
        groups.append((li.text().strip(), []))
    else:
        a = next(li.find_all(lambda n: n.tag == "a"))
        sid, title = a.attrs["href"][1:], a.text().strip()
        pages[sid] = "Home" if sid == "home" else re.sub(r"[^A-Za-z0-9]+", "-", title).strip("-")
        groups[-1][1].append((sid, title))
version = next(nav.find_all(lambda n: "ver" in n.cls())).text().strip()
sections = {n.attrs["id"]: n for n in tree.root.find_all(lambda n: n.tag == "section")}


def slug(text):
    s = re.sub(r"[^\w\- ]", "", text.strip().lower())
    return s.replace(" ", "-")


# h3/h4 ids -> (page, heading anchor)
anchors = {}
for sid, sec in sections.items():
    anchors[sid] = (pages[sid], "")
    for h in sec.find_all(lambda n: n.tag in ("h3", "h4") and "id" in n.attrs):
        anchors[h.attrs["id"]] = (pages[sid], slug(h.text()))

used_images = set()


def img_tag(n, size=24):
    src = n.attrs.get("src", "")
    used_images.add(src)
    name = src.split("/")[-1]
    return '<img src="images/%s" width="%d" height="%d" alt="">' % (name, size, size)


def esc(t):
    return re.sub(r"([*_`\\])", r"\\\1", t)


def inline(n, in_table=False):
    out = []
    for k in n.kids:
        if isinstance(k, str):
            t = re.sub(r"\s+", " ", k)
            out.append(esc(t).replace("|", "\\|") if in_table else esc(t))
            continue
        tag, c = k.tag, k.cls()
        if tag in ("strong", "b"):
            inner = inline(k, in_table).strip()
            # keep an icon outside the bold markers
            m = re.match(r'^(<img [^>]+>)\s*(.*)$', inner)
            out.append((m.group(1) + " **" + m.group(2) + "**") if m else ("**" + inner + "**") if inner else "")
        elif tag == "em":
            out.append("*" + inline(k, in_table).strip() + "*")
        elif tag == "code" or "coord" in c:
            t = k.text()
            out.append("`" + t.replace("|", "\\|") + "`" if in_table else "`" + t + "`")
        elif "grid-pat" in c:
            rows = k.text().split("\n")
            out.append("<code>" + "<br>".join(html.escape(r).replace(" ", "&nbsp;") for r in rows) + "</code>")
        elif tag == "img":
            out.append(img_tag(k) + " ")
        elif tag == "br":
            out.append("<br>")
        elif tag == "a":
            href = k.attrs.get("href", "")
            label = inline(k, in_table).strip()
            if href.startswith("#"):
                page, anc = anchors.get(href[1:], (None, ""))
                target = page + ("#" + anc if anc else "") if page else href
                out.append("[%s](%s)" % (label, target))
            else:
                out.append("[%s](%s)" % (label, href))
        else:
            out.append(inline(k, in_table))
    return "".join(out)


def cell(n):
    return re.sub(r"\s+", " ", inline(n, True)).strip() or " "


def table(n):
    rows = list(n.find_all(lambda x: x.tag == "tr"))
    if any("colspan" in c.attrs for r in rows for c in r.kids if isinstance(c, Node)):
        lines = ["<table>"]
        for r in rows:
            cells = [c for c in r.kids if isinstance(c, Node) and c.tag in ("th", "td")]
            lines.append("<tr>" + "".join(
                "<%s%s>%s</%s>" % (c.tag, ' colspan="%s"' % c.attrs["colspan"] if "colspan" in c.attrs else "",
                                   md_inline_to_html(cell(c)), c.tag) for c in cells) + "</tr>")
        lines.append("</table>")
        return "\n".join(lines)
    lines = []
    for i, r in enumerate(rows):
        cells = [c for c in r.kids if isinstance(c, Node) and c.tag in ("th", "td")]
        lines.append("| " + " | ".join(cell(c) for c in cells) + " |")
        if i == 0:
            lines.append("|" + "|".join(" ---: " if "num" in c.cls() else " --- " for c in cells) + "|")
    return "\n".join(lines)


def md_inline_to_html(t):
    t = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", t)
    t = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', t)
    return t.replace("\\|", "|")


def bar(text, mx):
    try:
        v = float(text.rstrip("*"))
    except ValueError:
        return text
    full = round(v / mx * 8)
    return "%s %s" % ("▰" * full + "▱" * (8 - full), text)


def cards(n):
    rows = ["| | Weapon | Damage | Per minute | Heard at (blocks) | Notes | Get |",
            "|---|---|---|---|---|---|---|"]
    for c in n.find_all(lambda x: "card" in x.cls()):
        icon = img_tag(next(c.find_all(lambda x: x.tag == "img")), 32)
        head = next(c.find_all(lambda x: "card-head" in x.cls()))
        name = next(head.find_all(lambda x: x.tag == "b")).text()
        tag = next(head.find_all(lambda x: x.tag == "small")).text()
        vals = {}
        for m in c.find_all(lambda x: "meter" in x.cls()):
            spans = [k for k in m.kids if isinstance(k, Node)]
            vals[spans[0].text()] = spans[-1].text()
        ps = [p for p in c.find_all(lambda x: x.tag == "p")]
        notes = " ".join(cell(p) for p in ps if "get" not in p.cls())
        get = " ".join(cell(p) for p in ps if "get" in p.cls()).replace("**Get:**", "").strip()
        rows.append("| %s | **%s**<br>%s | %s | %s | %s | %s | %s |" % (
            icon, name, tag, bar(vals.get("Damage", "–"), 22), bar(vals.get("Per minute", "–"), 600),
            bar(vals.get("Heard at", "–").replace("silent", "0"), 64) if vals.get("Heard at") != "silent" else "silent",
            notes, get))
    return "\n".join(rows)


def shots(n):
    # Building photos: one image per type with its caption underneath, copied as taken (not upscaled).
    out = []
    for f in n.find_all(lambda x: x.tag == "figure"):
        im = next(f.find_all(lambda x: x.tag == "img"))
        used_images.add(im.attrs["src"])
        cap = next(f.find_all(lambda x: x.tag == "figcaption"))
        out.append('<img src="images/%s" width="427" alt="%s">\n\n%s' % (
            im.attrs["src"].split("/")[-1], html.escape(im.attrs.get("alt", "")), inline(cap).strip()))
    return "\n\n".join(out)


fig_count = {}


def figure(n, sid):
    fig_count[sid] = fig_count.get(sid, 0) + 1
    name = "%s-%d.png" % (sid, fig_count[sid])
    if (FIGS / name).exists():
        used_images.add("fig/" + name)
    cap = next(n.find_all(lambda x: x.tag == "figcaption"), None)
    out = "![%s](images/%s)" % ("Diagram" if not cap else cap.text().strip().split(".")[0], name)
    legend = next(n.find_all(lambda x: "legend" in x.cls()), None)
    if legend:
        out += "\n\n*Key: " + ", ".join(s.text() for s in legend.kids if isinstance(s, Node)) + ".*"
    if cap:
        out += "\n\n*" + cell(cap) + "*"
    return out


def block(n, sid):
    out = []
    for k in n.kids:
        if isinstance(k, str):
            if k.strip():
                out.append(esc(k.strip()))
            continue
        tag, c = k.tag, k.cls()
        if tag in ("h1", "h2"):
            continue
        if tag == "h3":
            out.append("## " + k.text().strip())
        elif tag == "h4":
            out.append("### " + k.text().strip())
        elif tag == "p":
            if "ver" in c:
                continue
            out.append(inline(k).strip())
        elif tag in ("ul", "ol"):
            items = [li for li in k.kids if isinstance(li, Node) and li.tag == "li"]
            out.append("\n".join(("%d." % (i + 1) if tag == "ol" else "-") + " " + re.sub(r"\s+", " ", inline(li)).strip()
                                  for i, li in enumerate(items)))
        elif tag == "figure":
            out.append(figure(k, sid))
        elif "cards" in c:
            out.append(cards(k))
        elif "shots" in c:
            out.append(shots(k))
        elif "note" in c:
            paras = [inline(p).strip() for p in k.kids if isinstance(p, Node)]
            out.append("\n>\n".join("> " + p for p in paras))
        elif "table-wrap" in c or tag == "table":
            t = k if tag == "table" else next(k.find_all(lambda x: x.tag == "table"))
            out.append(table(t))
        elif tag in ("header", "div"):
            if "eyebrow" in c:
                out.append("*" + k.text().strip() + "*")
            else:
                out.append(block(k, sid))
        elif tag == "nav" or "pager" in c or "empty" in c:
            continue
        else:
            out.append(inline(k).strip())
    return "\n\n".join(x for x in out if x is not None).replace("\n\n\n", "\n\n")


OUT.mkdir(parents=True, exist_ok=True)
(OUT / "images").mkdir(exist_ok=True)
order = [sid for _, items in groups for sid, _ in items]
for i, sid in enumerate(order):
    body = block(sections[sid], sid)
    body = re.sub(r"\n{3,}", "\n\n", body).strip()
    nav_line = []
    if i > 0:
        nav_line.append("← [%s](%s)" % (dict(t for _, it in groups for t in it)[order[i - 1]], pages[order[i - 1]]))
    if i < len(order) - 1:
        nav_line.append("[%s](%s) →" % (dict(t for _, it in groups for t in it)[order[i + 1]], pages[order[i + 1]]))
    if sid == "home":
        body = body.replace("Use the sidebar or the Previous and Next buttons at the bottom of each page. The search box looks through every page.",
                            "Use the sidebar or the links at the bottom of each page. The wiki's search box looks through every page.")
    (OUT / (pages[sid] + ".md")).write_text(body + "\n\n---\n\n" + " · ".join(nav_line) + "\n")

side = ["**Ashfall field manual**", ""]
for g, items in groups:
    side.append("**%s**" % g)
    side.append("")
    side += ["- [%s](%s)" % (t, pages[s]) for s, t in items]
    side.append("")
(OUT / "_Sidebar.md").write_text("\n".join(side))
(OUT / "_Footer.md").write_text(version + "\n")

# Images: icons upscaled 4x (nearest neighbour) so they stay crisp; figures copied as rendered.
sys.path.insert(0, str(ROOT / "tools" / "models"))
from preview import read_png  # noqa: E402


def write_png(path, w, h, rows):
    raw = b"".join(b"\x00" + bytes(v for px in r for v in px) for r in rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


imap = json.loads(IMAGES.read_text())
missing = []
for src in sorted(used_images):
    if src.startswith("fig/"):
        shutil.copy(FIGS / src[4:], OUT / "images" / src[4:])
        continue
    if src not in imap:
        missing.append(src)
        continue
    f = ROOT / imap[src]
    dst = OUT / "images" / src.split("/")[-1]
    if f.suffix == ".jpg":
        shutil.copy(f, dst)
        continue
    try:
        w, h, rows = read_png(f)
        k = max(1, 64 // max(w, h))
        big = [[px for px in r for _ in range(k)] for r in rows for _ in range(k)]
        write_png(dst, w * k, h * k, big)
    except AssertionError:
        shutil.copy(f, dst)
print("pages:", len(order), "images:", len(list((OUT / "images").iterdir())), "missing:", missing)
