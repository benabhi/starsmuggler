# Turns the Spanish translations of the rule book and the event book (recursos/traduccion) into
# the text assets of the app, plus the event durations as data. Run from the repo root:
#   python tools/content/gen_texts.py
#
# Output:
#   content/src/main/assets/text/es/rules.json   rule sections by id (r201, r207a, ...)
#   content/src/main/assets/text/es/events.json  events by id (e001, ...)
#   content/src/main/assets/content/events.json  event durations, the same in every language
#
# Each text is a list of blocks: heading, paragraph, bullet (with level), table (rows of cells).
# Inline text keeps **bold**; the app finds r###/e### references when it draws the text.
import json
import re
import sys

RULES_MD = "recursos/traduccion/starsmuggler_eh_rules_11/starsmuggler_eh_rules_11.md"
EVENTS_MD = "recursos/traduccion/starsmuggler_eh_events_11/starsmuggler_eh_events_11.md"
TEXT_DIR = "content/src/main/assets/text/es/"
DATA_DIR = "content/src/main/assets/content/"

PAGE_MARK = re.compile(r"^<!--.*-->$")
RULE_SECTION = re.compile(r"^## (r\d{3}) (.+)$")
RULE_SUBSECTION = re.compile(r"^### (.+) \((r\d{3}[a-z])\)$")
EVENT_SECTION = re.compile(r"^## (e\d{3})(?:-e?(\d{3}))? (.+?) \(([^()]*)\)$")
GROUP_ENTRY = re.compile(r"^\*\*(e\d{3}) ([^*]+?):\*\*\s*(.*)$")
EXTRA_SECTIONS = {"Instrucciones": "instructions", "Diagramas de la nave": "ship_diagrams"}


def unescape(text):
    return text.replace("\\*", "*").replace("\\_", "_")


def blocks_of(lines):
    """Markdown lines of one section to blocks."""
    blocks = []
    paragraph = []
    table = []

    def flush():
        if paragraph:
            blocks.append({"type": "paragraph", "text": unescape(" ".join(paragraph))})
            paragraph.clear()
        if table:
            rows = [[unescape(c.strip()) for c in row.strip().strip("|").split("|")] for row in table
                    if not re.match(r"^\|[\s|:-]+\|$", row.strip())]
            block = {"type": "table", "rows": rows}
            # A header like "Código tecnológico del sistema: 10" names the columns that follow it:
            # the name becomes the table caption and the cell keeps only its value.
            for i, cell in enumerate(rows[0][1:], start=1):
                m = re.fullmatch(r"(.{12,}): (.+)", cell)
                if m:
                    block["caption"] = m.group(1)
                    rows[0][i] = m.group(2)
            blocks.append(block)
            table.clear()

    for raw in lines:
        line = raw.rstrip()
        stripped = line.strip()
        if PAGE_MARK.match(stripped) or stripped == "---":
            continue
        if not stripped:
            flush()
            continue
        if stripped.startswith("|"):
            if paragraph:
                flush()
            table.append(stripped)
            continue
        if table:
            flush()
        heading = re.match(r"^#{3,4} (.+)$", stripped)
        bullet = re.match(r"^(\s*)(?:[-*]|\d+\.) (.+)$", line)
        if heading:
            flush()
            blocks.append({"type": "heading", "text": unescape(heading.group(1))})
        elif bullet:
            flush()
            number = re.match(r"^\s*(\d+)\. ", line)
            text = unescape(bullet.group(2))
            if number:
                text = number.group(1) + ". " + text
            blocks.append({"type": "bullet", "level": len(bullet.group(1)) // 2, "text": text})
        elif stripped.startswith(">"):
            flush()
            blocks.append({"type": "paragraph", "text": unescape(stripped.lstrip("> ").strip())})
        elif blocks and blocks[-1]["type"] == "bullet" and line.startswith("  ") and not paragraph:
            blocks[-1]["text"] += " " + unescape(stripped)
        else:
            paragraph.append(stripped)
    flush()
    return blocks


def sections(path):
    """The level-2 sections of a translation, with their lines, up to the translation notes."""
    out = []
    current = None
    for line in open(path, encoding="utf-8").read().splitlines():
        if line.startswith("## "):
            if line.startswith("## Notas de traducción"):
                break
            current = [line, []]
            out.append(current)
        elif current is not None:
            current[1].append(line)
    return out


def rules():
    texts = {}
    for heading, lines in sections(RULES_MD):
        m = RULE_SECTION.match(heading)
        if m:
            rid, title = m.groups()
        elif heading[3:] in EXTRA_SECTIONS:
            rid, title = EXTRA_SECTIONS[heading[3:]], heading[3:]
        else:
            continue
        # Numbered subsections (r207a) become their own texts; other subheadings stay inside.
        chunks = [(rid, title, None, [])]
        for line in lines:
            sub = RULE_SUBSECTION.match(line.strip())
            if sub:
                chunks.append((sub.group(2), sub.group(1), rid, []))
            else:
                chunks[-1][3].append(line)
        for cid, ctitle, parent, clines in chunks:
            entry = {"title": ctitle, "blocks": blocks_of(clines)}
            if parent:
                entry["parent"] = parent
            if cid in texts:
                sys.exit("duplicate rule id " + cid)
            texts[cid] = entry
    for cid, entry in texts.items():
        children = [k for k, v in texts.items() if v.get("parent") == cid]
        if children:
            entry["children"] = children
    return texts


def duration(text):
    t = text.strip()
    fixed = re.fullmatch(r"(?:si no se indica duración, )?toma (\d+) horas?", t)
    if fixed:
        d = {"kind": "hours", "hours": int(fixed.group(1))}
        if t.startswith("si no"):
            d["defaulted"] = True
        return d
    per = re.fullmatch(r"toma (\d+) hora por (ronda|tirada|robo|mina)", t)
    if per:
        unit = {"ronda": "round", "tirada": "roll", "robo": "theft", "mina": "mine"}[per.group(2)]
        return {"kind": "per", "hours": int(per.group(1)), "per": unit}
    simple = {
        "no toma tiempo": {"kind": "hours", "hours": 0},
        "instantáneo": {"kind": "hours", "hours": 0, "instant": True},
        "toma el resto del día": {"kind": "rest_of_day"},
        "toma 1d6 horas": {"kind": "dice", "dice": "1d6"},
        "el tiempo varía": {"kind": "varies"},
        "ver referencia": {"kind": "see_reference"},
        "toma el tiempo normal de viaje": {"kind": "travel"},
        "toma 1 hora si estás presente": {"kind": "hours", "hours": 1, "ifPresent": True},
        "concluye la partida": {"kind": "ends_game"},
    }
    if t in simple:
        return simple[t]
    sys.exit("unknown event duration: " + t)


def events():
    texts = {}
    durations = []
    for heading, lines in sections(EVENTS_MD):
        m = EVENT_SECTION.match(heading)
        if not m:
            continue
        eid, last, title, when = m.groups()
        dur = duration(when)
        if last is None:
            texts[eid] = {"title": title, "duration": when, "blocks": blocks_of(lines)}
            durations.append({"id": eid, "duration": dur})
            continue
        # A block of short events (e026-035): each **eNNN Title:** paragraph is an event, with the
        # block introduction in front of it.
        intro = []
        entries = []
        for line in lines:
            g = GROUP_ENTRY.match(line.strip())
            if g:
                entries.append([g.group(1), g.group(2), [g.group(3)]])
            elif entries:
                entries[-1][2].append(line)
            else:
                intro.append(line)
        for sid, stitle, slines in entries:
            texts[sid] = {"title": stitle, "duration": when, "group": title,
                          "blocks": blocks_of(intro) + blocks_of(slines)}
            durations.append({"id": sid, "duration": dur})
    return texts, durations


def write(path, doc):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")


rule_texts = rules()
event_texts, event_durations = events()
write(TEXT_DIR + "rules.json", {"formatVersion": 1, "texts": rule_texts})
write(TEXT_DIR + "events.json", {"formatVersion": 1, "texts": event_texts})
write(DATA_DIR + "events.json", {"formatVersion": 1, "events": event_durations})
print(len(rule_texts), "rule texts,", len(event_texts), "event texts")
