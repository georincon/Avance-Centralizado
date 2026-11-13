#!/usr/bin/env python3
"""Construye indice/eudi.sqlite (SQLite FTS5) con todo el corpus del proyecto.

Uso:  python3 -I scripts/construir_indice.py
Solo biblioteca estandar. Reconstruye el indice desde cero cada vez (tarda ~1 min).
Cada fragmento conserva: fuente (categoria), documento, referencia (articulo/seccion/ID) y texto.
"""
import csv
import os
import re
import sqlite3
import sys
import time

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FUENTES = os.path.join(RAIZ, "fuentes")
DB = os.path.join(RAIZ, "indice", "eudi.sqlite")
VENTANA = 350  # palabras por fragmento


def leer(ruta):
    with open(ruta, encoding="utf-8", errors="replace") as f:
        return f.read()


def ventanas(lineas, etiqueta_fn, minimo=40):
    """Agrupa lineas en fragmentos de ~VENTANA palabras; la etiqueta es la ultima vista."""
    buf, n, etiqueta = [], 0, ""
    for ln in lineas:
        nueva = etiqueta_fn(ln)
        if nueva and nueva != etiqueta and n >= minimo:
            yield etiqueta, "\n".join(buf)
            buf, n = [], 0
        if nueva:
            etiqueta = nueva
        buf.append(ln)
        n += len(ln.split())
        if n >= VENTANA:
            yield etiqueta, "\n".join(buf)
            buf, n = [], 0
    if n >= minimo // 2:
        yield etiqueta, "\n".join(buf)


def por_encabezados_md(texto):
    """Fragmenta Markdown por encabezados (# ...), dividiendo secciones largas."""
    ruta, buf = "", []
    for ln in texto.splitlines():
        m = re.match(r"^(#{1,6})\s+(.*)", ln)
        if m:
            if buf:
                yield ruta, buf
            ruta, buf = m.group(2).strip(), [ln]
        else:
            buf.append(ln)
    if buf:
        yield ruta, buf


def md_fragmentos(texto):
    for titulo, lineas in por_encabezados_md(texto):
        for _, frag in ventanas(lineas, lambda l: None, minimo=20):
            if frag.strip():
                yield titulo, frag


RE_ART_EN = re.compile(r"^\s*(Article \d+[a-z]*|ANNEX [IVXL]+\w*|ANEXO [IVXL]+\w*)\s*$")
RE_ART_ES = re.compile(r"^\s*(Artículo \d+|ANEXO [IVXL]+|CAPÍTULO [IVXL]+)\s*$")
RE_SEC = re.compile(r"^\s{0,6}(\d+(?:\.\d+){0,4})\s+[A-Z][^\n]{3,90}$")


def indexar(con):
    cur = con.cursor()
    cur.execute("DROP TABLE IF EXISTS chunks")
    cur.execute("DROP TABLE IF EXISTS fts")
    cur.execute("DROP TABLE IF EXISTS hlr")
    cur.execute(
        "CREATE TABLE chunks(id INTEGER PRIMARY KEY, fuente TEXT, doc TEXT, ref TEXT, texto TEXT)"
    )
    cur.execute(
        "CREATE VIRTUAL TABLE fts USING fts5(texto, doc, ref, content='chunks', content_rowid='id', "
        "tokenize='unicode61 remove_diacritics 2')"
    )
    total = {}

    def add(fuente, doc, ref, texto):
        if len(texto.split()) < 8:
            return
        cur.execute(
            "INSERT INTO chunks(fuente,doc,ref,texto) VALUES(?,?,?,?)", (fuente, doc, ref, texto.strip())
        )
        total[fuente] = total.get(fuente, 0) + 1

    # --- Markdown: ARF, TS, conocimiento propio, README de referencia
    def md_dir(base, fuente, excluir=()):
        for dp, dn, fn in os.walk(base):
            dn[:] = [d for d in dn if d not in (".git", "node_modules", "media", "img") and d not in excluir]
            for f in sorted(fn):
                p = os.path.join(dp, f)
                rel = os.path.relpath(p, RAIZ)
                if f.endswith(".md"):
                    for titulo, frag in md_fragmentos(leer(p)):
                        add(fuente, rel, titulo, frag)
                elif f.endswith(".txt") and fuente == "conocimiento":
                    lineas = [re.sub(r"[ \t]{2,}", " ", l).strip() for l in leer(p).splitlines() if l.strip()]
                    for _, frag in ventanas(lineas, lambda l: None):
                        add(fuente, rel, "", frag)

    md_dir(os.path.join(FUENTES, "arf-repo", "docs"), "arf")
    md_dir(os.path.join(FUENTES, "standards-repo", "docs"), "ts")
    md_dir(os.path.join(RAIZ, "conocimiento"), "conocimiento")
    for repo in sorted(os.listdir(os.path.join(RAIZ, "referencia"))):
        base = os.path.join(RAIZ, "referencia", repo)
        for f in ("README.md", "README2.md", "CHANGELOG.md"):
            p = os.path.join(base, f)
            if os.path.isfile(p):
                for titulo, frag in md_fragmentos(leer(p)):
                    add("codigo", os.path.relpath(p, RAIZ), titulo, frag)
        docs = os.path.join(base, "docs")
        if os.path.isdir(docs):
            md_dir(docs, "codigo")
        rb = os.path.join(base, "rulebooks")
        if os.path.isdir(rb):
            md_dir(rb, "rulebook")

    # --- Normas UE (texto de Cellar) y eIDAS original en espanol
    for f in sorted(os.listdir(os.path.join(FUENTES, "regulaciones"))):
        if f.endswith(".txt"):
            p = os.path.join(FUENTES, "regulaciones", f)
            lineas = [l for l in leer(p).splitlines() if l.strip()]
            for etq, frag in ventanas(
                lineas, lambda l: (RE_ART_EN.match(l).group(1) if RE_ART_EN.match(l) else None)
            ):
                add("norma", f[:-4], etq or "preambulo", frag)
    p = os.path.join(FUENTES, "eidas1", "eidas1.txt")
    lineas = [re.sub(r"\s{2,}", " ", l).strip() for l in leer(p).splitlines() if l.strip()]
    for etq, frag in ventanas(
        lineas, lambda l: (RE_ART_ES.match(l).group(1) if RE_ART_ES.match(l) else None)
    ):
        add("norma", "eidas-910-2014-es", etq or "preambulo", frag)

    # --- ARF en PDF (version 3.0.0 oficial publicada)
    p = os.path.join(FUENTES, "arf", "arf-v3.0.0.txt")
    lineas = [re.sub(r"\s{2,}", " ", l).strip() for l in leer(p).splitlines() if l.strip()]
    for etq, frag in ventanas(lineas, lambda l: l if re.match(r"^\d+(\.\d+){0,3} [A-Z]", l) and len(l) < 90 else None):
        add("arf-pdf", "arf-v3.0.0.pdf", etq or "", frag)

    # --- Estandares: ETSI, IETF, OpenID, W3C
    est = os.path.join(FUENTES, "estandares")
    for sub, fuente in (("etsi", "etsi"), ("ietf", "ietf"), ("openid", "openid"), ("w3c", "w3c")):
        d = os.path.join(est, sub)
        for f in sorted(os.listdir(d)):
            if not f.endswith(".txt"):
                continue
            texto = leer(os.path.join(d, f))
            if len(texto.split()) < 1000:  # conversion fallida (p. ej. WebAuthn)
                print(f"  omitido (conversion pobre): {sub}/{f}", file=sys.stderr)
                continue
            lineas = [re.sub(r"[ \t]{2,}", " ", l).strip() for l in texto.splitlines() if l.strip()]
            for etq, frag in ventanas(
                lineas, lambda l: l if RE_SEC.match(" " + l) and len(l) < 95 else None
            ):
                add(fuente, f[:-4], etq, frag)

    # --- HLR del ARF (tabla propia + fragmentos buscables)
    cur.execute(
        "CREATE TABLE hlr(id TEXT, armonizado TEXT, parte TEXT, categoria TEXT, topic INTEGER, "
        "topic_titulo TEXT, nivel TEXT, texto TEXT, notas TEXT)"
    )
    p = os.path.join(FUENTES, "hlr-arf-3.0.0.csv")
    with open(p, encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh, delimiter=";"):
            t = r["Requirement_specification"]
            m = re.search(r"\b(SHALL NOT|SHALL|SHOULD NOT|SHOULD|MAY)\b", t)
            nivel = m.group(1) if m else ""
            tn = int(r["Topic_Number"]) if r["Topic_Number"].isdigit() else None
            cur.execute(
                "INSERT INTO hlr VALUES(?,?,?,?,?,?,?,?,?)",
                (r["Index"], r["Harmonized_ID"], r["Part"], r["Category"], tn, r["Topic_Title"], nivel, t, r["Notes"]),
            )
            add("hlr", f"HLR Topic {r['Topic_Number']}", r["Index"], f"{r['Index']} {nivel}: {t} {r['Notes']}")

    cur.execute("INSERT INTO fts(rowid, texto, doc, ref) SELECT id, texto, doc, ref FROM chunks")
    con.commit()
    return total


def main():
    os.makedirs(os.path.dirname(DB), exist_ok=True)
    if os.path.exists(DB):
        os.remove(DB)
    t0 = time.time()
    con = sqlite3.connect(DB)
    total = indexar(con)
    con.close()
    print("Fragmentos por fuente:")
    for k, v in sorted(total.items(), key=lambda x: -x[1]):
        print(f"  {k:14s} {v:7d}")
    print(f"Total {sum(total.values())} fragmentos en {time.time() - t0:.0f}s -> {os.path.relpath(DB, RAIZ)}")


if __name__ == "__main__":
    main()
