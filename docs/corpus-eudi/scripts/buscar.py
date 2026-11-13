#!/usr/bin/env python3
"""Busca en el indice del corpus EUDI/Colombia con citas trazables.

Uso:
  python3 -I scripts/buscar.py "wallet instance attestation" [-f arf] [-n 8] [--completo]
  python3 -I scripts/buscar.py "revocation" -f norma -d 32024R2979
Fuentes (-f): arf, arf-pdf, ts, norma, etsi, ietf, openid, w3c, hlr, rulebook, codigo, conocimiento
Cada resultado muestra: [fuente] documento > referencia, para citarlo exactamente.
"""
import argparse
import os
import re
import sqlite3
import sys
import textwrap

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(RAIZ, "indice", "eudi.sqlite")


def consulta_fts(q, modo):
    # frase entre comillas se respeta; el resto son terminos (AND o OR)
    frases = re.findall(r'"([^"]+)"', q)
    resto = re.sub(r'"[^"]+"', " ", q)
    terminos = [t for t in re.findall(r"[\w\-\.]+", resto, re.UNICODE) if len(t) > 1]
    partes = [f'"{f}"' for f in frases] + [f'"{t}"' if "-" in t or "." in t else t for t in terminos]
    return (" AND " if modo == "and" else " OR ").join(partes)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("consulta")
    ap.add_argument("-f", "--fuente", help="filtrar por fuente (separadas por coma)")
    ap.add_argument("-d", "--doc", help="filtrar por documento (subcadena)")
    ap.add_argument("-n", type=int, default=8)
    ap.add_argument("--completo", action="store_true", help="mostrar el fragmento entero")
    a = ap.parse_args()

    if not os.path.exists(DB):
        sys.exit("No existe el indice. Ejecuta: python3 -I scripts/construir_indice.py")
    con = sqlite3.connect(DB)
    filtros, params = [], []
    if a.fuente:
        fs = a.fuente.split(",")
        filtros.append("c.fuente IN (%s)" % ",".join("?" * len(fs)))
        params += fs
    if a.doc:
        filtros.append("c.doc LIKE ?")
        params.append(f"%{a.doc}%")
    where = (" AND " + " AND ".join(filtros)) if filtros else ""
    filas = []
    for modo in ("and", "or"):
        q = consulta_fts(a.consulta, modo)
        if not q:
            sys.exit("Consulta vacia")
        sql = (
            "SELECT c.fuente, c.doc, c.ref, c.texto, bm25(fts, 1.0, 0.5, 2.0) AS r "
            "FROM fts JOIN chunks c ON c.id = fts.rowid WHERE fts MATCH ?" + where + " ORDER BY r LIMIT ?"
        )
        try:
            filas = con.execute(sql, [q] + params + [a.n]).fetchall()
        except sqlite3.OperationalError as e:
            sys.exit(f"Consulta invalida ({e}): {q}")
        if filas:
            if modo == "or":
                print("(sin coincidencia exacta de todos los terminos; resultados por coincidencia parcial)\n")
            break
    if not filas:
        print("Sin resultados.")
        return
    terms = [t.strip('"').lower() for t in re.findall(r'"[^"]+"|[\w\-\.]+', a.consulta)]
    for i, (fu, doc, ref, texto, _) in enumerate(filas, 1):
        print(f"{i}. [{fu}] {doc} > {ref}")
        if a.completo:
            cuerpo = texto
        else:
            plano = re.sub(r"\s+", " ", texto)
            pos = min((plano.lower().find(t) for t in terms if t in plano.lower()), default=0)
            ini = max(0, pos - 150)
            cuerpo = ("..." if ini else "") + plano[ini : ini + 520] + ("..." if len(plano) > ini + 520 else "")
        print(textwrap.indent(textwrap.fill(cuerpo, 110) if not a.completo else cuerpo, "   "))
        print()


if __name__ == "__main__":
    main()
