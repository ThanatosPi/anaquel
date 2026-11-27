# -*- coding: utf-8 -*-
"""Compara db/schema.sql contra la maqueta aprobada y contra la base de la app.

    python db/comparar_esquema.py

1. Cada tabla y cada columna de schema.sql existe en db/maqueta_er_v2.py con el
   mismo tipo, y al revés. Si alguien cambia un nombre en un lado y no en el
   otro, esto falla: la maqueta es lo que aprobó la maestra (V2_LEEME, paso 5).
2. Si la app ya exportó su esquema de Room (app/mobile/schemas/), cada tabla y
   cada columna que guarda el teléfono existe en la v2 con el mismo nombre. La
   app puede guardar MENOS (sólo lo que captura), nunca otra cosa; la única
   excepción es la lista APP_SOLO de abajo, y cada renglón dice por qué.

Sale con código 1 a la primera diferencia; si todo cuadra imprime ESQUEMA OK.
No toca la base de datos: lee los archivos.
"""

import glob
import io
import json
import os
import re
import sys

DB = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(DB)
TIPO = re.compile(r"\b(BIGINT|TEXT|BOOLEAN|DATE|TIMESTAMPTZ|SMALLINT|INTEGER|NUMERIC\(\d+,\d+\))")
NO_COLUMNA = ("CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN", "--", ")")

# Columnas que sólo existen en el teléfono. Se dicen aquí, no se esconden.
APP_SOLO = {
    ("conciliacion_inventario", "clave_local"):
        "idempotencia del borrador del conteo: el mismo guardado dos veces no duplica el renglón",
    ("factura_proveedor", "clave_local"): "idempotencia del guardado de la factura",
    ("venta", "clave_local"): "idempotencia del guardado de la venta",
}


def tablas_del_esquema(texto):
    tablas = {}
    for m in re.finditer(r"CREATE TABLE (\w+) \((.*?)\n\);", texto, re.S):
        columnas = {}
        for linea in m.group(2).splitlines():
            linea = linea.strip()
            if not linea or linea.startswith(NO_COLUMNA) or linea[0] in "('":
                continue
            nombre = linea.split()[0]
            if not re.fullmatch(r"[a-z_]+", nombre):
                continue
            tipo = TIPO.search(linea)
            if tipo is None:
                continue                      # continuación de un CHECK o de una expresión
            columnas[nombre] = tipo.group(1)
        tablas[m.group(1)] = columnas
    return tablas


def tablas_de_la_maqueta():
    """Ejecuta sólo la parte de datos de la maqueta: el resto escribe HTML."""
    fuente = io.open(os.path.join(DB, "maqueta_er_v2.py"), encoding="utf-8").read()
    datos = fuente.split("# ---------------------------------------------------------------- geometría")[0]
    espacio = {"__file__": os.path.join(DB, "maqueta_er_v2.py")}
    exec(compile(datos, "maqueta_er_v2.py", "exec"), espacio)
    return {nombre: {c["name"]: c["typ"] for c in t["cols"]} for nombre, t in espacio["TABLES"].items()}


def esquema_room():
    rutas = sorted(glob.glob(os.path.join(REPO, "app", "mobile", "schemas", "*", "*.json")),
                   key=lambda r: int(os.path.splitext(os.path.basename(r))[0]))
    if not rutas:
        return None, None
    with io.open(rutas[-1], encoding="utf-8") as archivo:
        datos = json.load(archivo)["database"]
    return rutas[-1], {e["tableName"]: [c["columnName"] for c in e["fields"]] for e in datos["entities"]}


def comparar():
    errores = []
    esquema = tablas_del_esquema(io.open(os.path.join(DB, "schema.sql"), encoding="utf-8").read())
    maqueta = tablas_de_la_maqueta()

    for tabla in sorted(set(esquema) | set(maqueta)):
        if tabla not in esquema:
            errores.append("la maqueta tiene la tabla %s y schema.sql no" % tabla)
            continue
        if tabla not in maqueta:
            errores.append("schema.sql tiene la tabla %s y la maqueta no" % tabla)
            continue
        en_sql, en_maq = esquema[tabla], maqueta[tabla]
        for col in sorted(set(en_sql) | set(en_maq)):
            if col not in en_sql:
                errores.append("%s.%s está en la maqueta y no en schema.sql" % (tabla, col))
            elif col not in en_maq:
                errores.append("%s.%s está en schema.sql y no en la maqueta" % (tabla, col))
            elif en_sql[col] != en_maq[col]:
                errores.append("%s.%s es %s en schema.sql y %s en la maqueta"
                               % (tabla, col, en_sql[col], en_maq[col]))

    ruta_room, room = esquema_room()
    if room is not None:
        for tabla, columnas in sorted(room.items()):
            if tabla not in esquema:
                errores.append("la app guarda la tabla %s, que no existe en la v2" % tabla)
                continue
            for col in columnas:
                if col not in esquema[tabla] and (tabla, col) not in APP_SOLO:
                    errores.append("la app guarda %s.%s, que no existe en la v2" % (tabla, col))

    n_cols = sum(len(c) for c in esquema.values())
    if room is None:
        app = "app: todavía sin esquema de Room exportado"
    else:
        extra = sum(1 for t, cs in room.items() for c in cs if (t, c) in APP_SOLO)
        app = "app: %d tablas de Room, todas en la v2 (%d columnas sólo del teléfono, declaradas)" % (
            len(room), extra)
    return errores, "%d tablas, %d columnas iguales a la maqueta · %s" % (len(esquema), n_cols, app)


def main():
    for flujo in (sys.stdout, sys.stderr):
        if hasattr(flujo, "reconfigure"):
            flujo.reconfigure(encoding="utf-8", errors="replace")
    errores, resumen = comparar()
    if errores:
        print("ESQUEMA DISTINTO:\n  " + "\n  ".join(errores))
        return 1
    print("ESQUEMA OK: " + resumen)
    return 0


if __name__ == "__main__":
    sys.exit(main())
