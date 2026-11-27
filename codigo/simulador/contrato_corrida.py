# -*- coding: utf-8 -*-
"""Contrato del JSON que el motor entrega y la aplicación consume.

La forma la manda el esquema: una recomendación del JSON es una fila de
`recomendacion` con sus tablas vecinas ya resueltas. Este módulo existe para
que AEGIS y REACH no diverjan en silencio — si el JSON no pasa por aquí, la
aplicación no lo debería leer.

Uso:
    python contrato_corrida.py --validar salida/corrida.json
"""

import json
import sys

SALIDAS = ("LIQUIDAR", "RETENER", "RESURTIR", "ABSTENERSE")
AFINIDADES = ("ALTA", "MEDIA", "BAJA", "NO_APLICA")
ZONAS = ("TROPICAL", "TEMPLADO", "FRIO", "ARIDO", "SEMIARIDO")
# BIT-017. Mismo catálogo que unidad_medida: si admite fracciones.
UNIDADES = {"PIEZA": False, "METRO": True, "KILO": True, "LITRO": True}

CAMPOS_CORRIDA = ("corrida_id", "comercio", "iniciada_en", "terminada_en",
                  "productos_evaluados", "recomendaciones")
CAMPOS_PRODUCTO = ("sku", "nombre", "ciclo", "categoria", "unidad", "admite_fraccion",
                   "precio_original", "costo_unitario", "fecha_caducidad")
CAMPOS_CONCILIACION = ("existencia_fisica", "diferencia", "conciliada_en", "origen")


def _falta(dic, campos, donde, errores):
    for campo in campos:
        if campo not in dic:
            errores.append("%s: falta el campo '%s'" % (donde, campo))


def validar(corrida):
    """Devuelve una lista de errores. Lista vacía significa contrato cumplido."""
    errores = []
    if not isinstance(corrida, dict):
        return ["la corrida no es un objeto JSON"]

    _falta(corrida, CAMPOS_CORRIDA, "corrida", errores)

    comercio = corrida.get("comercio") or {}
    for campo in ("nombre", "zona_climatica", "origen_zona"):
        if campo not in comercio:
            errores.append("corrida.comercio: falta el campo '%s'" % campo)
    if comercio.get("zona_climatica") not in ZONAS:
        errores.append("corrida.comercio.zona_climatica: '%s' no es una zona válida"
                       % comercio.get("zona_climatica"))
    if comercio.get("origen_zona") not in ("GPS", "MANUAL"):
        errores.append("corrida.comercio.origen_zona: debe ser GPS o MANUAL")

    recomendaciones = corrida.get("recomendaciones")
    if not isinstance(recomendaciones, list):
        errores.append("corrida.recomendaciones: debe ser una lista")
        return errores

    vistos = set()
    for i, rec in enumerate(recomendaciones):
        donde = "recomendaciones[%d]" % i
        if not isinstance(rec, dict):
            errores.append("%s: no es un objeto" % donde)
            continue

        ident = rec.get("recomendacion_id")
        if ident is None:
            errores.append("%s: falta recomendacion_id" % donde)
        elif ident in vistos:
            errores.append("%s: recomendacion_id %s repetido" % (donde, ident))
        else:
            vistos.add(ident)

        producto = rec.get("producto") or {}
        _falta(producto, CAMPOS_PRODUCTO, donde + ".producto", errores)
        if producto.get("ciclo") not in ("PERECEDERO", "ESTACIONAL", "PERMANENTE"):
            errores.append("%s.producto.ciclo: '%s' no es un ciclo válido"
                           % (donde, producto.get("ciclo")))
        errores.extend(_validar_unidad(producto, donde + ".producto"))
        fraccion = UNIDADES.get(producto.get("unidad"), True)
        if not fraccion:
            cantidad = (rec.get("resurtir") or {}).get("cantidad_sugerida")
            fisica = (rec.get("conciliacion") or {}).get("existencia_fisica")
            for nombre, valor in (("resurtir.cantidad_sugerida", cantidad),
                                  ("conciliacion.existencia_fisica", fisica)):
                if not _entero(valor):
                    errores.append("%s.%s: %s no es entero y la unidad %s no admite fracción "
                                   "(BIT-017)" % (donde, nombre, valor, producto.get("unidad")))

        salida = rec.get("salida")
        if salida not in SALIDAS:
            errores.append("%s.salida: '%s' no está en el catálogo %s"
                           % (donde, salida, list(SALIDAS)))

        if not (rec.get("justificacion") or "").strip():
            errores.append("%s.justificacion: no puede ir vacía" % donde)

        if rec.get("afinidad_clima") is not None and rec["afinidad_clima"] not in AFINIDADES:
            errores.append("%s.afinidad_clima: '%s' no es válida" % (donde, rec["afinidad_clima"]))

        conciliacion = rec.get("conciliacion")
        if conciliacion is None:
            # HU-08: no hay recomendación sobre existencias sin conciliar.
            errores.append("%s.conciliacion: obligatoria (HU-08)" % donde)
        else:
            _falta(conciliacion, CAMPOS_CONCILIACION, donde + ".conciliacion", errores)

        precio = rec.get("precio")
        if salida == "LIQUIDAR":
            if not isinstance(precio, dict):
                errores.append("%s.precio: LIQUIDAR tiene que traer el bloque de precio" % donde)
            else:
                errores.extend(_validar_precio(precio, donde))
        else:
            if precio not in (None, {}):
                errores.append("%s.precio: sólo LIQUIDAR lleva precio" % donde)

        if salida == "RETENER":
            if not (rec.get("retener") or {}).get("fecha_reactivacion"):
                errores.append("%s.retener.fecha_reactivacion: RETENER siempre dice cuándo "
                               "vuelve (BIT-001)" % donde)
        if salida == "RESURTIR":
            cantidad = (rec.get("resurtir") or {}).get("cantidad_sugerida")
            if cantidad is None or cantidad <= 0:
                errores.append("%s.resurtir.cantidad_sugerida: debe ser mayor que cero" % donde)
        if salida == "ABSTENERSE":
            if not ((rec.get("abstencion") or {}).get("motivo") or "").strip():
                errores.append("%s.abstencion.motivo: abstenerse sin decir por qué no sirve "
                               "de nada" % donde)

        for j, ref in enumerate(rec.get("referencias") or []):
            for campo in ("fuente", "precio", "capturada_en", "vigente_hasta", "vigente"):
                if campo not in ref:
                    errores.append("%s.referencias[%d]: falta '%s'" % (donde, j, campo))

        # Decisión ya tomada por el dueño (opcional). Mismas reglas que la base.
        precio = rec.get("precio")

        # BIT-002. Una liquidación sin ninguna referencia vigente lo tiene que
        # declarar. Sin esta regla, E20 salió marcado como contrastado con una
        # referencia vencida y sólo lo notó la app (MSG-010).
        if salida == "LIQUIDAR" and isinstance(precio, dict):
            vigentes = [r for r in (rec.get("referencias") or []) if r.get("vigente")]
            if not vigentes and not precio.get("sin_contraste_de_mercado"):
                errores.append("%s.precio.sin_contraste_de_mercado: no hay referencia vigente y "
                               "la propuesta no lo declara (BIT-002)" % donde)

        aut = rec.get("autorizacion")
        if aut is not None:
            if aut.get("decision") not in ("APROBADA", "RECHAZADA"):
                errores.append("%s.autorizacion.decision: debe ser APROBADA o RECHAZADA" % donde)
            if aut.get("decision") == "RECHAZADA" and not (aut.get("motivo") or "").strip():
                errores.append("%s.autorizacion.motivo: un rechazo sin motivo no existe (HU-07)"
                               % donde)

        # Re-propuesta (opcional). BIT-011 precisado por BIT-016: lo que se
        # permite depende de qué pasó con la recomendación anterior.
        ant = rec.get("antecedente")
        if ant is not None:
            errores.extend(_validar_antecedente(ant, rec, precio, salida, donde))

    return errores


def _validar_antecedente(ant, rec, precio, salida, donde):
    """BIT-016. Valida el CONTRATO, no una historia en particular.

    · Campaña aprobada que terminó con existencia sin vender → baja UN escalón.
      «Sin vender la existencia» significa que quedó existencia: unidades
      vendidas < existencia inicial. Vender algunas no impide bajar.
    · Rechazo → nunca baja: mismo escalón o uno arriba, y trae el motivo.
    · En ningún caso salta dos escalones.
    """
    errores = []
    for campo in ("recomendacion_id", "escalon", "precio_propuesto", "decision"):
        if campo not in ant:
            errores.append("%s.antecedente: falta '%s'" % (donde, campo))
    if errores:
        return errores
    if ant["recomendacion_id"] == rec.get("recomendacion_id"):
        errores.append("%s.antecedente: una recomendación no puede venir de sí misma" % donde)

    nuevo = precio.get("escalon") if isinstance(precio, dict) else None
    previo = ant["escalon"]

    if ant["decision"] == "APROBADA":
        for campo in ("campana_termino", "existencia_inicial", "unidades_vendidas"):
            if ant.get(campo) is None:
                errores.append("%s.antecedente: una campaña aprobada trae '%s'" % (donde, campo))
        if errores:
            return errores
        if ant["unidades_vendidas"] >= ant["existencia_inicial"]:
            errores.append("%s.antecedente: la campaña vendió toda la existencia; no hay nada que "
                           "re-proponer (BIT-016)" % donde)
        if salida == "LIQUIDAR" and nuevo is not None and nuevo != previo + 1:
            errores.append("%s: tras una campaña sin terminar de vender se baja UN escalón "
                           "(%s → %s), no %s (BIT-016)" % (donde, previo, previo + 1, nuevo))
    elif ant["decision"] == "RECHAZADA":
        if not (ant.get("motivo") or "").strip():
            errores.append("%s.antecedente: un rechazo sin motivo no existe (HU-07)" % donde)
        if salida == "LIQUIDAR" and nuevo is not None and nuevo not in (previo, previo - 1):
            errores.append("%s: tras un rechazo no se baja de escalón; se queda en %s o sube a %s, "
                           "no %s (BIT-016)" % (donde, previo, max(previo - 1, 1), nuevo))
    else:
        errores.append("%s.antecedente.decision: debe ser APROBADA o RECHAZADA" % donde)
    return errores


def _entero(valor):
    return valor is None or float(valor) == int(float(valor))


def _validar_unidad(producto, donde):
    """BIT-017. La unidad existe y su admite_fraccion es el del catálogo."""
    unidad = producto.get("unidad")
    if unidad not in UNIDADES:
        return ["%s.unidad: '%s' no está en el catálogo %s" % (donde, unidad, list(UNIDADES))]
    if producto.get("admite_fraccion") is not UNIDADES[unidad]:
        return ["%s.admite_fraccion: %s no coincide con la unidad %s"
                % (donde, producto.get("admite_fraccion"), unidad)]
    return []


CICLOS = ("PERECEDERO", "ESTACIONAL", "PERMANENTE")
CAMPOS_CATALOGO_PRODUCTO = (
    "producto_id", "codigo_sku", "nombre_producto", "categoria_id", "temporada_id", "codigo_unidad",
    "ciclo_producto", "afinidad_climatica_propia", "zona_climatica_afinidad_propia",
    "precio_venta_regular", "costo_compra_unitario", "stock_minimo", "stock_maximo",
    "existencia_sistema", "fecha_caducidad", "estado_producto", "conciliacion_vigente")
CAMPOS_CATALOGO_CONCILIACION = ("conciliacion_id", "existencia_sistema_al_contar", "existencia_contada",
                                "metodo_conteo", "fecha_hora_conteo")


def validar_catalogo(catalogo):
    """Catálogo de inventario para la app (catalogo.json), con los nombres de la v2.

    Repite en el JSON las restricciones del esquema que la app necesita al
    sembrar su base: un catálogo que la base rechazaría tampoco se le entrega.
    """
    errores = []
    productos = catalogo.get("productos")
    if not isinstance(productos, list) or not productos:
        return ["catalogo.productos: debe ser una lista con productos"]
    unidades = {u.get("codigo_unidad"): u.get("permite_decimales") for u in catalogo.get("unidades") or []}
    if unidades != UNIDADES:
        errores.append("catalogo.unidades: %s no es el catálogo unidad_medida %s" % (unidades, UNIDADES))
    categorias = {c.get("categoria_id") for c in catalogo.get("categorias") or []}
    temporadas = {t.get("temporada_id") for t in catalogo.get("temporadas") or []}
    for i, cat in enumerate(catalogo.get("categorias") or []):
        if (cat.get("afinidad_climatica") is None) != (cat.get("zona_climatica_afinidad") is None):
            errores.append("catalogo.categorias[%d]: afinidad y zona van juntas (categoria_afinidad_con_zona)" % i)
    skus, ids = set(), set()
    for i, prod in enumerate(productos):
        donde = "catalogo.productos[%d]" % i
        faltan = [campo for campo in CAMPOS_CATALOGO_PRODUCTO if campo not in prod]
        if faltan:
            errores.append("%s: falta %s" % (donde, ", ".join("'%s'" % f for f in faltan)))
            continue
        if prod["codigo_sku"] in skus or prod["producto_id"] in ids:
            errores.append("%s: sku o id repetido (%s, %s)" % (donde, prod["codigo_sku"], prod["producto_id"]))
        skus.add(prod["codigo_sku"])
        ids.add(prod["producto_id"])
        if prod["categoria_id"] not in categorias:
            errores.append("%s: categoria_id %s no está en catalogo.categorias" % (donde, prod["categoria_id"]))
        if prod["temporada_id"] is not None and prod["temporada_id"] not in temporadas:
            errores.append("%s: temporada_id %s no está en catalogo.temporadas" % (donde, prod["temporada_id"]))
        if prod["ciclo_producto"] not in CICLOS:
            errores.append("%s: ciclo '%s' no es válido" % (donde, prod["ciclo_producto"]))
        if prod["ciclo_producto"] == "ESTACIONAL" and prod["temporada_id"] is None:
            errores.append("%s: estacional sin temporada (producto_estacional_con_temporada)" % donde)
        if prod["ciclo_producto"] == "PERECEDERO" and prod["fecha_caducidad"] is None:
            errores.append("%s: perecedero sin caducidad (producto_perecedero_con_caducidad)" % donde)
        if prod["estado_producto"] not in ("ACTIVO", "DESCONTINUADO"):
            errores.append("%s: estado '%s' no es válido" % (donde, prod["estado_producto"]))
        unidad = prod["codigo_unidad"]
        if unidad not in UNIDADES:
            errores.append("%s.codigo_unidad: '%s' no está en el catálogo %s" % (donde, unidad, list(UNIDADES)))
            continue
        conc = prod["conciliacion_vigente"]
        cantidades = [("existencia_sistema", prod["existencia_sistema"])]
        if conc is not None:
            faltan = [campo for campo in CAMPOS_CATALOGO_CONCILIACION if campo not in conc]
            if faltan:
                errores.append("%s.conciliacion_vigente: falta %s" % (donde, ", ".join(faltan)))
                continue
            if conc["metodo_conteo"] not in ("FOTO", "MANUAL", "CARGA"):
                errores.append("%s: método '%s' no es válido" % (donde, conc["metodo_conteo"]))
            cantidades += [("existencia_sistema_al_contar", conc["existencia_sistema_al_contar"]),
                           ("existencia_contada", conc["existencia_contada"])]
        for campo, valor in cantidades:
            if valor is None or valor < 0:
                errores.append("%s.%s: %s no es una existencia válida" % (donde, campo, valor))
            elif not UNIDADES[unidad] and not _entero(valor):
                errores.append("%s.%s: %s piezas no es un número entero (BIT-017)" % (donde, campo, valor))
    return errores


def _validar_precio(precio, donde):
    errores = []
    for campo in ("piso_economico", "piso_comercial", "precio_minimo",
                  "precio_propuesto", "escalon"):
        if campo not in precio:
            errores.append("%s.precio: falta '%s'" % (donde, campo))
    if errores:
        return errores

    piso_eco = precio["piso_economico"]
    piso_com = precio["piso_comercial"]
    minimo = precio["precio_minimo"]
    propuesto = precio["precio_propuesto"]
    original = precio.get("precio_original")

    escalon = precio["escalon"]
    if not isinstance(escalon, int) or not 1 <= escalon <= 9:
        errores.append("%s.precio.escalon: debe ser un entero de 1 a 9 (BIT-011), no %r"
                       % (donde, escalon))

    if round(minimo, 2) != round(max(piso_eco, piso_com), 2):
        errores.append("%s.precio: el mínimo (%s) no es el mayor de los dos pisos (%s, %s)"
                       % (donde, minimo, piso_eco, piso_com))
    if original is None:
        errores.append("%s.precio: falta 'precio_original'" % donde)
        return errores

    # BIT-003, las dos desigualdades ESTRICTAS. Es la prueba que sí se puede
    # automatizar sin conocer la curva de demanda.
    if not propuesto > minimo:
        errores.append("%s.precio: %s no es estrictamente mayor que el mínimo %s (BIT-003)"
                       % (donde, propuesto, minimo))
    if not propuesto < original:
        errores.append("%s.precio: %s no es estrictamente menor que el original %s (BIT-003)"
                       % (donde, propuesto, original))
    return errores


def main(argv):
    if len(argv) != 3 or argv[1] != "--validar":
        print("uso: python contrato_corrida.py --validar <archivo.json>")
        return 2
    with open(argv[2], encoding="utf-8") as archivo:
        corrida = json.load(archivo)
    errores = validar(corrida)
    if errores:
        print("CONTRATO INCUMPLIDO: %d problema(s)" % len(errores))
        for error in errores:
            print("  · " + error)
        return 1
    print("CONTRATO OK: %d recomendaciones" % len(corrida["recomendaciones"]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
