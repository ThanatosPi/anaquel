# -*- coding: utf-8 -*-
"""Los 20 escenarios del simulador, con su salida ESPERADA escrita a mano.

Esto es el oráculo: el motor de AEGIS se prueba contra estas salidas. Ningún
valor de aquí lo calcula un programa — todos están escritos a mano con la
aritmética a la vista, justo para que la prueba no herede el mismo error que el
código que se quiere verificar.

Cada escenario declara el folio que lo justifica. Si un escenario contradice un
folio, el error está en el escenario.

Tipos:
  recomendacion   · el motor debe producir esa salida con esos datos
  sin_salida      · el producto se evalúa y no genera recomendación
  no_evaluado     · el producto ni siquiera entra a la corrida
  rechazo_esquema · la base tiene que rechazar el INSERT (no es salida válida)

`escalon` es el número de escalón de BIT-011, no el porcentaje: 1 = 10 % de
rebaja sobre el precio original, 2 = 20 %, y así. Es lo mismo que guarda la
columna recomendacion.escalon.
"""

from datetime import timedelta

from comercio_sintetico import FECHA_CORTE

# Pisos de los productos que liquidan, calculados con la política del comercio
# sintético (70 % del costo / 30 % del precio original). Se escriben ya
# resueltos porque son parte de lo esperado, no del cálculo.
ESCENARIOS = [
    dict(
        id="E01", titulo="Permanente parado sobre el umbral → LIQUIDAR",
        sku="LLA-014", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="LIQUIDAR", afinidad="MEDIA",
        datos=dict(precio_original=520.00, piso_economico=273.00, piso_comercial=156.00,
                   precio_minimo=273.00, precio_propuesto=468.00, escalon=1),
        folios=["BIT-010", "BIT-011", "BIT-003"],
        porque=("214 días sin venta contra un intervalo mediano de 60: 214 > 2×60 y > 90. "
                "Afinidad MEDIA, así que k = 2. Primer escalón de la escalera: 10 % sobre 520 "
                "= 468, estrictamente entre 273 y 520."),
    ),
    dict(
        id="E02", titulo="Precio propuesto IGUAL al mínimo → la base lo rechaza",
        sku="LLA-014", fecha=FECHA_CORTE, tipo="rechazo_esquema",
        salida="LIQUIDAR", afinidad="MEDIA",
        datos=dict(precio_original=520.00, piso_economico=273.00, piso_comercial=156.00,
                   precio_minimo=273.00, precio_propuesto=273.00, escalon=None),
        folios=["BIT-003"],
        porque="El piso es un piso, no una meta. La desigualdad es estricta: 273 no es > 273.",
        restriccion="recomendacion_precio_entre_limites",
    ),
    dict(
        id="E03", titulo="Precio propuesto IGUAL al original → la base lo rechaza",
        sku="LLA-014", fecha=FECHA_CORTE, tipo="rechazo_esquema",
        salida="LIQUIDAR", afinidad="MEDIA",
        datos=dict(precio_original=520.00, piso_economico=273.00, piso_comercial=156.00,
                   precio_minimo=273.00, precio_propuesto=520.00, escalon=None),
        folios=["BIT-003"],
        porque="Una liquidación sin descuento real no es una liquidación.",
        restriccion="recomendacion_precio_entre_limites",
    ),
    dict(
        id="E04", titulo="Estacional DENTRO de su ventana → RETENER",
        sku="IMP-003", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="RETENER", afinidad="MEDIA",
        datos=dict(fecha_reactivacion="2027-06-01"),
        folios=["BIT-001"],
        porque=("El 22 de septiembre cae dentro de Lluvias (1-jun a 30-sep). La ventana manda "
                "sobre cualquier umbral de rotación, y la fecha es el próximo inicio de ventana."),
    ),
    dict(
        id="E05", titulo="Estacional FUERA de ventana con ventana futura → RETENER",
        sku="ADO-021", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="RETENER", afinidad="MEDIA",
        datos=dict(fecha_reactivacion="2026-11-01"),
        folios=["BIT-001"],
        porque=("295 días sin venta dispararían el umbral, pero es de Fin de año (1-nov a 15-ene): "
                "está dormido, no muerto. Liquidarlo en septiembre destruye la venta de noviembre."),
    ),
    dict(
        id="E06", titulo="Perecedero que no alcanza a venderse antes de caducar → LIQUIDAR",
        sku="SEL-007", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="LIQUIDAR", afinidad="MEDIA",
        datos=dict(precio_original=95.00, piso_economico=43.40, piso_comercial=28.50,
                   precio_minimo=43.40, precio_propuesto=85.50, escalon=1),
        folios=["BIT-010", "BIT-011"],
        porque=("40 piezas a ritmo de ~0.1 al día no se venden en los 95 días que le quedan de "
                "vida menos el margen de 7. Escalón de 10 %: 85.50, entre 43.40 y 95."),
    ),
    dict(
        id="E07", titulo="Perecedero con holgura → sin recomendación",
        sku="PEG-002", fecha=FECHA_CORTE, tipo="sin_salida",
        salida=None, afinidad="MEDIA", datos=dict(),
        folios=["BIT-010"],
        porque=("22 piezas con venta cada 6 días se agotan mucho antes de los 300 días de "
                "caducidad. No hay nada que recomendar y el agente no inventa una salida."),
    ),
    dict(
        id="E08", titulo="Permanente por DEBAJO del umbral → sin recomendación",
        sku="DES-010", fecha=FECHA_CORTE, tipo="sin_salida",
        salida=None, afinidad="MEDIA", datos=dict(),
        folios=["BIT-010"],
        porque=("40 días sin venta contra un mediano de 30: 40 < 2×30. Rotación lenta no es "
                "inventario muerto — este es justo el error que el dominio prohíbe."),
    ),
    dict(
        id="E09", titulo="Permanente casi sin historial y más de un año parado → LIQUIDAR",
        sku="TUB-100", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="LIQUIDAR", afinidad="MEDIA",
        datos=dict(precio_original=340.00, piso_economico=168.00, piso_comercial=102.00,
                   precio_minimo=168.00, precio_propuesto=306.00, escalon=1),
        folios=["BIT-010", "BIT-011"],
        porque=("2 ventas en 24 meses y 400 días sin vender: cae por la regla de historial "
                "insuficiente (< 3 ventas y > 365 días). Sin ella, justo el inventario muerto "
                "se quedaría fuera de toda regla."),
    ),
    dict(
        id="E10", titulo="Afinidad BAJA adelanta el umbral → LIQUIDAR",
        sku="CAL-030", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="LIQUIDAR", afinidad="BAJA",
        datos=dict(precio_original=1290.00, piso_economico=630.00, piso_comercial=387.00,
                   precio_minimo=630.00, precio_propuesto=1032.00, escalon=2),
        folios=["BIT-012", "BIT-010", "BIT-011"],
        porque=("Un calefactor en Mérida: afinidad BAJA, así que k = 1 y 130 > 1×90. Con k = 2 "
                "no sería candidato. Además arranca un escalón más profundo: 20 % → 1032."),
    ),
    dict(
        id="E11", titulo="Afinidad ALTA retrasa el umbral → sin recomendación",
        sku="VEN-040", fecha=FECHA_CORTE, tipo="sin_salida",
        salida=None, afinidad="ALTA", datos=dict(),
        folios=["BIT-012", "BIT-010"],
        porque=("Un ventilador en Mérida: afinidad ALTA, k = 3, y 200 < 3×80. Con la regla "
                "sin clima habría sido candidato a liquidar mercancía que sí se vende aquí."),
    ),
    dict(
        id="E12", titulo="Estacional de invierno en zona tropical → ABSTENERSE",
        sku="CHA-070", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="ABSTENERSE", afinidad="BAJA",
        datos=dict(motivo=("La temporada declarada (Frentes fríos) contradice la afinidad "
                           "climática de la zona. No decido: hay que revisar la ventana.")),
        folios=["BIT-012", "BIT-001"],
        porque=("El caso que motivó BIT-012. La zona dice que casi no rota; la ventana dice que "
                "en diciembre sí. **El clima no deroga BIT-001**: no se liquida por afinidad "
                "baja. Se declara la contradicción y se abstiene."),
    ),
    dict(
        id="E13", titulo="Existencia bajo el punto de reorden → RESURTIR",
        sku="MAR-001", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="RESURTIR", afinidad="MEDIA",
        datos=dict(cantidad_sugerida=24),
        folios=["HU-11"],
        porque=("Rotación sana (venta cada 9 días) y 3 piezas contra un punto de reorden de 5. "
                "RESURTIR produce cantidad, nunca precio."),
    ),
    dict(
        id="E14", titulo="Producto sin conciliación vigente → no se evalúa",
        sku="SIN-001", fecha=FECHA_CORTE, tipo="no_evaluado",
        salida=None, afinidad="MEDIA", datos=dict(),
        folios=["HU-08", "R6"],
        porque=("300 días sin venta lo harían candidato, pero nadie ha contado lo que hay en el "
                "anaquel. No se propone nada sobre existencias sin conciliar: la base lo impide "
                "con `conciliacion_id NOT NULL`."),
    ),
    dict(
        id="E15", titulo="Producto sin costo unitario → ABSTENERSE",
        sku="NOC-001", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="ABSTENERSE", afinidad="ALTA",
        datos=dict(motivo="No tengo el costo de este producto, así que no puedo calcular hasta "
                          "dónde se puede rebajar sin perder más de lo aceptable."),
        folios=["BIT-011", "R1"],
        porque="Sin costo no hay piso económico, y sin piso no hay rango en el cual elegir.",
    ),
    dict(
        id="E16", titulo="Sin referencia de mercado vigente → LIQUIDAR declarándolo",
        sku="PIN-012", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="LIQUIDAR", afinidad="MEDIA",
        datos=dict(precio_original=480.00, piso_economico=210.00, piso_comercial=144.00,
                   precio_minimo=210.00, precio_propuesto=432.00, escalon=1,
                   sin_contraste_de_mercado=True),
        folios=["BIT-002", "BIT-011"],
        porque=("La única referencia de este producto venció hace 9 días. La propuesta sale, "
                "pero marcada: el dueño tiene que ver que no hubo contraste de mercado."),
    ),
    dict(
        # Corregido el 23/sep (MSG-005): el título decía «rechazada» y el porqué
        # «campaña sin vender». Son dos historias distintas y BIT-011 las trata
        # distinto. Se elige una, y ahora los datos la representan completa.
        id="E17", titulo="Campaña aprobada que terminó sin vender → siguiente escalón",
        sku="LLA-014", fecha=FECHA_CORTE + timedelta(days=21), tipo="recomendacion", corrida=2,
        salida="LIQUIDAR", afinidad="MEDIA",
        datos=dict(precio_original=520.00, piso_economico=273.00, piso_comercial=156.00,
                   precio_minimo=273.00, precio_propuesto=416.00, escalon=2),
        # La historia que el generador tiene que dejar en la base ANTES de esta
        # corrida: la recomendación de E01 aprobada por el dueño, su campaña de
        # 14 días, y un resultado de cero unidades vendidas.
        antecedente=dict(escenario="E01", decision="APROBADA", campana_dias=14,
                         unidades_vendidas=0, existencia_inicial=6),
        folios=["BIT-011"],
        porque=("El dueño aprobó E01 a $468 y la campaña de 14 días terminó con cero unidades "
                "vendidas. Baja UN escalón: 20 % → 416. No es reintento automático: es una "
                "recomendación nueva, que apunta a la anterior y vuelve a pasar por la "
                "compuerta. Nunca salta dos escalones de golpe."),
    ),
    dict(
        id="E18", titulo="El piso no deja ningún escalón → ABSTENERSE",
        sku="PIS-001", fecha=FECHA_CORTE, tipo="recomendacion",
        salida="ABSTENERSE", afinidad="ALTA",
        datos=dict(motivo="El precio mínimo (98.00) está tan pegado al precio actual (100.00) "
                          "que ninguna rebaja cabe entre los dos."),
        folios=["BIT-011", "BIT-003"],
        porque=("Piso económico 98 sobre un precio de 100: el primer escalón (10 % → 90) ya cae "
                "por debajo del mínimo. El rango abierto está vacío y el agente lo dice."),
    ),
    dict(
        # BIT-016. El otro camino de BIT-011: después de un RECHAZO nunca se baja
        # de escalón. El dueño dijo que el precio era muy bajo; la re-propuesta
        # sube uno. Antes de BIT-016 el contrato sólo conocía el camino de E17, y
        # la app rechazaba este caso como si fuera un error (MSG-008).
        id="E20", titulo="Rechazada por precio bajo → re-propuesta un escalón ARRIBA",
        sku="CAL-030", fecha=FECHA_CORTE + timedelta(days=21), tipo="recomendacion", corrida=2,
        salida="LIQUIDAR", afinidad="BAJA",
        datos=dict(precio_original=1290.00, piso_economico=630.00, piso_comercial=387.00,
                   precio_minimo=630.00, precio_propuesto=1161.00, escalon=1,
                   # Su única referencia vence el 7 de octubre y esta corrida es del 13:
                   # la propuesta sale sin contraste de mercado y lo declara (BIT-002).
                   # Lo encontró la pantalla de AEGIS, no este oráculo (MSG-010).
                   sin_contraste_de_mercado=True),
        antecedente=dict(escenario="E10", decision="RECHAZADA",
                         motivo="El precio es muy bajo; en diciembre sí se vende."),
        folios=["BIT-016", "BIT-011", "HU-07"],
        porque=("El dueño rechazó E10 (20 %, $1,032) con motivo «el precio es muy bajo». Un "
                "rechazo nunca autoriza bajar: la re-propuesta queda en el mismo escalón o sube "
                "uno. Sube a 10 %: $1,161, estrictamente entre 630 y 1,290. La afinidad BAJA "
                "pedía empezar un escalón más profundo; el motivo del dueño pesa más."),
    ),
    dict(
        id="E19", titulo="Producto de OTRO comercio → no entra a la corrida",
        sku="TUE-001", fecha=FECHA_CORTE, tipo="no_evaluado", comercio=2,
        salida=None, afinidad=None, datos=dict(),
        folios=["BIT-013", "RNF-04"],
        porque=("Balatas paradas 380 días en la refaccionaria: por sí solas serían candidatas a "
                "liquidar. Pero la corrida es de la ferretería. Si el motor las evalúa, olvidó "
                "filtrar por comercio, y eso es una fuga de datos entre clientes, no un error "
                "de cálculo."),
    ),
]


def por_id(ident):
    for esc in ESCENARIOS:
        if esc["id"] == ident:
            return esc
    raise KeyError("No existe el escenario %s" % ident)


def tabla_markdown():
    filas = ["| # | Escenario | SKU | Salida esperada | Folios |",
             "|---|---|---|---|---|"]
    for esc in ESCENARIOS:
        salida = esc["salida"] or "—"
        if esc["tipo"] == "rechazo_esquema":
            salida = "rechazo de la base (`%s`)" % esc["restriccion"]
        elif esc["tipo"] == "sin_salida":
            salida = "sin recomendación"
        elif esc["tipo"] == "no_evaluado":
            salida = "no se evalúa"
        elif esc["salida"] == "LIQUIDAR":
            salida = "LIQUIDAR a $%.2f" % esc["datos"]["precio_propuesto"]
        filas.append("| %s | %s | `%s` | %s | %s |" % (
            esc["id"], esc["titulo"], esc["sku"], salida, ", ".join(esc["folios"])))
    return "\n".join(filas)


if __name__ == "__main__":
    print(tabla_markdown())
    print("\nTotal: %d escenarios" % len(ESCENARIOS))
