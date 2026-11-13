#!/usr/bin/env python3
"""Consulta los 725 requisitos de alto nivel (HLR) del ARF 3.0.0.

Uso:
  python3 -I scripts/hlr.py OIA_01              # un requisito por ID (con sus 'a','b'...)
  python3 -I scripts/hlr.py --topics            # lista de Topics con conteo
  python3 -I scripts/hlr.py --topic 9           # todos los HLR del Topic 9
  python3 -I scripts/hlr.py --actor Relying     # por categoria (subcadena)
  python3 -I scripts/hlr.py --texto revocation --nivel SHALL
  python3 -I scripts/hlr.py --stats
Nivel: SHALL, SHALL NOT, SHOULD, SHOULD NOT, MAY. IDs: prefijo_numero (p. ej. OIA_01, WUA_03, ISSU_12).
Los numeros 'R10..R14' del ARF NO son requisitos: son riesgos del registro de riesgos (cap. 7).
"""
import argparse
import os
import sqlite3
import sys
import textwrap

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(RAIZ, "indice", "eudi.sqlite")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("id", nargs="?")
    ap.add_argument("--topics", action="store_true")
    ap.add_argument("--topic", type=int)
    ap.add_argument("--actor")
    ap.add_argument("--texto")
    ap.add_argument("--nivel")
    ap.add_argument("--stats", action="store_true")
    ap.add_argument("-n", type=int, default=40)
    a = ap.parse_args()
    if not os.path.exists(DB):
        sys.exit("No existe el indice. Ejecuta: python3 -I scripts/construir_indice.py")
    con = sqlite3.connect(DB)

    if a.topics:
        for t, ti, n in con.execute("SELECT topic, topic_titulo, COUNT(*) FROM hlr GROUP BY topic ORDER BY topic"):
            print(f"Topic {t:>3}  {n:>3} HLR  {ti[:95]}")
        return
    if a.stats:
        print("Total:", con.execute("SELECT COUNT(*) FROM hlr").fetchone()[0])
        for k, n in con.execute("SELECT nivel, COUNT(*) FROM hlr GROUP BY nivel ORDER BY 2 DESC"):
            print(f"  {k or '(sin palabra normativa)':26s} {n}")
        for k, n in con.execute("SELECT categoria, COUNT(*) FROM hlr GROUP BY categoria ORDER BY 2 DESC"):
            print(f"  {k:40s} {n}")
        return

    where, p = [], []
    if a.id:
        # ID exacto o variantes con sufijo (OIA_01 -> OIA_01, OIA_01a ...)
        where.append("(id = ? OR id LIKE ?)")
        p += [a.id, a.id + "%"]
    if a.topic is not None:
        where.append("topic = ?")
        p.append(a.topic)
    if a.actor:
        where.append("categoria LIKE ?")
        p.append(f"%{a.actor}%")
    if a.texto:
        where.append("texto LIKE ?")
        p.append(f"%{a.texto}%")
    if a.nivel:
        where.append("nivel = ?")
        p.append(a.nivel.upper())
    if not where:
        ap.print_help()
        return
    filas = con.execute(
        "SELECT id, topic, topic_titulo, categoria, nivel, texto, notas FROM hlr WHERE "
        + " AND ".join(where)
        + " ORDER BY rowid LIMIT ?",
        p + [a.n],
    ).fetchall()
    if not filas:
        print("Sin resultados.")
        return
    for i, t, ti, cat, nv, tx, nt in filas:
        print(f"{i}  [{nv or '-'}]  Topic {t} - {ti[:60]}  ({cat})")
        print(textwrap.indent(textwrap.fill(tx, 108), "   "))
        if nt:
            print(textwrap.indent(textwrap.fill("Nota: " + nt, 108), "   "))
        print()
    if len(filas) == a.n:
        print(f"(mostrando {a.n}; usa -n para ampliar)")


if __name__ == "__main__":
    main()
