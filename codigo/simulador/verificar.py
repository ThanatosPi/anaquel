# -*- coding: utf-8 -*-
"""Verifica el simulador de punta a punta contra PostgreSQL de verdad.

Que un script corra no prueba nada; lo que prueba es que la base acepte lo que
debe y rechace lo que no — y que rechace POR LA RAZÓN CORRECTA. Esto hace:

  1. el esquema carga limpio;
  2. `db/datos_prueba.sql` produce EXACTAMENTE los errores esperados, en orden,
     y cada uno por la restricción que dice su comentario;
  3. los comercios sintéticos cargan limpio;
  4. cada escenario es representable, y los que no deben producir fila no la
     producen;
  5. los escenarios de tipo `rechazo_esquema` SÍ son rechazados por la base;
  6. la historia de E17 está completa en la base (MSG-005);
  7. la ubicación del comercio resuelve a la zona que declara (BIT-014);
  8. el JSON cumple el contrato, y dos JSON deliberadamente malos NO lo cumplen.

Uso:
    python verificar.py                    (usa la base anaquel_sim)
    python verificar.py --base otra_base

Variables de entorno: PGPASSWORD, PGUSER (por omisión postgres), PGBIN.
"""

import json
import os
import subprocess
import sys

from comercio_sintetico import construir, todos_los_productos, UNIDADES
from contrato_corrida import validar, validar_catalogo
from escenarios import ESCENARIOS
import generar

OUT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(OUT)
PGBIN = os.environ.get("PGBIN", r"C:\Program Files\PostgreSQL\16\bin")
PGUSER = os.environ.get("PGUSER", "postgres")

# Lo que cada bloque B de datos_prueba.sql tiene que decir al fallar, en orden.
# Un bloque que truena por otra razón no prueba su invariante: por eso no basta
# con contar errores.
# Esquema v2 (BIT-021, BIT-022): de B1 a B23 son los mismos casos de la v1 con
# la restricción que hoy los detiene; de B24 en adelante, los disparadores y
# columnas nuevas. db/V2_LEEME.md dice cuál se movió y por qué.
RECHAZOS_ESPERADOS = [
    ("B1",  "recomendacion_precio_entre_limites"),
    ("B2",  "recomendacion_precio_entre_limites"),
    ("B3",  "recomendacion_precio_entre_limites"),
    ("B4",  "conciliacion_id"),
    ("B5",  "recomendacion_retener_con_fecha"),
    ("B6",  "exigir_autorizacion_aprobada"),
    ("B7",  "recomendacion_rechazo_con_motivo"),
    ("B8",  "fuente_tipo_valido"),
    ("B9",  "producto_estacional_con_temporada"),
    ("B10", "conciliacion_foto_confirmada"),
    ("B11", "recomendacion_accion_valida"),
    ("B12", "categoria_afinidad_con_zona"),
    ("B13", "comercio_zona_gps_con_coordenadas"),
    ("B14", "producto_mismo_comercio"),
    ("B15", "recomendacion_conciliacion_del_producto"),
    ("B16", "recomendacion_mismo_comercio"),
    ("B17", "exigir_firma_del_dueno"),
    ("B18", "exigir_firma_del_dueno"),
    ("B19", "recomendacion_referencia_del_producto"),
    ("B20", "temporada_dia_inicio_valido"),
    ("B21", "recomendacion_anterior_del_producto"),
    ("B22", "detalle_venta_mismo_comercio"),
    ("B23", "producto_unidad_valida"),
    ("B24", "detalle_factura_mismo_comercio"),
    ("B25", "envio_mismo_comercio"),
    ("B26", "usuario_firmante_inmutable"),
    ("B27", "usuario_firmante_inmutable"),
    ("B28", "comercio_inmutable"),
    ("B29", "decision_inmutable"),
    ("B30", "exigir_autorizacion_aprobada"),
    ("B31", "campana_mismo_comercio"),
    ("B32", "conciliacion_mismo_comercio"),
    ("B33", "factura_mismo_comercio"),
    ("B34", "recomendacion_precio_autorizado_si_aprobada"),
    ("B35", "recomendacion_precio_autorizado_entre_limites"),
    ("B36", "recomendacion_acepta_riesgo"),
    ("B37", "usuario_comercio_segun_rol"),
    ("B38", "ux_usuario_llm_por_comercio"),
    ("B39", "comercio_manual_sin_coordenadas"),
    ("B40", "comercio_gps_con_municipio"),
    ("B41", "venta_folio_unico"),
    ("B42", "detalle_venta_pkey"),
    ("B43", "detalle_factura_proveedor_pkey"),
    ("B44", "recomendacion_decision_completa"),
    ("B45", "producto_stock_valido"),
    ("B46", "producto_afinidad_con_zona"),
    ("B47", "campana_resultado_completo"),
]


def _exe(nombre):
    ruta = os.path.join(PGBIN, nombre + ".exe")
    return ruta if os.path.isfile(ruta) else nombre


def _correr(exe, *args):
    return subprocess.run([_exe(exe)] + list(args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def _psql(base, *args):
    return _correr("psql", "-U", PGUSER, "-d", base, *args)


def _valor(base, sql):
    res = _psql(base, "-t", "-A", "-v", "ON_ERROR_STOP=1", "-c", sql)
    _exigir(res.returncode == 0, "falló la consulta de verificación:\n%s\n%s" % (sql, res.stderr))
    return res.stdout.strip()


class Falla(Exception):
    pass


def _exigir(condicion, mensaje):
    if not condicion:
        raise Falla(mensaje)


def rehacer_base(base):
    _correr("dropdb", "-U", PGUSER, "--if-exists", base)
    res = _correr("createdb", "-U", PGUSER, base)
    _exigir(res.returncode == 0, "no se pudo crear la base %s: %s" % (base, res.stderr.strip()))


def cargar(base, ruta, detener=True):
    return _psql(base, "-v", "ON_ERROR_STOP=1" if detener else "ON_ERROR_STOP=0",
                 "-q", "-f", ruta)


def paso_1_esquema(base):
    rehacer_base(base)
    res = cargar(base, os.path.join(REPO, "db", "schema.sql"))
    _exigir(res.returncode == 0, "el esquema no carga:\n" + res.stderr.strip())
    tablas = _valor(base, "SELECT count(*) FROM information_schema.tables "
                          "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")
    _exigir(tablas == "21", "el esquema v2 tiene 21 tablas y la base cargó %s" % tablas)
    disparadores = _valor(base, "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal")
    return "esquema cargado sin errores: %s tablas, %s disparadores" % (tablas, disparadores)


def paso_2_rechazos(base):
    res = cargar(base, os.path.join(REPO, "db", "datos_prueba.sql"), detener=False)
    errores = [linea for linea in (res.stderr + res.stdout).splitlines() if "ERROR:" in linea]
    _exigir(len(errores) == len(RECHAZOS_ESPERADOS),
            "datos_prueba.sql produjo %d errores y se esperaban %d:\n  %s"
            % (len(errores), len(RECHAZOS_ESPERADOS), "\n  ".join(errores)))
    for (bloque, esperado), linea in zip(RECHAZOS_ESPERADOS, errores):
        _exigir(esperado in linea,
                "%s falló, pero no por «%s»:\n  %s" % (bloque, esperado, linea.strip()))
    return "%d rechazos, cada uno por su restricción" % len(errores)


def paso_3_sintetico(base):
    rehacer_base(base)
    res = cargar(base, os.path.join(REPO, "db", "schema.sql"))
    _exigir(res.returncode == 0, "el esquema no carga:\n" + res.stderr.strip())
    ruta = os.path.join(OUT, "salida", "comercio_sintetico.sql")
    _exigir(os.path.isfile(ruta), "falta %s — corre antes: python generar.py --sql" % ruta)
    res = cargar(base, ruta)
    _exigir(res.returncode == 0, "los comercios sintéticos no cargan:\n" + res.stderr.strip())
    return "dos comercios sintéticos cargados"


def paso_4_escenarios(base):
    datos = construir()
    skus = {p["sku"] for p in todos_los_productos(datos)}
    # Desde la v2 las salidas son un CHECK, no una tabla: la base sigue siendo la
    # que dice cuáles existen.
    acciones = _valor(base, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                            "WHERE conname = 'recomendacion_accion_valida'")
    for esc in ESCENARIOS:
        _exigir(esc["sku"] in skus,
                "%s apunta a %s, que no existe en los comercios sintéticos" % (esc["id"], esc["sku"]))
        if esc["salida"]:
            _exigir("'%s'" % esc["salida"] in acciones,
                    "%s espera la salida %s, que no admite recomendacion_accion_valida"
                    % (esc["id"], esc["salida"]))
        if esc["tipo"] == "recomendacion" and esc["salida"] == "LIQUIDAR":
            d = esc["datos"]
            minimo = max(d["piso_economico"], d["piso_comercial"])
            _exigir(abs(minimo - d["precio_minimo"]) < 0.005,
                    "%s: el mínimo declarado (%s) no es el mayor de los pisos (%s)"
                    % (esc["id"], d["precio_minimo"], minimo))
            _exigir(d["precio_minimo"] < d["precio_propuesto"] < d["precio_original"],
                    "%s: %s no está estrictamente entre %s y %s (BIT-003)"
                    % (esc["id"], d["precio_propuesto"], d["precio_minimo"], d["precio_original"]))
            _exigir(abs(d["precio_original"] * (1 - d["escalon"] / 10.0) - d["precio_propuesto"])
                    < 0.005, "%s: %s no es el escalón %s de %s (BIT-011)"
                    % (esc["id"], d["precio_propuesto"], d["escalon"], d["precio_original"]))

        # Lo que no debe producir fila, no la produce.
        if esc["tipo"] in ("sin_salida", "no_evaluado"):
            filas = _valor(base, "SELECT count(*) FROM recomendacion r JOIN producto p "
                                 "USING (producto_id) WHERE p.codigo_sku = '%s'" % esc["sku"])
            _exigir(filas == "0", "%s (%s) no debe tener recomendación y tiene %s"
                    % (esc["id"], esc["sku"], filas))

    esperadas = len([e for e in ESCENARIOS if e["tipo"] == "recomendacion"])
    en_base = _valor(base, "SELECT count(*) FROM recomendacion")
    _exigir(en_base == str(esperadas),
            "la base tiene %s recomendaciones y los escenarios esperan %d" % (en_base, esperadas))
    ajenas = _valor(base, "SELECT count(*) FROM recomendacion r JOIN corrida_nocturna c "
                          "USING (corrida_id) JOIN producto p USING (producto_id) "
                          "WHERE p.comercio_id <> c.comercio_id")
    _exigir(ajenas == "0", "hay %s recomendaciones en corridas de otro comercio" % ajenas)
    return "%d escenarios representables, %d recomendaciones en la base" % (len(ESCENARIOS), esperadas)


def paso_5_rechazos_de_escenario(base):
    """Los escenarios que deben fallar, fallan. Se prueban contra la base real."""
    probados = 0
    for esc in ESCENARIOS:
        if esc["tipo"] != "rechazo_esquema":
            continue
        d = esc["datos"]
        sql = ("INSERT INTO recomendacion (corrida_id, producto_id, conciliacion_id, "
               "accion_recomendada, justificacion, precio_venta_regular, precio_minimo_por_costo, "
               "precio_minimo_por_descuento, precio_propuesto) "
               "SELECT 1, p.producto_id, c.conciliacion_id, '%s', 'prueba de %s', "
               "%s, %s, %s, %s "
               "FROM producto p JOIN conciliacion_inventario c USING (producto_id) "
               "WHERE p.codigo_sku = '%s'"
               % (esc["salida"], esc["id"], d["precio_original"], d["piso_economico"],
                  d["piso_comercial"], d["precio_propuesto"], esc["sku"]))
        res = _psql(base, "-v", "ON_ERROR_STOP=1", "-c", sql)
        _exigir(res.returncode != 0, "%s debía ser rechazado por la base y pasó" % esc["id"])
        _exigir(esc["restriccion"] in (res.stderr + res.stdout),
                "%s falló, pero no por %s:\n%s" % (esc["id"], esc["restriccion"], res.stderr.strip()))
        probados += 1
    return "%d rechazos de escenario confirmados" % probados


def paso_6_historias(base):
    """Las re-propuestas sólo prueban algo si su premisa está en la base (MSG-005),
    y cada una respeta la regla de BIT-016 que le toca."""
    contadas = []
    for esc in ESCENARIOS:
        ant = esc.get("antecedente")
        if not ant:
            continue
        fila = _valor(base, """
            SELECT r1.estado_decision, coalesce(r1.motivo_rechazo, ''),
                   r2.recomendacion_anterior_id = r1.recomendacion_id,
                   r2.escalon_descuento - r1.escalon_descuento
            FROM   recomendacion r1
            JOIN   producto p        ON p.producto_id = r1.producto_id AND p.codigo_sku = '%s'
            JOIN   recomendacion r2  ON r2.producto_id = r1.producto_id AND r2.corrida_id = %d
            WHERE  r1.corrida_id = 1 AND r1.estado_decision <> 'PENDIENTE'
        """ % (esc["sku"], esc["corrida"]))
        _exigir(fila, "%s: falta la decisión previa en la base" % esc["id"])
        decision, motivo, encadenada, salto = fila.split("|")
        _exigir(decision == ant["decision"], "%s: la decisión previa es %s" % (esc["id"], decision))
        _exigir(encadenada == "t", "%s: la re-propuesta no apunta a %s" % (esc["id"], ant["escenario"]))

        if decision == "APROBADA":
            campana = _valor(base, """
                SELECT c.fecha_hora_fin < ca.fecha_hora_inicio, c.unidades_vendidas::int
                FROM   recomendacion r1
                JOIN   producto p           ON p.producto_id = r1.producto_id AND p.codigo_sku = '%s'
                JOIN   campana c            ON c.recomendacion_id = r1.recomendacion_id
                JOIN   corrida_nocturna ca  ON ca.corrida_id = %d
                WHERE  r1.corrida_id = 1 AND c.fecha_hora_calculo_resultado IS NOT NULL
            """ % (esc["sku"], esc["corrida"]))
            _exigir(campana, "%s: falta la campaña o su resultado" % esc["id"])
            termino, vendidas = campana.split("|")
            _exigir(termino == "t", "%s: la campaña no había terminado" % esc["id"])
            _exigir(int(vendidas) < ant["existencia_inicial"],
                    "%s: la campaña vendió toda la existencia" % esc["id"])
            _exigir(salto == "1", "%s: tras campaña sin terminar de vender baja uno, no %s"
                    % (esc["id"], salto))
            contadas.append("%s campaña → +1" % esc["id"])
        else:
            _exigir(motivo.strip(), "%s: rechazo sin motivo" % esc["id"])
            _exigir(salto in ("0", "-1"), "%s: tras un rechazo no se baja; salto %s"
                    % (esc["id"], salto))
            contadas.append("%s rechazo → %s" % (esc["id"], salto))
    _exigir(len(contadas) >= 2, "se esperaban al menos las historias de E17 y E20")
    return " · ".join(contadas)


def paso_7_zona(base):
    com = construir()["comercio"]
    zona = _valor(base, "SELECT zona_climatica FROM zona_por_coordenadas(%s, %s)"
                  % (com["latitud"], com["longitud"]))
    _exigir(zona == com["zona_climatica"],
            "las coordenadas del comercio resuelven a '%s' y declara '%s'"
            % (zona, com["zona_climatica"]))
    fuera = _valor(base, "SELECT count(*) FROM zona_por_coordenadas(15.0, -105.0)")
    _exigir(fuera == "0", "un punto en el Pacífico resolvió a una zona")

    # La app demo resuelve la zona con municipios.json porque todavía no tiene
    # API. Tiene que ser la MISMA tabla que precarga schema.sql y dar lo mismo
    # que la base, o la demo enseña otra regla.
    with open(os.path.join(OUT, "salida", "municipios.json"), encoding="utf-8") as archivo:
        tabla = json.load(archivo)["municipios"]
    en_base = _valor(base, "SELECT count(*) FROM municipio_zona_climatica")
    _exigir(en_base == str(len(tabla)),
            "municipios.json trae %d municipios y la base %s" % (len(tabla), en_base))
    for mun in tabla:
        fila = _valor(base, "SELECT entidad_federativa || '|' || municipio || '|' || zona_climatica "
                            "|| '|' || latitud_centro || '|' || longitud_centro || '|' || radio_km "
                            "|| '|' || fuente_dato FROM municipio_zona_climatica "
                            "WHERE municipio_zona_id = %d" % mun["municipio_zona_id"])
        esperado = "%s|%s|%s|%.6f|%.6f|%.1f|%s" % (
            mun["entidad_federativa"], mun["municipio"], mun["zona_climatica"], mun["latitud_centro"],
            mun["longitud_centro"], mun["radio_km"], mun["fuente_dato"])
        _exigir(fila == esperado, "municipio %d: municipios.json dice\n  %s\ny la base\n  %s"
                % (mun["municipio_zona_id"], esperado, fila))
        resuelto = _valor(base, "SELECT municipio_zona_id || '|' || zona_climatica "
                                "FROM zona_por_coordenadas(%s, %s)"
                          % (mun["latitud_centro"], mun["longitud_centro"]))
        _exigir(resuelto == "%d|%s" % (mun["municipio_zona_id"], mun["zona_climatica"]),
                "el centro de %s resuelve a %s en la base" % (mun["municipio"], resuelto))
    return "coordenadas → %s; fuera de radio → manual; %d municipios iguales a la base" % (
        zona, len(tabla))


def paso_8_contrato():
    corridas = []
    for nombre in ("corrida.json", "corrida_2.json"):
        ruta = os.path.join(OUT, "salida", nombre)
        _exigir(os.path.isfile(ruta), "falta %s — corre antes: python generar.py --json" % ruta)
        with open(ruta, encoding="utf-8") as archivo:
            corrida = json.load(archivo)
        errores = validar(corrida)
        _exigir(not errores, "%s no cumple el contrato:\n  %s" % (nombre, "\n  ".join(errores)))
        corridas.append(corrida)

    # Pruebas negativas: si el validador no atrapa esto, no sirve de nada.
    malo = json.loads(json.dumps(corridas[0]))
    rec = next(r for r in malo["recomendaciones"] if r["salida"] == "LIQUIDAR")
    rec["precio"]["precio_propuesto"] = rec["precio"]["precio_minimo"]
    _exigir(validar(malo), "el validador aceptó un precio igual al mínimo (BIT-003)")

    salto = json.loads(json.dumps(corridas[1]))
    rec = next(r for r in salto["recomendaciones"]
               if (r.get("antecedente") or {}).get("decision") == "APROBADA")
    rec["precio"]["escalon"] = rec["antecedente"]["escalon"] + 2
    _exigir(validar(salto), "el validador aceptó un salto de dos escalones (BIT-011)")

    baja = json.loads(json.dumps(corridas[1]))
    rec = next(r for r in baja["recomendaciones"]
               if (r.get("antecedente") or {}).get("decision") == "RECHAZADA")
    rec["precio"]["escalon"] = rec["antecedente"]["escalon"] + 1
    _exigir(validar(baja), "el validador aceptó bajar de escalón tras un rechazo (BIT-016)")

    # El error que encontró la pantalla de AEGIS (MSG-010): E20 sin referencia
    # vigente y sin declararlo. La versión anterior del oráculo era exactamente esto.
    callada = json.loads(json.dumps(corridas[1]))
    rec = next(r for r in callada["recomendaciones"] if r["escenario"] == "E20")
    rec["precio"]["sin_contraste_de_mercado"] = False
    _exigir(validar(callada), "el validador aceptó una liquidación sin referencia vigente que no "
                              "lo declara (BIT-002)")
    return "dos corridas cumplen; cuatro pruebas negativas atrapadas"


def paso_9_catalogo(base):
    """catalogo.json es el reflejo de producto + conciliación vigente del comercio 1,
    con sus ids: es lo que siembra la base local de la app (BIT-022)."""
    with open(os.path.join(OUT, "salida", "catalogo.json"), encoding="utf-8") as archivo:
        catalogo = json.load(archivo)
    errores = validar_catalogo(catalogo)
    _exigir(not errores, "catalogo.json no cumple:\n  " + "\n  ".join(errores))
    en_base = _valor(base, "SELECT count(*) FROM producto WHERE comercio_id = 1")
    _exigir(en_base == str(len(catalogo["productos"])),
            "catalogo.json trae %d productos y el comercio 1 tiene %s"
            % (len(catalogo["productos"]), en_base))
    for cat in catalogo["categorias"]:
        fila = _valor(base, "SELECT nombre_categoria || '|' || coalesce(afinidad_climatica, '-') || '|' "
                            "|| coalesce(zona_climatica_afinidad, '-') FROM categoria "
                            "WHERE comercio_id = 1 AND categoria_id = %d" % cat["categoria_id"])
        esperado = "%s|%s|%s" % (cat["nombre_categoria"], cat["afinidad_climatica"] or "-",
                                 cat["zona_climatica_afinidad"] or "-")
        _exigir(fila == esperado, "categoría %d: catálogo dice %s y la base %s"
                % (cat["categoria_id"], esperado, fila))
    for prod in catalogo["productos"]:
        fila = _valor(base, """
            SELECT p.producto_id || '|' || p.categoria_id || '|' || coalesce(p.temporada_id::text, '-')
                   || '|' || p.codigo_unidad || '|' || u.permite_decimales || '|' || p.existencia_sistema
                   || '|' || coalesce(v.conciliacion_id::text, '-') || '|'
                   || coalesce(v.existencia_contada::text, '-')
            FROM   producto p
            JOIN   unidad_medida u USING (codigo_unidad)
            LEFT   JOIN v_conciliacion_vigente v USING (producto_id)
            WHERE  p.comercio_id = 1 AND p.codigo_sku = '%s'""" % prod["codigo_sku"])
        conc = prod["conciliacion_vigente"]
        esperado = "%d|%d|%s|%s|%s|%.3f|%s|%s" % (
            prod["producto_id"], prod["categoria_id"],
            prod["temporada_id"] if prod["temporada_id"] is not None else "-",
            prod["codigo_unidad"], "true" if UNIDADES[prod["codigo_unidad"]] else "false",
            prod["existencia_sistema"],
            conc["conciliacion_id"] if conc else "-",
            "%.3f" % conc["existencia_contada"] if conc else "-")
        _exigir(fila == esperado, "%s: catálogo dice %s y la base %s" % (prod["codigo_sku"], esperado, fila))

    # BIT-017. El caso que mostró la app (MSG-012): «2.125 piezas» no es válido.
    malo = json.loads(json.dumps(catalogo))
    pieza = next(p for p in malo["productos"]
                 if not UNIDADES[p["codigo_unidad"]] and p["conciliacion_vigente"] is not None)
    pieza["conciliacion_vigente"]["existencia_contada"] = 2.125
    _exigir(validar_catalogo(malo), "el validador aceptó 2.125 piezas (BIT-017)")
    estacional = json.loads(json.dumps(catalogo))
    prod = next(p for p in estacional["productos"] if p["ciclo_producto"] == "ESTACIONAL")
    prod["temporada_id"] = None
    _exigir(validar_catalogo(estacional), "el validador aceptó un estacional sin temporada")
    metros = [p["codigo_sku"] for p in catalogo["productos"] if UNIDADES[p["codigo_unidad"]]]
    sin = [p["codigo_sku"] for p in catalogo["productos"] if p["conciliacion_vigente"] is None]
    return ("%d productos y %d categorías iguales a la base; sin conciliar: %s; %d por metro; "
            "2.125 piezas y estacional sin temporada atrapados") % (
        len(catalogo["productos"]), len(catalogo["categorias"]), ", ".join(sin), len(metros))


def main(argv):
    # En Windows, cuando la salida va a un pipe o a un archivo, Python usa cp1252
    # y truena con «→» o «·». La consola sí aguanta UTF-8; un pipe no.
    for flujo in (sys.stdout, sys.stderr):
        if hasattr(flujo, "reconfigure"):
            flujo.reconfigure(encoding="utf-8", errors="replace")

    base = "anaquel_sim"
    if "--base" in argv:
        base = argv[argv.index("--base") + 1]

    # Regenerar antes de verificar: verificar un archivo viejo no verifica nada.
    generar.main(["generar.py", "--todo"])

    pasos = [
        ("esquema", lambda: paso_1_esquema(base)),
        ("rechazos del esquema", lambda: paso_2_rechazos(base)),
        ("carga sintética", lambda: paso_3_sintetico(base)),
        ("escenarios", lambda: paso_4_escenarios(base)),
        ("rechazos de escenario", lambda: paso_5_rechazos_de_escenario(base)),
        ("re-propuestas", lambda: paso_6_historias(base)),
        ("zona por ubicación", lambda: paso_7_zona(base)),
        ("contrato del JSON", paso_8_contrato),
        ("catálogo de inventario", lambda: paso_9_catalogo(base)),
    ]

    for nombre, funcion in pasos:
        try:
            detalle = funcion()
        except Falla as falla:
            print("FALLA en %s: %s" % (nombre, falla))
            return 1
        print("  ok · %-24s %s" % (nombre, detalle))

    print("\nSIMULADOR OK: %d escenarios, %d rechazos esperados"
          % (len(ESCENARIOS), len(RECHAZOS_ESPERADOS)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
