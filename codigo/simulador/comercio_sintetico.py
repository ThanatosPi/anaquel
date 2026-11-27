# -*- coding: utf-8 -*-
"""Comercio sintético de Anaquel.

Define una ferretería ficticia con historial de ventas, conciliaciones,
referencias de mercado y afinidades climáticas. Sirve para dos cosas:

  1. dar al motor (zona de AEGIS) algo real contra qué correr, sin depender
     del comercio piloto;
  2. sostener los escenarios de `escenarios.py`, cuya salida esperada está
     escrita a mano.

REGLA DE FRONTERA (BIT-008): aquí NO se implementan las reglas del dominio.
Este módulo genera datos; las salidas esperadas se escriben a mano en
`escenarios.py`. Si algún día hace falta calcularlas, se abre un mensaje.

Determinismo: misma semilla, mismos datos. `FECHA_CORTE` es fija a propósito;
si fuera `date.today()` los escenarios cambiarían de resultado cada día.
"""

import random
from datetime import date, timedelta

SEMILLA = 20260922
FECHA_CORTE = date(2026, 9, 22)

# --- Parámetros del comercio ficticio -------------------------------------
# NO son reglas del dominio: son la política de ESTE comercio de prueba.
# El piso económico admite perder hasta 30 % sobre el costo; el comercial no
# deja rebajar más de 70 % sobre el precio original (BIT-003 usa el mayor).
FACTOR_PISO_ECONOMICO = 0.70
FACTOR_PISO_COMERCIAL = 0.30

COMERCIO = {
    "nombre": "Ferretería El Tornillo Feliz",
    "giro": "ferretería",
    "zona_climatica": "TROPICAL",
    "origen_zona": "GPS",
    "latitud": 20.967200,
    "longitud": -89.624700,   # Mérida, Yucatán
    "municipio": ("Yucatán", "Mérida"),   # municipio_zona_climatica (v2)
}

# La cuenta del agente se llamaba SISTEMA; en la v2 es el rol LLM (BIT-021).
USUARIOS = [
    ("Dueño del comercio", "dueno@ejemplo.invalid", "DUENO"),
    ("Agente Anaquel", "agente@ejemplo.invalid", "LLM"),
]

# BIT-014. Punto representativo y radio de cobertura por municipio. El
# teléfono entrega coordenadas; la base resuelve el municipio más cercano
# dentro de su radio con zona_por_coordenadas(). Fuente pública declarada.
#
# Es el MISMO catálogo que precarga db/schema.sql (municipio_zona_climatica),
# en el mismo orden: el renglón n es el municipio_zona_id n. verificar.py
# compara los dos renglón por renglón.
#
# Los 16 de la CDMX (20/oct): centro y área de los polígonos del Marco
# Geoestadístico del INEGI (dic. 2025), radio = raíz de área/π, y la zona del
# clima que cubre más superficie según el Compendio de información geográfica
# municipal 2010. Los cinco primeros son los de la v1 y no se reclasificaron:
# con esa misma regla, Nezahualcóyotl saldría SEMIARIDO, Toluca TEMPLADO y
# Saltillo ARIDO (pendiente de Sebastián).
FUENTE_CLIMA = "Clasificación Köppen-García (fuente pública)"
_INEGI = "INEGI, Marco Geoestadístico dic. 2025 (%s) y Compendio geográfico municipal 2010: %s"
MUNICIPIOS = [
    # (entidad federativa, municipio, zona, latitud, longitud, radio_km, fuente)
    ("Yucatán", "Mérida", "TROPICAL", 20.967200, -89.624700, 40.0, FUENTE_CLIMA),
    ("Estado de México", "Toluca", "FRIO", 19.282600, -99.655700, 30.0, FUENTE_CLIMA),
    ("Estado de México", "Nezahualcóyotl", "TEMPLADO", 19.400600, -99.014800, 15.0, FUENTE_CLIMA),
    ("Chihuahua", "Chihuahua", "ARIDO", 28.635300, -106.088900, 40.0, FUENTE_CLIMA),
    ("Coahuila", "Saltillo", "SEMIARIDO", 25.423300, -101.000000, 35.0, FUENTE_CLIMA),
    ("Ciudad de México", "Azcapotzalco", "TEMPLADO", 19.485329, -99.182107, 3.3, _INEGI % ("09002", "templado subhúmedo 100 %")),
    ("Ciudad de México", "Coyoacán", "TEMPLADO", 19.326667, -99.150376, 4.1, _INEGI % ("09003", "templado subhúmedo 100 %")),
    ("Ciudad de México", "Cuajimalpa de Morelos", "FRIO", 19.324321, -99.310930, 4.8, _INEGI % ("09004", "semifrío 50.71 %, templado 49.29 %")),
    ("Ciudad de México", "Gustavo A. Madero", "TEMPLADO", 19.504065, -99.115864, 5.3, _INEGI % ("09005", "templado subhúmedo 72.80 %")),
    ("Ciudad de México", "Iztacalco", "SEMIARIDO", 19.396912, -99.094330, 2.7, _INEGI % ("09006", "seco semiseco 78.70 %")),
    ("Ciudad de México", "Iztapalapa", "TEMPLADO", 19.349166, -99.056799, 6.0, _INEGI % ("09007", "templado subhúmedo 77.48 %")),
    ("Ciudad de México", "La Magdalena Contreras", "FRIO", 19.268977, -99.268413, 4.5, _INEGI % ("09008", "semifrío 62.89 %")),
    ("Ciudad de México", "Milpa Alta", "FRIO", 19.141773, -99.049318, 9.6, _INEGI % ("09009", "semifrío 67.87 %")),
    ("Ciudad de México", "Álvaro Obregón", "TEMPLADO", 19.336176, -99.246820, 5.5, _INEGI % ("09010", "templado subhúmedo 71.82 %")),
    ("Ciudad de México", "Tláhuac", "TEMPLADO", 19.276998, -99.002822, 5.2, _INEGI % ("09011", "templado subhúmedo 100 %")),
    ("Ciudad de México", "Tlalpan", "FRIO", 19.198338, -99.206221, 10.0, _INEGI % ("09012", "semifrío 62.92 %")),
    ("Ciudad de México", "Xochimilco", "TEMPLADO", 19.245145, -99.090364, 6.0, _INEGI % ("09013", "templado subhúmedo 97.38 %")),
    ("Ciudad de México", "Benito Juárez", "TEMPLADO", 19.380642, -99.161135, 2.9, _INEGI % ("09014", "templado subhúmedo 100 %")),
    ("Ciudad de México", "Cuauhtémoc", "TEMPLADO", 19.431373, -99.149056, 3.2, _INEGI % ("09015", "templado subhúmedo 100 %")),
    ("Ciudad de México", "Miguel Hidalgo", "TEMPLADO", 19.428069, -99.204584, 3.9, _INEGI % ("09016", "templado subhúmedo 100 %")),
    ("Ciudad de México", "Venustiano Carranza", "SEMIARIDO", 19.430495, -99.093106, 3.3, _INEGI % ("09017", "seco semiseco 91.42 %")),
]


def municipio_zona_id(entidad, municipio):
    """El id que le da la base: posición en MUNICIPIOS, empezando en 1."""
    for i, mun in enumerate(MUNICIPIOS, start=1):
        if mun[:2] == (entidad, municipio):
            return i
    raise KeyError("%s, %s no está en el catálogo de municipios" % (municipio, entidad))

# Segundo comercio (BIT-013). No participa en ningún escenario de salida: está
# para que el motor tenga algo que NO debe tocar. Si una corrida del comercio 1
# evalúa uno de estos productos, el motor olvidó filtrar por comercio (E19).
# El dueño negó el permiso de ubicación: zona elegida a mano.
COMERCIO_2 = {
    "nombre": "Refaccionaria La Tuerca",
    "giro": "refaccionaria",
    "zona_climatica": "FRIO",
    "origen_zona": "MANUAL",
    "latitud": None,
    "longitud": None,
    "municipio": ("Estado de México", "Toluca"),   # elegido a mano (BIT-012)
}

USUARIOS_2 = [
    ("Dueña de la refaccionaria", "dueno2@ejemplo.invalid", "DUENO"),
    ("Agente Anaquel", "agente2@ejemplo.invalid", "LLM"),   # una cuenta del agente por comercio
]

CATEGORIAS_2 = ["Frenos", "Suspensión"]

PRODUCTOS_2 = [
    dict(sku="TUE-001", nombre="Balatas delanteras", categoria="Frenos",
         ciclo="PERMANENTE", precio=640.00, costo=410.00, reorden=4,
         dias_ultima_venta=380, intervalo=45, ventas_24m=5,
         teorica=8, existencia=8, origen="MANUAL"),   # parado: tentaría a liquidar
    dict(sku="TUE-002", nombre="Disco de freno ventilado", categoria="Frenos",
         ciclo="PERMANENTE", precio=980.00, costo=700.00, reorden=2,
         dias_ultima_venta=15, intervalo=20, ventas_24m=30,
         teorica=6, existencia=6, origen="CARGA"),
    dict(sku="TUE-003", nombre="Amortiguador trasero", categoria="Suspensión",
         ciclo="PERMANENTE", precio=1150.00, costo=820.00, reorden=2,
         dias_ultima_venta=60, intervalo=40, ventas_24m=14,
         teorica=4, existencia=4, origen="CARGA"),
]

CATEGORIAS = [
    "Herramienta manual",     # 1
    "Jardinería",             # 2
    "Pintura",                # 3
    "Electricidad",           # 4
    "Ropa de trabajo",        # 5
    "Temporada",              # 6
]

# (nombre, dia_inicio, mes_inicio, dia_fin, mes_fin)
TEMPORADAS = [
    ("Lluvias", 1, 6, 30, 9),        # 1
    ("Fin de año", 1, 11, 15, 1),    # 2 · cruza el año: lo resuelve el motor
    ("Frentes fríos", 1, 12, 28, 2), # 3
]

# BIT-017. Mismo catálogo que unidad_medida en la base: en qué se cuenta el
# producto y si admite fracciones. Una pieza no se cuenta en fracciones.
UNIDADES = {"PIEZA": False, "METRO": True, "KILO": True, "LITRO": True}

# Afinidad climática en zona TROPICAL. La categoría fija el caso general.
AFINIDAD_CATEGORIA = {
    "Herramienta manual": "MEDIA",
    "Jardinería": "ALTA",
    "Pintura": "MEDIA",
    "Electricidad": "MEDIA",
    "Ropa de trabajo": "BAJA",
    "Temporada": "MEDIA",
}

# Productos ancla: los que usan los escenarios. Cada uno trae el historial que
# necesita para caer del lado que el escenario espera.
#   dias_ultima_venta · intervalo · ventas_24m → con qué se arma el historial
#   existencia        · teorica              → conciliación vigente
ANCLAS = [
    dict(sku="LLA-014", nombre='Llave Stillson 14"', categoria="Herramienta manual",
         ciclo="PERMANENTE", precio=520.00, costo=390.00, reorden=2,
         dias_ultima_venta=214, intervalo=60, ventas_24m=11,
         teorica=7, existencia=6, origen="FOTO"),

    dict(sku="MAR-001", nombre="Martillo de uña 16 oz", categoria="Herramienta manual",
         ciclo="PERMANENTE", precio=180.00, costo=110.00, reorden=5,
         dias_ultima_venta=3, intervalo=9, ventas_24m=78,
         teorica=3, existencia=3, origen="CARGA"),

    dict(sku="DES-010", nombre="Desarmador plano 1/4", categoria="Herramienta manual",
         ciclo="PERMANENTE", precio=65.00, costo=38.00, reorden=8,
         dias_ultima_venta=40, intervalo=30, ventas_24m=24,
         teorica=14, existencia=14, origen="CARGA"),

    dict(sku="TUB-100", nombre='Tubo galvanizado 2" x 6 m', categoria="Herramienta manual",
         ciclo="PERMANENTE", precio=340.00, costo=240.00, reorden=1,
         dias_ultima_venta=400, intervalo=380, ventas_24m=2,
         teorica=4, existencia=4, origen="MANUAL"),

    dict(sku="CAL-030", nombre="Calefactor eléctrico 1500 W", categoria="Electricidad",
         ciclo="PERMANENTE", precio=1290.00, costo=900.00, reorden=1,
         dias_ultima_venta=130, intervalo=90, ventas_24m=7,
         teorica=5, existencia=5, origen="CARGA", afinidad="BAJA"),

    dict(sku="VEN-040", nombre="Ventilador de pedestal 16\"", categoria="Electricidad",
         ciclo="PERMANENTE", precio=890.00, costo=600.00, reorden=2,
         dias_ultima_venta=200, intervalo=80, ventas_24m=9,
         teorica=6, existencia=6, origen="CARGA", afinidad="ALTA"),

    dict(sku="IMP-003", nombre="Impermeabilizante 19 L", categoria="Pintura",
         ciclo="ESTACIONAL", temporada="Lluvias", precio=1250.00, costo=890.00, reorden=3,
         dias_ultima_venta=12, intervalo=20, ventas_24m=40,
         teorica=9, existencia=9, origen="MANUAL"),

    dict(sku="ADO-021", nombre="Serie de luces navideñas", categoria="Temporada",
         ciclo="ESTACIONAL", temporada="Fin de año", precio=260.00, costo=150.00, reorden=4,
         dias_ultima_venta=295, intervalo=30, ventas_24m=52,
         teorica=18, existencia=18, origen="CARGA"),

    dict(sku="CHA-070", nombre="Chamarra acolchada de trabajo", categoria="Ropa de trabajo",
         ciclo="ESTACIONAL", temporada="Frentes fríos", precio=780.00, costo=520.00, reorden=2,
         dias_ultima_venta=610, intervalo=200, ventas_24m=3,
         teorica=11, existencia=11, origen="MANUAL", afinidad="BAJA"),

    dict(sku="SEL-007", nombre="Sellador acrílico 300 ml", categoria="Pintura",
         ciclo="PERECEDERO", caducidad_dias=95, precio=95.00, costo=62.00, reorden=10,
         dias_ultima_venta=18, intervalo=25, ventas_24m=29,
         teorica=40, existencia=40, origen="CARGA"),

    dict(sku="PEG-002", nombre="Pegamento de contacto 250 ml", categoria="Pintura",
         ciclo="PERECEDERO", caducidad_dias=300, precio=78.00, costo=45.00, reorden=12,
         dias_ultima_venta=5, intervalo=6, ventas_24m=95,
         teorica=22, existencia=22, origen="CARGA"),

    dict(sku="PIN-012", nombre="Pintura vinílica 4 L blanco", categoria="Pintura",
         ciclo="PERMANENTE", precio=480.00, costo=300.00, reorden=4,
         dias_ultima_venta=260, intervalo=70, ventas_24m=10,
         teorica=8, existencia=8, origen="CARGA"),

    dict(sku="SIN-001", nombre="Juego de llaves Allen", categoria="Herramienta manual",
         ciclo="PERMANENTE", precio=210.00, costo=130.00, reorden=3,
         dias_ultima_venta=300, intervalo=75, ventas_24m=8,
         teorica=5, existencia=None, origen=None),   # sin conciliación: no se evalúa

    dict(sku="NOC-001", nombre="Carretilla reforzada", categoria="Jardinería",
         ciclo="PERMANENTE", precio=1450.00, costo=None, reorden=1,
         dias_ultima_venta=240, intervalo=90, ventas_24m=6,
         teorica=3, existencia=3, origen="MANUAL"),  # sin costo: no hay piso económico

    dict(sku="PIS-001", nombre="Bomba sumergible 1/2 HP", categoria="Jardinería",
         ciclo="PERMANENTE", precio=100.00, costo=140.00, reorden=1,
         dias_ultima_venta=330, intervalo=110, ventas_24m=4,
         teorica=2, existencia=2, origen="MANUAL"),  # el piso no deja ningún escalón
]

# Relleno: productos que no participan en ningún escenario pero que le dan al
# motor un catálogo de tamaño creíble. Se generan con la semilla fija.
N_RELLENO = 45


def _productos_relleno(rnd):
    familias = [
        ("TOR", "Tornillo hexagonal", "Herramienta manual", 12.00, 90),
        ("BRO", "Broca para concreto", "Herramienta manual", 48.00, 70),
        ("MAN", "Manguera de jardín 15 m", "Jardinería", 320.00, 45),
        ("REG", "Regadera de plástico", "Jardinería", 140.00, 60),
        ("BRC", "Brocha de 3 pulgadas", "Pintura", 55.00, 30),
        ("CAB", "Cable calibre 12 (por metro)", "Electricidad", 14.00, 25),   # BIT-017: METRO
        ("FOC", "Foco LED 9 W", "Electricidad", 38.00, 20),
        ("GUA", "Guantes de carnaza", "Ropa de trabajo", 95.00, 50),
    ]
    productos = []
    for i in range(N_RELLENO):
        pre, nombre, categoria, base, inter = familias[i % len(familias)]
        precio = round(base * rnd.uniform(0.8, 2.4), 2)
        productos.append(dict(
            sku="%s-%03d" % (pre, 200 + i),
            nombre="%s %s" % (nombre, rnd.choice(["chico", "mediano", "grande", "premium"])),
            categoria=categoria,
            ciclo="PERMANENTE",
            precio=precio,
            costo=round(precio * rnd.uniform(0.55, 0.75), 2),
            reorden=rnd.randint(2, 10),
            dias_ultima_venta=rnd.randint(1, 260),
            intervalo=max(5, int(inter * rnd.uniform(0.6, 1.6))),
            ventas_24m=rnd.randint(4, 80),
            teorica=rnd.randint(2, 30),
            existencia=None,     # se ajusta abajo
            origen=rnd.choice(["CARGA", "MANUAL"]),
        ))
        productos[-1]["existencia"] = productos[-1]["teorica"]
        # BIT-017. El cable se vende por metro: su existencia lleva fracción. Se
        # deriva sin tocar el generador aleatorio, para no mover los datos de los
        # demás productos ni los escenarios.
        if pre == "CAB":
            productos[-1]["unidad"] = "METRO"
            productos[-1]["teorica"] = productos[-1]["teorica"] * 5 + 0.5
            productos[-1]["existencia"] = productos[-1]["teorica"]
    return productos


def _historial(prod, rnd):
    """Ventas hacia atrás desde la última venta, con intervalo ± ruido."""
    ventas = []
    dia = prod["dias_ultima_venta"]
    cuenta = 0
    while dia <= 720 and cuenta < prod["ventas_24m"]:
        fecha = FECHA_CORTE - timedelta(days=dia)
        ventas.append((fecha, rnd.randint(1, 3), prod["precio"]))
        paso = max(1, int(prod["intervalo"] * rnd.uniform(0.7, 1.3)))
        dia += paso
        cuenta += 1
    return ventas


def _completar(prod, rnd, producto_id, comercio_id):
    prod["producto_id"] = producto_id
    prod["comercio_id"] = comercio_id
    prod.setdefault("unidad", "PIEZA")
    prod.setdefault("temporada", None)
    prod.setdefault("caducidad_dias", None)
    prod.setdefault("afinidad", None)
    prod["ventas"] = _historial(prod, rnd)
    prod["fecha_caducidad"] = (
        FECHA_CORTE + timedelta(days=prod["caducidad_dias"])
        if prod["caducidad_dias"] is not None else None
    )
    prod["fecha_ultima_venta"] = FECHA_CORTE - timedelta(days=prod["dias_ultima_venta"])


def construir():
    """Devuelve los dos comercios como diccionarios, listos para emitir.

    Los identificadores se asignan en el orden en que generar.py inserta:
    primero todo el comercio 1, luego el comercio 2.
    """
    rnd = random.Random(SEMILLA)

    productos = [dict(p) for p in ANCLAS] + _productos_relleno(rnd)
    for i, prod in enumerate(productos, start=1):
        _completar(prod, rnd, i, 1)

    productos_2 = [dict(p) for p in PRODUCTOS_2]
    for i, prod in enumerate(productos_2, start=len(productos) + 1):
        _completar(prod, rnd, i, 2)

    fuentes = [
        ("Catálogo de cadena ferretera", "TIENDA_EN_LINEA", "https://ejemplo.invalid/cadena", 30),
        ("Marketplace nacional", "MARKETPLACE", "https://ejemplo.invalid/market", 15),
        ("Índice público de precios", "INDICE_PUBLICO", "https://ejemplo.invalid/indice", 60),
    ]

    # Referencias: unas vigentes y una vencida a propósito (PIN-012), para que
    # exista el caso «propuesta sin contraste de mercado».
    referencias = [
        dict(sku="LLA-014", fuente=1, precio=498.00, confianza=0.910, dias_vigencia=30,
             descripcion="Llave para tubo 14 pulgadas"),
        dict(sku="CAL-030", fuente=2, precio=1190.00, confianza=0.780, dias_vigencia=15,
             descripcion="Calefactor de ambiente 1500W"),
        dict(sku="TUB-100", fuente=3, precio=352.00, confianza=0.840, dias_vigencia=60,
             descripcion="Tubo galvanizado cédula 30"),
        dict(sku="SEL-007", fuente=1, precio=88.00, confianza=0.900, dias_vigencia=30,
             descripcion="Sellador acrílico para juntas"),
        dict(sku="PIN-012", fuente=1, precio=455.00, confianza=0.870, dias_vigencia=-9,
             descripcion="Pintura vinílica blanca 4 litros"),   # VENCIDA
    ]

    afinidades = []
    for categoria, nivel in AFINIDAD_CATEGORIA.items():
        afinidades.append(dict(ambito="categoria", nombre=categoria, nivel=nivel))
    for prod in productos:
        if prod.get("afinidad"):
            afinidades.append(dict(ambito="producto", nombre=prod["sku"], nivel=prod["afinidad"]))

    return dict(
        comercio=COMERCIO,
        usuarios=USUARIOS,
        categorias=CATEGORIAS,
        temporadas=TEMPORADAS,
        productos=productos,
        fuentes=fuentes,
        referencias=referencias,
        afinidades=afinidades,
        municipios=MUNICIPIOS,
        comercio_2=COMERCIO_2,
        usuarios_2=USUARIOS_2,
        categorias_2=CATEGORIAS_2,
        productos_2=productos_2,
        fecha_corte=FECHA_CORTE,
    )


def todos_los_productos(datos):
    return datos["productos"] + datos["productos_2"]


def por_sku(datos, sku):
    for prod in todos_los_productos(datos):
        if prod["sku"] == sku:
            return prod
    raise KeyError("No existe el producto %s en el comercio sintético" % sku)


def afinidad_de(datos, prod):
    """La fila del producto gana sobre la de la categoría (lección GEA_ECE)."""
    if prod.get("afinidad"):
        return prod["afinidad"]
    return AFINIDAD_CATEGORIA.get(prod["categoria"], "MEDIA")


if __name__ == "__main__":
    d = construir()
    for com, prods in ((d["comercio"], d["productos"]), (d["comercio_2"], d["productos_2"])):
        print("Comercio: %s (%s, zona %s por %s) · %d productos · %d ventas" % (
            com["nombre"], com["giro"], com["zona_climatica"], com["origen_zona"],
            len(prods), sum(len(p["ventas"]) for p in prods)))
    print("Referencias: %d · afinidades: %d · municipios: %d" % (
        len(d["referencias"]), len(d["afinidades"]), len(d["municipios"])))
    print("Fecha de corte: %s" % d["fecha_corte"])
