# -*- coding: utf-8 -*-
"""Genera los artefactos del simulador.

    python generar.py --sql     → salida/comercio_sintetico.sql
    python generar.py --json    → salida/corrida.json y corrida_2.json (lo que consume la app)
    python generar.py --tabla   → salida/escenarios.md
    python generar.py --todo

Los identificadores se asignan por orden de inserción, igual que en
`db/datos_prueba.sql`: las llaves son IDENTITY y no se capturan a mano. Por eso
el orden de los INSERT de aquí abajo NO se cambia sin revisar `_ids()`.
"""

import json
import os
import sys
from datetime import timedelta

from comercio_sintetico import (construir, todos_los_productos, municipio_zona_id, FECHA_CORTE,
                                UNIDADES, AFINIDAD_CATEGORIA, FACTOR_PISO_ECONOMICO,
                                FACTOR_PISO_COMERCIAL)
from escenarios import ESCENARIOS, tabla_markdown

OUT = os.path.dirname(os.path.abspath(__file__))
SALIDA = os.path.join(OUT, "salida")
SEGUNDA_CORRIDA = FECHA_CORTE + timedelta(days=21)


def _txt(valor):
    """Literal SQL: NULL, o texto con las comillas escapadas."""
    if valor is None:
        return "NULL"
    return "'" + str(valor).replace("'", "''") + "'"


def _num(valor):
    return "NULL" if valor is None else str(valor)


def _ts(fecha, hora):
    return _txt("%s %s-06" % (fecha, hora))


def _limpio(texto):
    return " ".join(texto.replace("**", "").split())


# ------------------------------------------------------------ identidades --
def _ids(datos):
    """Resuelve los ids que va a asignar PostgreSQL, en el orden de inserción."""
    ids = {}
    n_cat_1 = len(datos["categorias"])
    ids["categoria"] = {(1, nombre): i for i, nombre in enumerate(datos["categorias"], start=1)}
    ids["categoria"].update({(2, nombre): i for i, nombre in
                             enumerate(datos["categorias_2"], start=n_cat_1 + 1)})
    ids["temporada"] = {t[0]: i for i, t in enumerate(datos["temporadas"], start=1)}

    ids["conciliacion"], siguiente = {}, 1
    for prod in todos_los_productos(datos):
        if prod["existencia"] is not None:
            ids["conciliacion"][prod["sku"]] = siguiente
            siguiente += 1

    ids["referencia"] = {}
    for i, ref in enumerate(datos["referencias"], start=1):
        ids["referencia"].setdefault(ref["sku"], []).append((i, ref))

    ids["recomendacion"] = {}
    for i, (esc, _) in enumerate(_recomendables(datos), start=1):
        ids["recomendacion"][esc["id"]] = i
    return ids


def _recomendables(datos):
    """Escenarios que producen una fila de recomendación, con su producto."""
    pares = []
    for esc in ESCENARIOS:
        if esc["tipo"] != "recomendacion":
            continue
        prod = next(p for p in todos_los_productos(datos) if p["sku"] == esc["sku"])
        pares.append((esc, prod))
    return pares


def _antecedentes():
    """Escenarios cuya premisa es una decisión previa (hoy sólo E17)."""
    return [esc for esc in ESCENARIOS if esc.get("antecedente")]


def _referencias_vigentes(datos, sku, fecha):
    """Referencias del producto que siguen vigentes en la fecha de la corrida."""
    ids = _ids(datos)
    vigentes = []
    for ref_id, ref in ids["referencia"].get(sku, []):
        if FECHA_CORTE + timedelta(days=ref["dias_vigencia"]) > fecha:
            vigentes.append(ref_id)
    return vigentes


# ---------------------------------------------------------------- SQL ------
def generar_sql(datos):
    ids = _ids(datos)
    L = []
    a = L.append

    a("-- =====================================================================")
    a("-- Anaquel · comercios sintéticos generados por simulador/generar.py")
    a("-- Semilla fija, fecha de corte %s. NO editar a mano: se regenera." % FECHA_CORTE)
    a("-- Datos 100 %% sintéticos: ningún comercio identificable entra al repo.")
    a("-- Esquema v2 (BIT-021, BIT-022). Los municipios ya vienen en schema.sql.")
    a("-- =====================================================================")
    a("")
    a("BEGIN;")
    a("")

    a("-- Comercio 1: la ferretería de los escenarios. Comercio 2: la refaccionaria")
    a("-- que el motor NO debe tocar al correr para el comercio 1 (E19, BIT-013).")
    a("INSERT INTO comercio (municipio_zona_id, nombre_comercio, giro_comercial, origen_zona_climatica,")
    a("                      latitud, longitud) VALUES")
    a(",\n".join("    (%d, %s, %s, %s, %s, %s)" % (
        municipio_zona_id(*c["municipio"]), _txt(c["nombre"]), _txt(c["giro"]),
        _txt(c["origen_zona"]), _num(c["latitud"]), _num(c["longitud"]))
        for c in (datos["comercio"], datos["comercio_2"])) + ";")
    a("")

    a("-- La cuenta del agente es el rol LLM: propone y nunca autoriza.")
    a("INSERT INTO usuario (comercio_id, codigo_rol, correo_electronico, nombre_completo, hash_contrasena) VALUES")
    filas = ["    (1, %s, %s, %s, 'no-es-un-hash-real')" % (_txt(r), _txt(c), _txt(n))
             for n, c, r in datos["usuarios"]]
    filas += ["    (2, %s, %s, %s, 'no-es-un-hash-real')" % (_txt(r), _txt(c), _txt(n))
              for n, c, r in datos["usuarios_2"]]
    a(",\n".join(filas) + ";")
    a("")

    com = datos["comercio"]
    a("-- BIT-012. La categoría fija la afinidad climática del caso general, para la")
    a("-- zona del comercio; el producto la sobrescribe en su propia fila.")
    a("INSERT INTO categoria (comercio_id, nombre_categoria, afinidad_climatica, zona_climatica_afinidad) VALUES")
    filas = []
    for nombre in datos["categorias"]:
        nivel = AFINIDAD_CATEGORIA.get(nombre)
        filas.append("    (1, %s, %s, %s)" % (
            _txt(nombre), _txt(nivel), _txt(com["zona_climatica"]) if nivel else "NULL"))
    filas += ["    (2, %s, NULL, NULL)" % _txt(n) for n in datos["categorias_2"]]
    a(",\n".join(filas) + ";")
    a("")

    a("INSERT INTO temporada (comercio_id, nombre_temporada, dia_inicio, mes_inicio, dia_fin, mes_fin) VALUES")
    a(",\n".join("    (1, %s, %d, %d, %d, %d)" % (_txt(t[0]), t[1], t[2], t[3], t[4])
                 for t in datos["temporadas"]) + ";")
    a("")

    a("INSERT INTO producto (comercio_id, codigo_sku, nombre_producto, categoria_id, ciclo_producto,")
    a("                      codigo_unidad, temporada_id, afinidad_climatica_propia,")
    a("                      zona_climatica_afinidad_propia, precio_venta_regular, costo_compra_unitario,")
    a("                      stock_minimo, existencia_sistema, fecha_caducidad) VALUES")
    filas = []
    for prod in todos_los_productos(datos):
        propia = prod.get("afinidad")
        filas.append("    (%d, %s, %s, %d, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)" % (
            prod["comercio_id"], _txt(prod["sku"]), _txt(prod["nombre"]),
            ids["categoria"][(prod["comercio_id"], prod["categoria"])],
            _txt(prod["ciclo"]), _txt(prod["unidad"]),
            _num(ids["temporada"][prod["temporada"]]) if prod["temporada"] else "NULL",
            _txt(propia), _txt(com["zona_climatica"]) if propia else "NULL",
            _num(prod["precio"]), _num(prod["costo"]), _num(prod["reorden"]), _num(prod["teorica"]),
            _txt(prod["fecha_caducidad"]) if prod["fecha_caducidad"] else "NULL"))
    a(",\n".join(filas) + ";")
    a("")

    a("-- Historial de ventas: una venta por renglón, con su fecha real. El renglón")
    a("-- hereda el comercio de la venta; detalle_venta_mismo_comercio lo cuida.")
    venta_id = 0
    ventas_sql, renglones = [], []
    for prod in todos_los_productos(datos):
        for fecha, cantidad, precio in prod["ventas"]:
            venta_id += 1
            ventas_sql.append("    (%d, %s, %s)" % (
                prod["comercio_id"], _txt("SIM-%05d" % venta_id), _ts(fecha, "12:00:00")))
            renglones.append("    (%d, %d, %s, %s)" % (
                venta_id, prod["producto_id"], _num(cantidad), _num(precio)))
    a("INSERT INTO venta (comercio_id, folio_ticket_pos, fecha_hora_venta) VALUES")
    a(",\n".join(ventas_sql) + ";")
    a("")
    a("INSERT INTO detalle_venta (venta_id, producto_id, cantidad_vendida, precio_unitario_cobrado) VALUES")
    a(",\n".join(renglones) + ";")
    a("")

    a("-- Conciliaciones vigentes. SIN-001 no tiene: es el escenario E14.")
    filas = []
    for prod in todos_los_productos(datos):
        if prod["existencia"] is None:
            continue
        confirma = None
        if prod["origen"] in ("FOTO", "MANUAL"):
            confirma = 1 if prod["comercio_id"] == 1 else 3   # el dueño de ESE comercio
        filas.append("    (%d, %d, %s, %s, %s, %s, %s)" % (
            prod["comercio_id"], prod["producto_id"], _num(prod["teorica"]),
            _num(prod["existencia"]), _txt(prod["origen"]), _num(confirma),
            _ts(FECHA_CORTE - timedelta(days=1), "19:00:00")))
    a("INSERT INTO conciliacion_inventario (comercio_id, producto_id, existencia_sistema_al_contar,")
    a("                                     existencia_contada, metodo_conteo, confirmado_por_usuario_id,")
    a("                                     fecha_hora_conteo) VALUES")
    a(",\n".join(filas) + ";")
    a("")

    a("INSERT INTO fuente_precio_publica (nombre_fuente, tipo_fuente, url_base, dias_vigencia) VALUES")
    a(",\n".join("    (%s, %s, %s, %d)" % (_txt(n), _txt(t), _txt(u), v)
                 for n, t, u, v in datos["fuentes"]) + ";")
    a("")

    a("-- Referencias públicas. La de PIN-012 está VENCIDA a propósito (E16).")
    filas = []
    for ref in datos["referencias"]:
        pid = next(p["producto_id"] for p in datos["productos"] if p["sku"] == ref["sku"])
        vigente_hasta = FECHA_CORTE + timedelta(days=ref["dias_vigencia"])
        capturada = vigente_hasta - timedelta(days=abs(ref["dias_vigencia"]) + 1)
        filas.append("    (%d, %d, %s, %s, %s, %s, %s, %s)" % (
            pid, ref["fuente"], _txt("https://ejemplo.invalid/%s" % ref["sku"].lower()),
            _txt(ref["descripcion"]), _num(ref["precio"]), _num(ref["confianza"]),
            _ts(capturada, "03:00:00"), _ts(vigente_hasta, "03:00:00")))
    a("INSERT INTO referencia_precio_mercado (producto_id, fuente_id, url_publicacion, descripcion_publicada,")
    a("                                       precio_publicado, confianza_emparejamiento, fecha_hora_captura,")
    a("                                       fecha_hora_vencimiento) VALUES")
    a(",\n".join(filas) + ";")
    a("")

    evaluados = len([p for p in datos["productos"] if p["existencia"] is not None])
    a("-- Dos corridas del comercio 1: la segunda, tres semanas después, es la de E17.")
    a("INSERT INTO corrida_nocturna (comercio_id, estado_corrida, cantidad_productos_evaluados,")
    a("                              fecha_hora_inicio, fecha_hora_fin) VALUES")
    a("    (1, 'TERMINADA', %d, %s, %s)," % (
        evaluados, _ts(FECHA_CORTE, "03:10:00"), _ts(FECHA_CORTE, "03:40:00")))
    a("    (1, 'TERMINADA', %d, %s, %s);" % (
        evaluados, _ts(SEGUNDA_CORRIDA, "03:10:00"), _ts(SEGUNDA_CORRIDA, "03:40:00")))
    a("")

    a("-- Recomendaciones: la salida esperada de cada escenario, escrita a mano en")
    a("-- escenarios.py. El simulador NO las calcula (BIT-008). Entran PENDIENTES.")
    a("INSERT INTO recomendacion (corrida_id, producto_id, conciliacion_id, accion_recomendada,")
    a("                           justificacion, dias_sin_venta, capital_inmovilizado,")
    a("                           afinidad_climatica_aplicada, precio_venta_regular,")
    a("                           precio_minimo_por_costo, precio_minimo_por_descuento,")
    a("                           precio_propuesto, recuperacion_esperada, escalon_descuento,")
    a("                           sin_contraste_de_mercado, fecha_reactivacion_estimada,")
    a("                           cantidad_a_resurtir, recomendacion_anterior_id) VALUES")
    filas = []
    for esc, prod in _recomendables(datos):
        d = esc["datos"]
        anterior = None
        if esc.get("antecedente"):
            anterior = ids["recomendacion"][esc["antecedente"]["escenario"]]
        filas.append("    (%d, %d, %d, %s, %s, %d, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)" % (
            esc.get("corrida", 1), prod["producto_id"],
            ids["conciliacion"][prod["sku"]],
            _txt(esc["salida"]), _txt(_justificacion(esc)),
            _dias_sin_venta(esc, prod), _num(_capital(prod)), _txt(esc["afinidad"]),
            _num(d.get("precio_original")), _num(d.get("piso_economico")),
            _num(d.get("piso_comercial")), _num(d.get("precio_propuesto")),
            _num(_recuperacion(d, prod)), _num(d.get("escalon")),
            "true" if _sin_contraste(esc) else "false",
            _txt(d.get("fecha_reactivacion")) if d.get("fecha_reactivacion") else "NULL",
            _num(d.get("cantidad_sugerida")), _num(anterior)))
    a(",\n".join(filas) + ";")
    a("")

    a("-- Qué referencias sostuvieron cada precio. Sólo las VIGENTES en la fecha de")
    a("-- la corrida y sólo del mismo producto (BIT-013). E16 no cita ninguna.")
    filas = []
    for esc, prod in _recomendables(datos):
        if esc["salida"] != "LIQUIDAR":
            continue
        for ref_id in _referencias_vigentes(datos, prod["sku"], esc["fecha"]):
            filas.append("    (%d, %d, %d)" % (
                ref_id, ids["recomendacion"][esc["id"]], prod["producto_id"]))
    if filas:
        a("INSERT INTO recomendacion_referencia (referencia_id, recomendacion_id, producto_id) VALUES")
        a(",\n".join(filas) + ";")
        a("")

    a("-- Las decisiones previas de las re-propuestas (BIT-011, BIT-016):")
    a("--   E17 · el dueño aprobó E01, la campaña corrió 14 días y no vendió nada;")
    a("--   E20 · el dueño rechazó E10 con motivo. Sin estas filas, las re-propuestas")
    a("--         no prueban lo que dicen probar (MSG-005). La decisión vive en la")
    a("--         recomendación desde BIT-021; el precio autorizado, desde BIT-022.")
    for esc in _antecedentes():
        ant = esc["antecedente"]
        rec_id = ids["recomendacion"][ant["escenario"]]
        prev = next(e for e in ESCENARIOS if e["id"] == ant["escenario"])
        aprobada = ant["decision"] == "APROBADA"
        a("UPDATE recomendacion")
        a("SET    estado_decision = %s, decidido_por_usuario_id = 1, fecha_hora_decision = %s," % (
            _txt(ant["decision"]), _ts(FECHA_CORTE, "09:00:00")))
        a("       motivo_rechazo = %s, precio_autorizado = %s, acepto_riesgo_sin_contraste = %s" % (
            _txt(ant.get("motivo")),
            _num(prev["datos"]["precio_propuesto"]) if aprobada and prev["salida"] == "LIQUIDAR" else "NULL",
            "true" if aprobada and _sin_contraste(prev) else "false"))
        a("WHERE  recomendacion_id = %d;" % rec_id)
        if aprobada:
            inicia = FECHA_CORTE
            termina = FECHA_CORTE + timedelta(days=ant["campana_dias"])
            a("INSERT INTO campana (comercio_id, recomendacion_id, texto_mensaje, fecha_hora_inicio,")
            a("                     fecha_hora_fin, unidades_vendidas, ingreso_obtenido,")
            a("                     capital_recuperado, fecha_hora_calculo_resultado)")
            a("VALUES (1, %d, %s, %s, %s, %s, 0, 0, %s);" % (
                rec_id, _txt("%s a $%.2f." % (prev["sku"], prev["datos"]["precio_propuesto"])),
                _ts(inicia, "10:00:00"), _ts(termina, "22:00:00"),
                _num(ant["unidades_vendidas"]), _ts(termina, "23:00:00")))
        a("")

    a("COMMIT;")
    a("")
    return "\n".join(L)


def _sin_contraste(esc):
    """BIT-002: sólo una liquidación puede salir sin referencia vigente."""
    return esc["salida"] == "LIQUIDAR" and bool(esc["datos"].get("sin_contraste_de_mercado"))


def _dias_sin_venta(esc, prod):
    """En la segunda corrida el producto lleva más días parado."""
    return prod["dias_ultima_venta"] + (esc["fecha"] - FECHA_CORTE).days


def _capital(prod):
    if prod["costo"] is None or prod["existencia"] is None:
        return None
    return round(prod["costo"] * prod["existencia"], 2)


def _recuperacion(d, prod):
    if d.get("precio_propuesto") is None or prod["existencia"] is None:
        return None
    return round(d["precio_propuesto"] * prod["existencia"], 2)


def _justificacion(esc):
    base = _limpio(esc["porque"])
    if esc["salida"] == "LIQUIDAR" and esc["datos"].get("sin_contraste_de_mercado"):
        base += " Emitida SIN referencia de mercado vigente."
    return base


# --------------------------------------------------------------- JSON ------
def generar_json(datos, corrida=1):
    ids = _ids(datos)
    com = datos["comercio"]
    decisiones = {esc["antecedente"]["escenario"]: esc["antecedente"] for esc in _antecedentes()}
    recomendaciones = []
    fecha_corrida = FECHA_CORTE if corrida == 1 else SEGUNDA_CORRIDA

    for esc, prod in _recomendables(datos):
        if esc.get("corrida", 1) != corrida:
            continue
        d = esc["datos"]
        rec = {
            "recomendacion_id": ids["recomendacion"][esc["id"]],
            "escenario": esc["id"],
            "producto": {
                "sku": prod["sku"],
                "nombre": prod["nombre"],
                "ciclo": prod["ciclo"],
                "categoria": prod["categoria"],
                "unidad": prod["unidad"],
                "admite_fraccion": UNIDADES[prod["unidad"]],
                "precio_original": prod["precio"],
                "costo_unitario": prod["costo"],
                "fecha_caducidad": str(prod["fecha_caducidad"]) if prod["fecha_caducidad"] else None,
            },
            "salida": esc["salida"],
            "justificacion": _justificacion(esc),
            "dias_sin_venta": _dias_sin_venta(esc, prod),
            "capital_detenido": _capital(prod),
            "afinidad_clima": esc["afinidad"],
            "precio": None,
            "retener": None,
            "resurtir": None,
            "abstencion": None,
            "conciliacion": {
                "existencia_fisica": prod["existencia"],
                "diferencia": (prod["teorica"] - prod["existencia"]
                               if prod["existencia"] is not None else None),
                "conciliada_en": str(FECHA_CORTE - timedelta(days=1)),
                "origen": prod["origen"],
            },
            "referencias": _referencias_de(datos, prod, fecha_corrida),
            "antecedente": None,
            "autorizacion": None,
        }

        if esc["salida"] == "LIQUIDAR":
            rec["precio"] = {
                "precio_original": d["precio_original"],
                "piso_economico": d["piso_economico"],
                "piso_comercial": d["piso_comercial"],
                "precio_minimo": d["precio_minimo"],
                "precio_propuesto": d["precio_propuesto"],
                "escalon": d["escalon"],
                "sin_contraste_de_mercado": bool(d.get("sin_contraste_de_mercado")),
            }
        elif esc["salida"] == "RETENER":
            rec["retener"] = {"fecha_reactivacion": d["fecha_reactivacion"]}
        elif esc["salida"] == "RESURTIR":
            rec["resurtir"] = {"cantidad_sugerida": d["cantidad_sugerida"]}
        elif esc["salida"] == "ABSTENERSE":
            rec["abstencion"] = {"motivo": _limpio(d["motivo"])}

        # La decisión que el dueño ya tomó sobre esta recomendación, si existe.
        if esc["id"] in decisiones:
            ant = decisiones[esc["id"]]
            rec["autorizacion"] = {"decision": ant["decision"],
                                   "decidida_en": str(FECHA_CORTE) + "T09:00:00-06:00",
                                   "motivo": ant.get("motivo")}

        # De dónde viene una re-propuesta: la app lo muestra antes que el precio.
        # BIT-016: los campos dependen de la decisión anterior.
        if esc.get("antecedente"):
            ant = esc["antecedente"]
            prev = next(e for e in ESCENARIOS if e["id"] == ant["escenario"])
            aprobada = ant["decision"] == "APROBADA"
            rec["antecedente"] = {
                "recomendacion_id": ids["recomendacion"][ant["escenario"]],
                "escalon": prev["datos"]["escalon"],
                "precio_propuesto": prev["datos"]["precio_propuesto"],
                "decision": ant["decision"],
                "motivo": ant.get("motivo"),
                "campana_termino": (str(FECHA_CORTE + timedelta(days=ant["campana_dias"]))
                                    if aprobada else None),
                "existencia_inicial": ant.get("existencia_inicial") if aprobada else None,
                "unidades_vendidas": ant.get("unidades_vendidas") if aprobada else None,
            }

        recomendaciones.append(rec)

    evaluados = len([p for p in datos["productos"] if p["existencia"] is not None])
    return {
        "corrida_id": corrida,
        "comercio": {
            "nombre": com["nombre"],
            "giro": com["giro"],
            "zona_climatica": com["zona_climatica"],
            "origen_zona": com["origen_zona"],
        },
        "iniciada_en": str(fecha_corrida) + "T03:10:00-06:00",
        "terminada_en": str(fecha_corrida) + "T03:40:00-06:00",
        "productos_evaluados": evaluados,
        "recomendaciones": recomendaciones,
    }


def _referencias_de(datos, prod, fecha):
    fuera = []
    for ref in datos["referencias"]:
        if ref["sku"] != prod["sku"]:
            continue
        vigente_hasta = FECHA_CORTE + timedelta(days=ref["dias_vigencia"])
        capturada = vigente_hasta - timedelta(days=abs(ref["dias_vigencia"]) + 1)
        fuera.append({
            "fuente": datos["fuentes"][ref["fuente"] - 1][0],
            "precio": ref["precio"],
            "capturada_en": str(capturada),
            "vigente_hasta": str(vigente_hasta),
            "vigente": vigente_hasta > fecha,
            "confianza_emparejamiento": ref["confianza"],
        })
    return fuera


def generar_catalogo(datos):
    """Catálogo y existencias del comercio 1, para el inventario de la app.

    AEGIS lo pidió en MSG-008 en vez de inventar 59 productos a partir de las
    recomendaciones. Desde la v2 lleva los nombres de las columnas del esquema
    (BIT-022): es el reflejo de comercio, unidad_medida, categoria, temporada,
    producto y v_conciliacion_vigente, con sus ids, y es lo que siembra la base
    local del teléfono. Un producto sin conciliación (SIN-001) viene con
    conciliacion_vigente nula: es el que la app muestra como «Sin confirmar».
    """
    ids = _ids(datos)
    com = datos["comercio"]
    productos = []
    for prod in datos["productos"]:
        conciliado = prod["existencia"] is not None
        propia = prod.get("afinidad")
        productos.append({
            "producto_id": prod["producto_id"],
            "codigo_sku": prod["sku"],
            "nombre_producto": prod["nombre"],
            "categoria_id": ids["categoria"][(1, prod["categoria"])],
            "temporada_id": ids["temporada"][prod["temporada"]] if prod["temporada"] else None,
            "codigo_unidad": prod["unidad"],
            "ciclo_producto": prod["ciclo"],
            "afinidad_climatica_propia": propia,
            "zona_climatica_afinidad_propia": com["zona_climatica"] if propia else None,
            "precio_venta_regular": prod["precio"],
            "costo_compra_unitario": prod["costo"],
            "stock_minimo": prod["reorden"],
            "stock_maximo": None,
            "existencia_sistema": prod["teorica"],
            "fecha_caducidad": str(prod["fecha_caducidad"]) if prod["fecha_caducidad"] else None,
            "estado_producto": "ACTIVO",
            "conciliacion_vigente": {
                "conciliacion_id": ids["conciliacion"][prod["sku"]],
                "existencia_sistema_al_contar": prod["teorica"],
                "existencia_contada": prod["existencia"],
                "metodo_conteo": prod["origen"],
                "fecha_hora_conteo": str(FECHA_CORTE - timedelta(days=1)) + "T19:00:00-06:00",
            } if conciliado else None,
        })
    return {
        "comercio": {
            "comercio_id": 1,
            "nombre_comercio": com["nombre"],
            "giro_comercial": com["giro"],
            "zona_horaria": "America/Mexico_City",
        },
        "corte": str(FECHA_CORTE),
        "conteo_local": {
            "regla": ("Un conteo capturado en el teléfono es un renglón de conciliacion_inventario "
                      "con metodo_conteo MANUAL, confirmado por el dueño en el acto. Un conteo por "
                      "FOTO sólo existe si el dueño confirma el número (conciliacion_foto_confirmada). "
                      "La existencia contada nunca es negativa, y si la unidad no permite decimales "
                      "(permite_decimales = false, como PIEZA) es un número entero (BIT-017). Un "
                      "conteo nuevo no borra el anterior: es un hecho fechado."),
            "campos": ["producto_id", "existencia_sistema_al_contar", "existencia_contada",
                       "metodo_conteo", "confirmado_por_usuario_id", "fecha_hora_conteo"],
        },
        "unidades": [
            {"codigo_unidad": codigo, "permite_decimales": decimales}
            for codigo, decimales in UNIDADES.items()
        ],
        "categorias": [
            {"categoria_id": ids["categoria"][(1, nombre)], "nombre_categoria": nombre,
             "afinidad_climatica": AFINIDAD_CATEGORIA.get(nombre),
             "zona_climatica_afinidad": com["zona_climatica"] if AFINIDAD_CATEGORIA.get(nombre) else None}
            for nombre in datos["categorias"]
        ],
        "temporadas": [
            {"temporada_id": ids["temporada"][t[0]], "nombre_temporada": t[0],
             "dia_inicio": t[1], "mes_inicio": t[2], "dia_fin": t[3], "mes_fin": t[4]}
            for t in datos["temporadas"]
        ],
        "productos": productos,
    }


def generar_municipios(datos):
    """BIT-014 para la app demo, que todavía no tiene API.

    Es la MISMA tabla que precarga schema.sql (municipio_zona_climatica), con
    sus ids y los nombres de sus columnas, y la misma regla que
    zona_por_coordenadas(): gana el municipio más cercano cuyo radio cubra el
    punto (semiverseno, radio de la Tierra 6371 km); si ninguno lo cubre, no hay
    zona y la app va al selector manual. verificar.py comprueba que el archivo y
    la base tengan los mismos renglones y resuelvan igual.
    """
    return {
        "regla": ("Municipio más cercano cuyo radio_km cubra el punto, por la fórmula del "
                  "semiverseno con radio terrestre de 6371 km. Si ninguno lo cubre: sin zona, "
                  "selector manual. Nunca se adivina."),
        "municipios": [
            {"municipio_zona_id": i, "entidad_federativa": e, "municipio": m, "zona_climatica": z,
             "latitud_centro": la, "longitud_centro": lo, "radio_km": r, "fuente_dato": f}
            for i, (e, m, z, la, lo, r, f) in enumerate(datos["municipios"], start=1)
        ],
    }


# ---------------------------------------------------------------- CLI ------
def _escribir(nombre, contenido):
    if not os.path.isdir(SALIDA):
        os.makedirs(SALIDA)
    ruta = os.path.join(SALIDA, nombre)
    with open(ruta, "w", encoding="utf-8", newline="\n") as archivo:
        archivo.write(contenido)
    print("escrito: %s (%d bytes)" % (ruta, len(contenido.encode("utf-8"))))
    return ruta


def main(argv):
    opciones = set(argv[1:]) or {"--todo"}
    if "--todo" in opciones:
        opciones = {"--sql", "--json", "--tabla"}
    datos = construir()

    if "--sql" in opciones:
        _escribir("comercio_sintetico.sql", generar_sql(datos))
    if "--json" in opciones:
        _escribir("corrida.json",
                  json.dumps(generar_json(datos, 1), ensure_ascii=False, indent=2) + "\n")
        _escribir("corrida_2.json",
                  json.dumps(generar_json(datos, 2), ensure_ascii=False, indent=2) + "\n")
        _escribir("municipios.json",
                  json.dumps(generar_municipios(datos), ensure_ascii=False, indent=2) + "\n")
        _escribir("catalogo.json",
                  json.dumps(generar_catalogo(datos), ensure_ascii=False, indent=2) + "\n")
    if "--tabla" in opciones:
        encabezado = ("# Escenarios del simulador\n\n"
                      "Salidas esperadas escritas a mano. Comercio sintético con fecha de corte "
                      "%s.\nPolítica de pisos del comercio: económico = %.0f %% del costo · "
                      "comercial = %.0f %% del precio original. El escalón es el de BIT-011: "
                      "1 = 10 %% de rebaja, 2 = 20 %%.\n\n"
                      % (FECHA_CORTE, FACTOR_PISO_ECONOMICO * 100, FACTOR_PISO_COMERCIAL * 100))
        _escribir("escenarios.md", encabezado + tabla_markdown() + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
