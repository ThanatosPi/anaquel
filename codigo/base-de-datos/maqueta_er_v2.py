# -*- coding: utf-8 -*-
"""Maqueta del modelo ER v2 de Anaquel (propuesta para la revisión de Base de Datos).

Uso: python db/maqueta_er_v2.py [salida.html]
Sin argumento escribe db/maqueta_er_v2.html, junto a este script.

Toda la propuesta vive aquí como datos: tablas, columnas, nombre anterior y notas. Si la
maestra corrige un nombre, se corrige en table(...) y se regenera; la página no se edita a mano.
Los assert detienen el script si dos tablas se enciman o si una línea recta queda chueca.

Historia: 6/oct primera revisión (26 -> 20 tablas); 8/oct revisión de Felipe (db/REVISION_V2.md)
y segunda revisión de la maestra (db/REVISION_MAESTRA.md): las entidades débiles pierden
comercio_id, los detalles se identifican por sus dueños, tabla de roles y zona por municipio.
"""
import os
import re
import sys
from datetime import date
from html import escape as E

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), 'maqueta_er_v2.html')
IDENT = re.compile(r'(\*_en|fecha_\*|\b[a-z]+(?:_[a-z0-9]+)+\b|\bDUENO\b|\bCHECK\b)')


def I(s):
    """Escapa el texto y pone en código los identificadores."""
    return IDENT.sub(lambda m: '<code>' + m.group(1) + '</code>', E(s))


AREAS = [
    ('cu', 'Comercio y usuarios', 'a1', 'Quién usa el sistema, con qué rol, y dónde está el comercio.'),
    ('ca', 'Catálogo', 'a2', 'Lo que vende el comercio y cómo se clasifica.'),
    ('vc', 'Ventas y compras', 'a3', 'Lo que llega del punto de venta y de las facturas de proveedor.'),
    ('im', 'Inventario y mercado', 'a4', 'Cuánto hay de verdad en el anaquel y a cuánto se vende afuera.'),
    ('re', 'Recomendación', 'a5', 'Lo que propone el agente cada madrugada y lo que decide el dueño.'),
    ('cp', 'Campañas', 'a6', 'Las ofertas aprobadas y a quién se enviaron.'),
]
AREA = {a[0]: a for a in AREAS}


def C(name, typ, key='', old=None, note=''):
    return {'name': name, 'typ': typ, 'key': key, 'old': old, 'note': note}


TABLES = {}


def table(name, area, old, desc, cols, absorbs=(), new=False):
    TABLES[name] = {'name': name, 'area': area, 'old': old, 'desc': desc,
                    'cols': cols, 'absorbs': list(absorbs), 'new': new}


# ---------------------------------------------------------------- tablas
table('comercio', 'cu', None, 'La tienda. Las entidades fuertes dependen de ella; las débiles heredan su comercio.', [
    C('comercio_id', 'BIGINT', 'pk'),
    C('municipio_zona_id', 'BIGINT', 'fk', 'NUEVA', 'El municipio donde está el comercio, a partir de su ubicación. La zona climática sale de ahí.'),
    C('rfc', 'TEXT', 'uq', 'NUEVA', 'Aparece en el boceto de la revisión. Opcional.'),
    C('nombre_comercio', 'TEXT', '', 'nombre'),
    C('giro_comercial', 'TEXT', '', 'giro'),
    C('zona_horaria', 'TEXT'),
    C('origen_zona_climatica', 'TEXT', '', 'origen_zona', 'GPS o MANUAL (BIT-012): cómo se obtuvo el municipio.'),
    C('latitud', 'NUMERIC(9,6)'),
    C('longitud', 'NUMERIC(9,6)'),
    C('fecha_alta', 'DATE', '', 'creado_en'),
])
table('rol', 'cu', None,
      'Catálogo de roles. Sólo DUENO autoriza. LLM es el agente: propone y nunca autoriza. ADMINISTRADOR administra la plataforma y no pertenece a ningún comercio.', [
    C('codigo_rol', 'TEXT', 'pk', 'NUEVA', 'DUENO, ADMINISTRADOR, ANALISTA o LLM.'),
    C('descripcion_rol', 'TEXT', '', 'NUEVA'),
], new=True)
table('usuario', 'cu', None, 'Quien entra a la app, incluido el agente (rol LLM, uno por comercio).', [
    C('usuario_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co', None, 'Vacío sólo para ADMINISTRADOR (CHECK).'),
    C('codigo_rol', 'TEXT', 'fk', 'rol', 'Antes era texto con CHECK. La cuenta SISTEMA pasa a ser el rol LLM.'),
    C('correo_electronico', 'TEXT', 'uq', 'correo'),
    C('nombre_completo', 'TEXT', '', 'nombre'),
    C('hash_contrasena', 'TEXT'),
    C('esta_activo', 'BOOLEAN', '', 'activo'),
    C('fecha_alta', 'DATE', '', 'creado_en'),
])
table('municipio_zona_climatica', 'cu', 'zona_climatica_referencia',
      'Catálogo precargado con todos los municipios de la zona que se cubre, por ejemplo las 16 alcaldías de la CDMX. Con la ubicación del cliente, zona_por_coordenadas() da el municipio donde está (BIT-014); si no hay, se elige a mano de esta lista. El ADMINISTRADOR agrega municipios cuando se cubre otra zona.', [
    C('municipio_zona_id', 'BIGINT', 'pk', 'referencia_zona_id'),
    C('entidad_federativa', 'TEXT', 'uq', 'estado', 'Antes «estado», que se leía como estado de un registro.'),
    C('municipio', 'TEXT', 'uq', None, 'Único junto con entidad_federativa.'),
    C('zona_climatica', 'TEXT', '', 'zona', 'CHECK: TROPICAL, TEMPLADO, FRIO, ARIDO o SEMIARIDO. Antes apuntaba a la tabla zona_climatica.'),
    C('latitud_centro', 'NUMERIC(9,6)', '', 'latitud'),
    C('longitud_centro', 'NUMERIC(9,6)', '', 'longitud'),
    C('radio_km', 'NUMERIC(6,1)'),
    C('fuente_dato', 'TEXT', '', 'fuente'),
    C('fecha_hora_captura', 'TIMESTAMPTZ', '', 'capturada_en'),
])
table('categoria', 'ca', None, 'Agrupa los productos del comercio. Guarda también su afinidad con el clima.', [
    C('categoria_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('nombre_categoria', 'TEXT', 'uq', 'nombre', 'Único dentro del comercio.'),
    C('afinidad_climatica', 'TEXT', '', 'afinidad_climatica.nivel', 'ALTA, MEDIA, BAJA o NO_APLICA. Antes era una fila de la tabla afinidad_climatica.'),
    C('zona_climatica_afinidad', 'TEXT', '', 'afinidad_climatica.zona', 'Zona para la que se declaró la afinidad; obligatoria si hay afinidad. Propuesta pendiente de folio: si no coincide con la zona del comercio, el agente la ignora y lo dice en la justificación.'),
], absorbs=['afinidad_climatica'])
table('temporada', 'ca', None, 'La ventana del año en que vende un producto estacional.', [
    C('temporada_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('nombre_temporada', 'TEXT', 'uq', 'nombre', 'Único dentro del comercio.'),
    C('dia_inicio', 'SMALLINT', '', None, 'Día y mes, sin año: la temporada se repite cada año (BIT-015).'),
    C('mes_inicio', 'SMALLINT'),
    C('dia_fin', 'SMALLINT'),
    C('mes_fin', 'SMALLINT'),
])
table('unidad_medida', 'ca', None,
      'PIEZA, METRO, KILO o LITRO. Sigue siendo tabla porque las cantidades de otras tablas consultan si la unidad acepta decimales (BIT-017).', [
    C('codigo_unidad', 'TEXT', 'pk', 'codigo'),
    C('descripcion_unidad', 'TEXT', '', 'descripcion'),
    C('permite_decimales', 'BOOLEAN', '', 'admite_fraccion', 'Una pieza se cuenta en enteros; un metro no.'),
])
table('producto', 'ca', None, 'Lo que hay en el anaquel. Gana stock máximo y existencia, como en el boceto de la revisión.', [
    C('producto_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('codigo_sku', 'TEXT', 'uq', 'sku', 'Único dentro del comercio: dos comercios pueden usar el mismo código.'),
    C('categoria_id', 'BIGINT', 'fk'),
    C('temporada_id', 'BIGINT', 'fk', None, 'Obligatoria si el ciclo es ESTACIONAL.'),
    C('codigo_unidad', 'TEXT', 'fk', 'unidad'),
    C('nombre_producto', 'TEXT', '', 'nombre'),
    C('ciclo_producto', 'TEXT', '', 'ciclo', 'PERECEDERO, ESTACIONAL o PERMANENTE. Antes apuntaba a la tabla ciclo_producto.'),
    C('afinidad_climatica_propia', 'TEXT', '', 'afinidad_climatica.nivel', 'Opcional. Si se llena, manda sobre la de su categoría.'),
    C('zona_climatica_afinidad_propia', 'TEXT', '', 'afinidad_climatica.zona', 'Zona para la que se declaró la afinidad propia.'),
    C('precio_venta_regular', 'NUMERIC(12,2)', '', 'precio_original', 'Precio normal, sin rebaja. Es el «precio original» de la regla mínimo < propuesto < original.'),
    C('costo_compra_unitario', 'NUMERIC(12,2)', '', 'costo_unitario', 'Costo vigente. El de cada compra queda en detalle_factura_proveedor. Pendiente desde la Clase 07: cuál manda si la factura y el punto de venta no coinciden.'),
    C('stock_minimo', 'INTEGER', '', 'punto_reorden', 'Es el punto de reorden de HU-11: con la política de mínimos y máximos, al llegar aquí se resurte.'),
    C('stock_maximo', 'INTEGER', '', 'NUEVA', 'Hasta dónde se resurte. Lo usaría la regla de RESURTIR propuesta (cantidad = stock_maximo − existencia_sistema), que aún no se aprueba.'),
    C('existencia_sistema', 'NUMERIC(12,3)', '', 'NUEVA', 'Lo que el punto de venta dice que hay hoy. El conteo físico va en conciliacion_inventario.'),
    C('fecha_caducidad', 'DATE', '', None, 'Obligatoria si el ciclo es PERECEDERO.'),
    C('estado_producto', 'TEXT', '', 'activo', 'ACTIVO o DESCONTINUADO.'),
    C('fecha_alta', 'DATE', '', 'creado_en'),
], absorbs=['afinidad_climatica'])
table('venta', 'vc', None, 'El encabezado del ticket que llega del punto de venta.', [
    C('venta_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('folio_ticket_pos', 'TEXT', 'uq', 'folio_origen', 'Folio del ticket en el punto de venta. Único dentro del comercio, para que una carga repetida no duplique ventas. Las ventas sin folio no tienen esa protección: en PostgreSQL varios valores vacíos no chocan. Si el punto de venta reinicia el folio por caja, la caja entra en la llave.'),
    C('fecha_hora_venta', 'TIMESTAMPTZ', '', 'vendida_en'),
    C('fecha_hora_carga', 'TIMESTAMPTZ', '', 'cargada_en', 'Cuándo llegó a Anaquel.'),
])
table('detalle_venta', 'vc', 'venta_renglon',
      'Cada producto dentro de un ticket. Entidad débil: se identifica por su venta y su producto, y hereda el comercio de la venta.', [
    C('venta_id', 'BIGINT', 'pkfk'),
    C('producto_id', 'BIGINT', 'pkfk', None, 'Si el ticket trae el mismo producto en dos renglones, la carga los suma.'),
    C('cantidad_vendida', 'NUMERIC(12,3)', '', 'cantidad'),
    C('precio_unitario_cobrado', 'NUMERIC(12,2)', '', 'precio_unitario'),
    C('subtotal', 'NUMERIC(12,2)', 'gen', 'NUEVA', 'Calculada: cantidad_vendida × precio_unitario_cobrado.'),
])
table('factura_proveedor', 'vc', None, 'Factura de compra. Su imagen se lee con OCR para sacar el costo; quien la recibe empareja sus renglones.', [
    C('factura_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('recibida_por_usuario_id', 'BIGINT', 'fkx', 'factura_renglon.revisado_por', 'Quien recibe la factura y empareja cada renglón con su producto. Antes iba en cada renglón.'),
    C('nombre_proveedor', 'TEXT', '', 'proveedor'),
    C('folio_factura', 'TEXT', '', 'folio'),
    C('fecha_emision', 'DATE', '', 'emitida_en'),
    C('ruta_imagen_factura', 'TEXT', '', 'ruta_imagen'),
    C('fecha_hora_captura', 'TIMESTAMPTZ', '', 'capturada_en'),
])
table('detalle_factura_proveedor', 'vc', 'factura_renglon',
      'Cada renglón de la factura. Entidad débil: se identifica por su factura y su producto, y hereda el comercio de la factura.', [
    C('factura_id', 'BIGINT', 'pkfk'),
    C('producto_id', 'BIGINT', 'pkfk', None, 'Obligatorio: el renglón se empareja con su producto al recibir la factura. Si el mismo producto viene dos veces, se suma.'),
    C('descripcion_leida_ocr', 'TEXT', '', 'descripcion_ocr'),
    C('cantidad_comprada', 'NUMERIC(12,3)', '', 'cantidad'),
    C('costo_compra_unitario', 'NUMERIC(12,2)', '', 'costo_unitario', 'Costo de esa compra. Es histórico: no cambia aunque cambie el costo vigente del producto.'),
    C('confianza_ocr', 'NUMERIC(4,3)', '', 'confianza', 'Entre 0 y 1.'),
])
table('conciliacion_inventario', 'im', 'conciliacion',
      'El conteo físico contra lo que dice el sistema. Sin conciliación no hay recomendación. Conserva comercio_id mientras se confirma si es débil (pregunta 1).', [
    C('conciliacion_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('producto_id', 'BIGINT', 'fk'),
    C('confirmado_por_usuario_id', 'BIGINT', 'fkx', 'confirmada_por'),
    C('existencia_sistema_al_contar', 'NUMERIC(12,3)', '', 'existencia_teorica', 'Lo que decía el sistema en el momento del conteo. La existencia actual está en producto.existencia_sistema.'),
    C('existencia_contada', 'NUMERIC(12,3)', '', 'existencia_fisica'),
    C('metodo_conteo', 'TEXT', '', 'origen', 'FOTO, MANUAL o CARGA. Un conteo por FOTO lo confirma una persona.'),
    C('ruta_foto_anaquel', 'TEXT', '', 'ruta_imagen'),
    C('confianza_vision', 'NUMERIC(4,3)', '', 'confianza'),
    C('fecha_hora_conteo', 'TIMESTAMPTZ', '', 'conciliada_en'),
])
table('fuente_precio_publica', 'im', 'fuente_publica', 'Un sitio público de donde se toman precios de referencia (BIT-002).', [
    C('fuente_id', 'BIGINT', 'pk'),
    C('nombre_fuente', 'TEXT', 'uq', 'nombre'),
    C('tipo_fuente', 'TEXT', '', 'tipo', 'TIENDA_EN_LINEA, MARKETPLACE, BASE_ABIERTA o INDICE_PUBLICO.'),
    C('url_base', 'TEXT'),
    C('dias_vigencia', 'INTEGER', '', 'vigencia_dias', 'Cuántos días vale un precio capturado de esta fuente.'),
    C('fecha_hora_registro', 'TIMESTAMPTZ', '', 'declarada_en'),
])
table('referencia_precio_mercado', 'im', 'referencia_mercado', 'Un precio publicado, con su origen, fecha de captura y vencimiento.', [
    C('referencia_id', 'BIGINT', 'pk'),
    C('producto_id', 'BIGINT', 'fk'),
    C('fuente_id', 'BIGINT', 'fk'),
    C('url_publicacion', 'TEXT', '', 'url'),
    C('descripcion_publicada', 'TEXT', '', 'descripcion'),
    C('precio_publicado', 'NUMERIC(12,2)', '', 'precio', 'Mayor que cero.'),
    C('confianza_emparejamiento', 'NUMERIC(4,3)', '', None, 'Qué tan seguro es que sea el mismo producto.'),
    C('fecha_hora_captura', 'TIMESTAMPTZ', '', 'capturada_en'),
    C('fecha_hora_vencimiento', 'TIMESTAMPTZ', '', 'vigente_hasta'),
])
table('corrida_nocturna', 're', 'corrida_analisis', 'La corrida de madrugada que evalúa los productos de un comercio.', [
    C('corrida_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('fecha_hora_inicio', 'TIMESTAMPTZ', '', 'iniciada_en'),
    C('fecha_hora_fin', 'TIMESTAMPTZ', '', 'terminada_en'),
    C('estado_corrida', 'TEXT', '', 'estado', 'EN_CURSO, TERMINADA o FALLIDA.'),
    C('cantidad_productos_evaluados', 'INTEGER', '', 'productos_evaluados'),
])
table('recomendacion', 're', None,
      'Lo que el agente propone para un producto y lo que decide el dueño sobre esa propuesta. Entidad débil de la corrida: hereda su comercio.', [
    C('recomendacion_id', 'BIGINT', 'pk'),
    C('corrida_id', 'BIGINT', 'fk'),
    C('producto_id', 'BIGINT', 'fk', None, 'Del mismo comercio que la corrida (disparador recomendacion_mismo_comercio).'),
    C('conciliacion_id', 'BIGINT', 'fk', None, 'Llave compuesta con producto_id: no puede citar el conteo de otro producto.'),
    C('recomendacion_anterior_id', 'BIGINT', 'fk', None, 'La propuesta que esta reemplaza, si es una re-propuesta (BIT-016).'),
    C('decidido_por_usuario_id', 'BIGINT', 'fkx', 'autorizacion.usuario_id', 'Sólo un usuario DUENO del comercio de la corrida (disparador exigir_firma_del_dueno).'),
    C('accion_recomendada', 'TEXT', '', 'salida', 'LIQUIDAR, RETENER, RESURTIR o ABSTENERSE. Antes apuntaba a la tabla salida.'),
    C('justificacion', 'TEXT'),
    C('dias_sin_venta', 'INTEGER'),
    C('capital_inmovilizado', 'NUMERIC(12,2)', '', 'capital_detenido'),
    C('afinidad_climatica_aplicada', 'TEXT', '', 'afinidad_clima'),
    C('precio_venta_regular', 'NUMERIC(12,2)', '', 'precio_original', 'Copia del precio al momento de recomendar.'),
    C('precio_minimo_por_costo', 'NUMERIC(12,2)', '', 'piso_economico', 'Pérdida máxima aceptable sobre el costo.'),
    C('precio_minimo_por_descuento', 'NUMERIC(12,2)', '', 'piso_comercial', 'Descuento máximo sobre el precio regular.'),
    C('precio_minimo', 'NUMERIC(12,2)', 'gen', None, 'Calculada: el mayor de los dos anteriores, igual que en la v1.'),
    C('precio_propuesto', 'NUMERIC(12,2)', '', None, 'Sólo LIQUIDAR. Estrictamente entre precio_minimo y precio_venta_regular.'),
    C('recuperacion_esperada', 'NUMERIC(12,2)'),
    C('escalon_descuento', 'SMALLINT', '', 'escalon', '10 %, 20 %, 30 %… (BIT-011).'),
    C('sin_contraste_de_mercado', 'BOOLEAN', '', 'NUEVA', 'Sólo LIQUIDAR: se propuso sin referencia de mercado vigente (BIT-002). MSG-018 H2, agregada el 20/oct tras la aprobación; avisar a la maestra.'),
    C('fecha_reactivacion_estimada', 'DATE', '', 'fecha_reactivacion', 'Sólo RETENER.'),
    C('cantidad_a_resurtir', 'NUMERIC(12,3)', '', 'cantidad_sugerida', 'Sólo RESURTIR.'),
    C('fecha_hora_generacion', 'TIMESTAMPTZ', '', 'generada_en'),
    C('estado_decision', 'TEXT', '', 'autorizacion.decision', 'PENDIENTE, APROBADA o RECHAZADA. Una vez decidida, no cambia (disparador decision_inmutable).'),
    C('fecha_hora_decision', 'TIMESTAMPTZ', '', 'autorizacion.decidida_en'),
    C('motivo_rechazo', 'TEXT', '', 'autorizacion.motivo_rechazo', 'Obligatorio si se rechaza; vacío si se aprueba.'),
    C('precio_autorizado', 'NUMERIC(12,2)', '', 'NUEVA', 'El precio al que el dueño aprobó una liquidación; puede ser distinto del propuesto, con la misma regla mínimo < autorizado < regular. MSG-018 H2, agregada el 20/oct tras la aprobación; avisar a la maestra.'),
    C('acepto_riesgo_sin_contraste', 'BOOLEAN', '', 'NUEVA', 'Obligatorio para aprobar una propuesta sin contraste de mercado. MSG-018 H2, agregada el 20/oct tras la aprobación; avisar a la maestra.'),
], absorbs=['autorizacion'])
table('recomendacion_referencia', 're', None, 'Tabla puente: qué precios de mercado consultó cada recomendación.', [
    C('referencia_id', 'BIGINT', 'pkfk'),
    C('recomendacion_id', 'BIGINT', 'pkfk'),
    C('producto_id', 'BIGINT', 'fkx', None, 'Completa las dos llaves compuestas: la referencia y la recomendación son del mismo producto.'),
])
table('campana', 'cp', None, 'La campaña de liquidación. Sale sólo con aprobación del dueño y guarda también su resultado.', [
    C('campana_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('recomendacion_id', 'BIGINT', 'fkuq', 'autorizacion_id', 'Una campaña por recomendación, y sólo si está APROBADA (disparador exigir_autorizacion_aprobada).'),
    C('texto_mensaje', 'TEXT', '', 'texto'),
    C('ruta_imagen_promocional', 'TEXT', '', 'ruta_imagen'),
    C('fecha_hora_inicio', 'TIMESTAMPTZ', '', 'inicia_en'),
    C('fecha_hora_fin', 'TIMESTAMPTZ', '', 'termina_en'),
    C('fecha_alta', 'DATE', '', 'creada_en'),
    C('unidades_vendidas', 'NUMERIC(12,3)', '', 'resultado_campana.unidades_vendidas'),
    C('ingreso_obtenido', 'NUMERIC(12,2)', '', 'resultado_campana.ingreso'),
    C('capital_recuperado', 'NUMERIC(12,2)', '', 'resultado_campana.capital_recuperado'),
    C('fecha_hora_calculo_resultado', 'TIMESTAMPTZ', '', 'resultado_campana.calculado_en'),
], absorbs=['resultado_campana'])
table('cliente', 'cp', None, 'Cliente que aceptó recibir ofertas del comercio.', [
    C('cliente_id', 'BIGINT', 'pk'),
    C('comercio_id', 'BIGINT', 'co'),
    C('nombre_cliente', 'TEXT', '', 'nombre'),
    C('telefono', 'TEXT'),
    C('correo_electronico', 'TEXT', '', 'correo'),
    C('fecha_hora_consentimiento', 'TIMESTAMPTZ', '', 'consintio_en'),
    C('fecha_hora_revocacion', 'TIMESTAMPTZ', '', 'revoco_en'),
    C('maximo_envios_por_mes', 'SMALLINT', '', 'max_envios_mes'),
])
table('envio_campana', 'cp', 'envio', 'Cada mensaje de una campaña a un cliente. Hereda el comercio de la campaña.', [
    C('envio_id', 'BIGINT', 'pk'),
    C('campana_id', 'BIGINT', 'fk'),
    C('cliente_id', 'BIGINT', 'fk', None, 'Del mismo comercio que la campaña (disparador envio_mismo_comercio). Un cliente recibe cada campaña una sola vez.'),
    C('canal_envio', 'TEXT', '', 'canal', 'APP, CORREO o WHATSAPP.'),
    C('fecha_hora_envio', 'TIMESTAMPTZ', '', 'enviado_en'),
])

assert len(TABLES) == 21, len(TABLES)

ALL_COLS = [c for t in TABLES.values() for c in t['cols']]
N_RENAMED = sum(1 for c in ALL_COLS if c['old'] and c['old'] != 'NUEVA' and '.' not in c['old'])
N_MOVED = sum(1 for c in ALL_COLS if c['old'] and '.' in c['old'])
N_NEW = sum(1 for c in ALL_COLS if c['old'] == 'NUEVA')
N_COMERCIO = sum(1 for t in TABLES.values() if any(c['key'] == 'co' for c in t['cols']))
N_USUARIO = sum(1 for t in TABLES.values() for c in t['cols'] if c['key'] == 'fkx' and c['name'].endswith('usuario_id'))
N_TABLES_RENAMED = sum(1 for t in TABLES.values() if t['old'])
N_COLS = len(ALL_COLS)
WEAK = ['detalle_venta', 'detalle_factura_proveedor', 'recomendacion', 'envio_campana']
for w in WEAK:
    assert not any(c['name'] == 'comercio_id' for c in TABLES[w]['cols']), w

# ---------------------------------------------------------------- geometría
W, HEAD, ROW, FOOT, PADB = 232, 30, 20, 20, 6
COLX = [24, 314, 626, 916]
POS = {
    'categoria': (0, 40), 'temporada': (0, 184), 'unidad_medida': (0, 328), 'cliente': (0, 912),
    'producto': (1, 40), 'municipio_zona_climatica': (1, 256), 'comercio': (1, 420),
    'usuario': (1, 564), 'rol': (1, 748), 'envio_campana': (1, 872),
    'detalle_venta': (2, 40), 'detalle_factura_proveedor': (2, 184),
    'referencia_precio_mercado': (2, 328), 'conciliacion_inventario': (2, 472),
    'recomendacion': (2, 656), 'campana': (2, 892),
    'venta': (3, 40), 'factura_proveedor': (3, 184), 'fuente_precio_publica': (3, 368),
    'recomendacion_referencia': (3, 496), 'corrida_nocturna': (3, 676),
}
assert set(POS) == set(TABLES), set(TABLES) ^ set(POS)
KEYS = ('pk', 'co', 'fk', 'fkx', 'uq', 'pkfk', 'fkuq')
VW, VH = 1172, 1040


def keyrows(n):
    return [c for c in TABLES[n]['cols'] if c['key'] in KEYS]


def extra(n):
    return len(TABLES[n]['cols']) - len(keyrows(n))


def H(n):
    return HEAD + ROW * len(keyrows(n)) + (FOOT if extra(n) else 0) + PADB


def L(n): return COLX[POS[n][0]]
def R(n): return L(n) + W
def T(n): return POS[n][1]
def B(n): return T(n) + H(n)
def CX(n): return L(n) + W // 2


def Y(n, col):
    names = [c['name'] for c in keyrows(n)]
    return T(n) + HEAD + ROW * names.index(col) + ROW // 2


# ninguna tabla se encima con otra
for a in TABLES:
    for b in TABLES:
        if a < b and POS[a][0] == POS[b][0]:
            assert B(a) + 12 <= T(b) or B(b) + 12 <= T(a), (a, b)
for n in TABLES:
    assert B(n) <= VH - 20, n

# líneas rectas: el renglón de la llave primaria queda a la altura del renglón de la foránea
STRAIGHT = [('venta', 'venta_id', 'detalle_venta', 'venta_id'),
            ('factura_proveedor', 'factura_id', 'detalle_factura_proveedor', 'factura_id'),
            ('fuente_precio_publica', 'fuente_id', 'referencia_precio_mercado', 'fuente_id'),
            ('corrida_nocturna', 'corrida_id', 'recomendacion', 'corrida_id'),
            ('campana', 'campana_id', 'envio_campana', 'campana_id'),
            ('cliente', 'cliente_id', 'envio_campana', 'cliente_id')]
for p, pc, c, cc in STRAIGHT:
    assert Y(p, pc) == Y(c, cc), (p, Y(p, pc), c, Y(c, cc))


# ---------------------------------------------------------------- dibujo
def f(v):
    return ('%.1f' % v).rstrip('0').rstrip('.')


def along(x, y, d, k, o):
    return (x + d * k, y) if o == 'h' else (x, y + d * k)


def bar(x, y, d, k, o):
    bx, by = along(x, y, d, k, o)
    if o == 'h':
        return f'<line class="er-m" x1="{f(bx)}" y1="{f(by-5)}" x2="{f(bx)}" y2="{f(by+5)}"/>'
    return f'<line class="er-m" x1="{f(bx-5)}" y1="{f(by)}" x2="{f(bx+5)}" y2="{f(by)}"/>'


def ring(x, y, d, k, o):
    cx, cy = along(x, y, d, k, o)
    return f'<circle class="er-o" cx="{f(cx)}" cy="{f(cy)}" r="3"/>'


def foot(x, y, d, o):
    ax, ay = along(x, y, d, 9, o)
    if o == 'h':
        return (f'<path class="er-m" fill="none" d="M{f(ax)},{f(ay)} L{f(x)},{f(y-5)} '
                f'M{f(ax)},{f(ay)} L{f(x)},{f(y+5)}"/>')
    return (f'<path class="er-m" fill="none" d="M{f(ax)},{f(ay)} L{f(x-5)},{f(y)} '
            f'M{f(ax)},{f(ay)} L{f(x+5)},{f(y)}"/>')


def mark(kind, x, y, d, o):
    if kind == '||':
        return bar(x, y, d, 5, o) + bar(x, y, d, 9, o)
    if kind == 'o|':
        return bar(x, y, d, 5, o) + ring(x, y, d, 13, o)
    if kind == 'o<':
        return foot(x, y, d, o) + ring(x, y, d, 13, o)
    raise ValueError(kind)


def path(pts):
    d = 'M' + ' L'.join(f'{f(x)},{f(y)}' for x, y in pts)
    return f'<path class="er-e" fill="none" d="{d}"/>'


EDGES = []
LABELS = []


def edge(pts, pmark, cmark):
    """pts: de la tabla padre a la tabla hija. pmark/cmark: (tipo, x, y, dir, orient)."""
    EDGES.append(path(pts) + mark(*pmark) + mark(*cmark))


def vertical(parent, child, label, pkind='||', ckind='o<'):
    """Padre arriba o abajo de la hija, en la misma columna."""
    x = CX(child)
    if B(parent) < T(child):        # padre arriba
        p, c, pd, cd = B(parent), T(child), 1, -1
    else:                           # padre abajo
        p, c, pd, cd = T(parent), B(child), -1, 1
    edge([(x, p), (x, c)], (pkind, x, p, pd, 'v'), (ckind, x, c, cd, 'v'))
    LABELS.append((x + 9, (p + c) / 2 + 4, label))


# catálogo -> producto
for parent, pk, fkcol, ex, kind in [('categoria', 'categoria_id', 'categoria_id', 276, 'o|'),
                                    ('temporada', 'temporada_id', 'temporada_id', 284, 'o|'),
                                    ('unidad_medida', 'codigo_unidad', 'codigo_unidad', 292, '||')]:
    py, cy = Y(parent, pk), Y('producto', fkcol)
    edge([(R(parent), py), (ex, py), (ex, cy), (L('producto'), cy)],
         (kind, R(parent), py, 1, 'h'), ('o<', L('producto'), cy, -1, 'h'))

# producto -> cinco hijas, por un tronco común
TRUNK = 566
children = [('detalle_venta', '||'), ('detalle_factura_proveedor', '||'),
            ('referencia_precio_mercado', '||'), ('conciliacion_inventario', '||'), ('recomendacion', '||')]
ys = [Y(c, 'producto_id') for c, _ in children]
EDGES.append(path([(R('producto'), Y('producto', 'producto_id')), (TRUNK, Y('producto', 'producto_id')),
                   (TRUNK, max(ys))]))
for (c, kind), cy in zip(children, ys):
    EDGES.append(path([(TRUNK, cy), (L(c), cy)]) + mark(kind, TRUNK, cy, 1, 'h') + mark('o<', L(c), cy, -1, 'h'))

# derecha -> izquierda, rectas
for p, pc, c, cc in STRAIGHT[:4]:
    y = Y(p, pc)
    edge([(L(p), y), (R(c), y)], ('||', L(p), y, -1, 'h'), ('o<', R(c), y, 1, 'h'))

# campaña -> envío y cliente -> envío
y = Y('campana', 'campana_id')
edge([(L('campana'), y), (R('envio_campana'), y)], ('||', L('campana'), y, -1, 'h'), ('o<', R('envio_campana'), y, 1, 'h'))
y = Y('cliente', 'cliente_id')
edge([(R('cliente'), y), (L('envio_campana'), y)], ('||', R('cliente'), y, 1, 'h'), ('o<', L('envio_campana'), y, -1, 'h'))

# referencia -> puente y recomendación -> puente
rx = R('referencia_precio_mercado')
py = B('referencia_precio_mercado') - 10
cy = Y('recomendacion_referencia', 'referencia_id')
assert py > Y('referencia_precio_mercado', 'fuente_id')
edge([(rx, py), (878, py), (878, cy), (L('recomendacion_referencia'), cy)],
     ('||', rx, py, 1, 'h'), ('o<', L('recomendacion_referencia'), cy, -1, 'h'))
py = Y('recomendacion', 'recomendacion_id')
cy = Y('recomendacion_referencia', 'recomendacion_id')
assert py < Y('recomendacion', 'corrida_id')
edge([(R('recomendacion'), py), (886, py), (886, cy), (L('recomendacion_referencia'), cy)],
     ('||', R('recomendacion'), py, 1, 'h'), ('o<', L('recomendacion_referencia'), cy, -1, 'h'))

# verticales
vertical('conciliacion_inventario', 'recomendacion', 'conciliacion_id')
vertical('recomendacion', 'campana', 'recomendacion_id · 1 a 0..1', ckind='o|')
vertical('municipio_zona_climatica', 'comercio', 'municipio_zona_id', pkind='o|')
vertical('rol', 'usuario', 'codigo_rol')

# recomendación -> recomendación anterior
cy = Y('recomendacion', 'recomendacion_anterior_id')
lx, bx, by = L('recomendacion'), L('recomendacion') + 36, B('recomendacion') + 28
assert by < T('campana') - 12
EDGES.append(path([(bx, B('recomendacion')), (bx, by), (lx - 26, by), (lx - 26, cy), (lx, cy)])
             + mark('o|', bx, B('recomendacion'), 1, 'v') + mark('o<', lx, cy, -1, 'h'))
assert lx - 26 > TRUNK + 8 and cy > max(ys)
assert by < Y('campana', 'campana_id')

BADGE = {'pk': ['PK'], 'fk': ['FK'], 'fkx': ['FK'], 'uq': ['UQ'], 'pkfk': ['PK', 'FK'], 'fkuq': ['FK', 'UQ']}


def badge_svg(x, cy, key):
    if key == 'co':
        return f'<path class="er-co" d="M{x+9},{cy-5} L{x+14},{cy} L{x+9},{cy+5} L{x+4},{cy} Z"/>'
    out = []
    for i, lab in enumerate(BADGE[key]):
        bx = x + i * 20
        cls = 'b-' + ({'PK': 'pk', 'UQ': 'uq'}.get(lab) or ('fkx' if key == 'fkx' else 'fk'))
        out.append(f'<rect class="er-b {cls}" x="{bx}" y="{cy-7}" width="18" height="14" rx="3"/>'
                   f'<text class="er-bt {cls}-t" x="{bx+9}" y="{cy+3.5}" text-anchor="middle">{lab}</text>')
    return ''.join(out)


def box_svg(n):
    t = TABLES[n]
    x, y, h = L(n), T(n), H(n)
    a = AREA[t['area']][2]
    o = [f'<a class="er-a" href="#t-{n}" aria-label="{E(n)}: ver sus columnas">',
         f'<rect class="er-box" x="{x}" y="{y}" width="{W}" height="{h}" rx="5"/>',
         f'<path class="er-hd {a}" d="M{x},{y+HEAD} V{y+5} Q{x},{y} {x+5},{y} H{x+W-5} Q{x+W},{y} {x+W},{y+5} V{y+HEAD} Z"/>',
         f'<line class="er-sep" x1="{x}" y1="{y+HEAD}" x2="{x+W}" y2="{y+HEAD}"/>',
         f'<text class="er-tn" x="{x+10}" y="{y+20}">{E(n)}</text>']
    for i, c in enumerate(keyrows(n)):
        cy = y + HEAD + ROW * i + ROW // 2
        o.append(badge_svg(x + 8, cy, c['key']))
        cls = 'er-r er-r-co' if c['key'] == 'co' else 'er-r'
        o.append(f'<text class="{cls}" x="{x+52}" y="{cy+4}">{E(c["name"])}</text>')
    if extra(n):
        fy = y + HEAD + ROW * len(keyrows(n)) + 14
        word = 'columna' if extra(n) == 1 else 'columnas'
        o.append(f'<text class="er-ft" x="{x+52}" y="{fy}">+{extra(n)} {word} más</text>')
    o.append('</a>')
    return '\n'.join(o)


def legend_svg(x, y):
    o = []
    yy = y + 24
    o.append(f'<text class="er-lt" x="{x+12}" y="{yy}">Cómo leer el diagrama</text>')
    yy += 22
    for key, txt in [('pk', 'llave primaria'), ('fk', 'llave foránea con línea'),
                     ('fkx', 'llave foránea sin línea'), ('uq', 'valor único'),
                     ('co', 'comercio_id (entidad fuerte)')]:
        o.append(badge_svg(x + 12, yy - 4, key))
        o.append(f'<text class="er-l" x="{x+40}" y="{yy}">{txt}</text>')
        yy += 21
    yy += 6
    for kind, txt in [('||', 'exactamente uno'), ('o|', 'cero o uno'), ('o<', 'cero o muchos')]:
        o.append(f'<line class="er-e" x1="{x+12}" y1="{yy-4}" x2="{x+52}" y2="{yy-4}"/>')
        o.append(f'<line class="er-sep" x1="{x+52}" y1="{yy-12}" x2="{x+52}" y2="{yy+4}"/>')
        o.append(mark(kind, x + 52, yy - 4, -1, 'h'))
        o.append(f'<text class="er-l" x="{x+64}" y="{yy}">{txt}</text>')
        yy += 21
    yy += 6
    for code, name, tok, _ in AREAS:
        o.append(f'<rect class="er-hd {tok} er-chip" x="{x+12}" y="{yy-10}" width="18" height="12" rx="2"/>')
        o.append(f'<text class="er-l" x="{x+40}" y="{yy}">{name}</text>')
        yy += 19
    yy += 8
    for line in ['Las llaves hacia comercio y usuario', f'no se dibujan: las llevan {N_COMERCIO} y {N_USUARIO} tablas.',
                 'Clic en una tabla para ver sus columnas.']:
        o.append(f'<text class="er-ft" x="{x+12}" y="{yy}">{line}</text>')
        yy += 15
    h = yy - y + 4
    assert y + h < T('cliente') - 12
    return f'<rect class="er-legend" x="{x}" y="{y}" width="{W}" height="{h}" rx="6"/>' + ''.join(o)


labels_svg = ''.join(f'<text class="er-el" x="{f(lx)}" y="{f(ly)}">{E(t)}</text>' for lx, ly, t in LABELS)
ER_SVG = (f'<svg class="er" viewBox="0 0 {VW} {VH}" xmlns="http://www.w3.org/2000/svg" role="group" '
          f'aria-label="Modelo entidad-relación de Anaquel: {len(TABLES)} tablas y sus llaves foráneas.">'
          f'<rect class="er-bg" x="0" y="0" width="{VW}" height="{VH}"/>'
          + '\n'.join(EDGES) + labels_svg
          + '\n'.join(box_svg(n) for n in TABLES)
          + legend_svg(24, 440) + '</svg>')


# ---------------------------------------------------------------- figura de aislamiento
def iso_svg(con_disparador):
    if con_disparador:
        q1, q2 = 'comercio de la venta 10', 'comercio del producto 77'
        r1, r2 = '= 1', '= 2'
        res_cls, res = 'iso-ok', 'Se rechaza: 1 ≠ 2 (detalle_venta_mismo_comercio).'
        e2 = 'iso-e iso-e-no'
        r2cls = 'iso-r-no'
    else:
        q1, q2 = 'busca venta_id 10', 'busca producto_id 77'
        r1, r2 = 'sí existe', 'sí existe'
        res_cls, res = 'iso-bad', 'Se guarda: el renglón mezcla dos comercios.'
        e2 = 'iso-e'
        r2cls = 'iso-r-ok'
    aria = ('Con el disparador, el renglón que mezcla la venta del comercio 1 con el producto del comercio 2 se rechaza.'
            if con_disparador else
            'Sólo con llaves foráneas simples, el renglón que mezcla la venta del comercio 1 con el producto del comercio 2 se acepta.')
    return f'''<svg viewBox="0 0 460 300" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="{aria}">
<rect class="er-bg" x="0" y="0" width="460" height="300"/>
<rect class="er-box" x="30" y="18" width="400" height="52" rx="5"/>
<text class="iso-k" x="44" y="38">detalle_venta (renglón nuevo)</text>
<text class="iso-v" x="44" y="58">venta_id = 10 · producto_id = 77</text>
<path class="iso-e" fill="none" d="M120,70 V162"/><path class="iso-h" d="M114,154 L120,164 L126,154 Z"/>
<path class="{e2}" fill="none" d="M340,70 V162"/><path class="iso-h" d="M334,154 L340,164 L346,154 Z"/>
<text class="iso-q" x="130" y="104">{q1}</text><text class="iso-r iso-r-ok" x="130" y="122">{r1}</text>
<text class="iso-q" x="350" y="104">{q2}</text><text class="iso-r {r2cls}" x="350" y="122">{r2}</text>
<rect class="er-box" x="30" y="166" width="180" height="54" rx="5"/>
<text class="iso-k" x="44" y="188">venta 10</text><text class="iso-s" x="44" y="207">del comercio 1</text>
<rect class="er-box" x="250" y="166" width="180" height="54" rx="5"/>
<text class="iso-k" x="264" y="188">producto 77</text><text class="iso-s" x="264" y="207">del comercio 2</text>
<rect class="{res_cls}" x="30" y="240" width="400" height="40" rx="5"/>
<text class="iso-res" x="230" y="265" text-anchor="middle">{res}</text>
</svg>'''


# ---------------------------------------------------------------- html
LOGO = ('<svg class="logo" viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Anaquel">'
        '<rect width="512" height="512" rx="112" fill="#10265C"/>'
        '<g stroke="#FFFFFF" stroke-width="36" stroke-linecap="round" fill="none">'
        '<line x1="256" y1="96" x2="136" y2="412"/><line x1="256" y1="96" x2="376" y2="412"/></g>'
        '<line x1="168" y1="376" x2="344" y2="376" stroke="#FFFFFF" stroke-width="12"/>'
        '<rect x="191" y="328" width="38" height="42" rx="5" fill="#FFFFFF"/>'
        '<rect x="237" y="338" width="38" height="32" rx="5" fill="#FFFFFF"/>'
        '<rect x="283" y="334" width="38" height="36" rx="5" fill="#D0202F"/></svg>')


def code(s):
    return f'<code>{E(s)}</code>'


def badge_html(key):
    if key == 'co':
        return '<span class="b b-co" title="comercio_id">◆</span>'
    if key == 'gen':
        return '<span class="b b-gen">calc</span>'
    if key == '':
        return ''
    return ''.join(f'<span class="b {"b-pk" if l == "PK" else "b-uq" if l == "UQ" else ("b-fkx" if key == "fkx" else "b-fk")}">{l}</span>'
                   for l in BADGE[key])


def card(n):
    t = TABLES[n]
    a = AREA[t['area']]
    meta = []
    if t['new']:
        meta.append('<span class="nueva">tabla nueva</span>')
    if t['old']:
        meta.append(f'<span class="meta">antes {code(t["old"])}</span>')
    for ab in t['absorbs']:
        meta.append(f'<span class="meta meta-abs">absorbe {code(ab)}</span>')
    rows = []
    for c in t['cols']:
        sub = []
        if c['old'] == 'NUEVA':
            sub.append('<span class="nueva">nueva</span>')
        elif c['old'] and '.' in c['old']:
            sub.append(f'<span class="antes">viene de {code(c["old"])}</span>')
        elif c['old']:
            sub.append(f'<span class="antes">antes {code(c["old"])}</span>')
        if c['note']:
            sub.append(f'<span class="nota">{E(c["note"])}</span>')
        subhtml = f'<div class="sub">{"".join(sub)}</div>' if sub else ''
        rows.append(f'<tr><td class="ck">{badge_html(c["key"])}</td>'
                    f'<td class="cn"><code class="col">{E(c["name"])}</code>{subhtml}</td>'
                    f'<td class="ct">{E(c["typ"])}</td></tr>')
    return f'''<article class="card" id="t-{n}">
<header class="card-h {a[2]}"><h4><code>{E(n)}</code></h4><div class="metas">{"".join(meta)}</div></header>
<p class="card-d">{E(t["desc"])}</p>
<table class="cols"><tbody>{"".join(rows)}</tbody></table>
</article>'''


dict_html = []
for code_, name, tok, desc in AREAS:
    names = [n for n in TABLES if TABLES[n]['area'] == code_]
    dict_html.append(f'<section class="area"><h3><span class="swatch {tok}"></span>{name}</h3>'
                     f'<p class="area-d">{desc}</p><div class="cards">{"".join(card(n) for n in names)}</div></section>')

REVIEW = [
    ('Quitar comercio_id de recomendación, envío, detalle de venta y detalle de factura',
     'Hecho. Lo heredan de corrida_nocturna, campana, venta y factura_proveedor. Un disparador revisa que los dos padres de cada renglón sean del mismo comercio (ver «Reglas que cuida un disparador»).'),
    ('Relacionar la zona con el comercio',
     'municipio_zona_climatica viene precargada con todos los municipios de la zona que se cubre, por ejemplo las 16 alcaldías de la CDMX. Con la ubicación del cliente, el comercio guarda el id del municipio donde está, y la zona sale de ahí.'),
    ('El usuario que recibe va por factura, no por producto',
     'factura_proveedor.recibida_por_usuario_id. Se quita del renglón.'),
    ('Llave compuesta entre factura y producto en el detalle',
     'detalle_factura_proveedor se identifica por (factura_id, producto_id), sin llave propia ni comercio_id. Por consistencia, detalle_venta se identifica por (venta_id, producto_id).'),
    ('Las entidades débiles no llevan comercio_id',
     'Aplicado a las cuatro del primer punto. conciliacion_inventario lo conserva mientras se confirma si también es débil (pregunta 1).'),
    ('Tabla de roles con administrador y LLM',
     'Tabla rol con DUENO, ADMINISTRADOR, ANALISTA y LLM. Sólo DUENO autoriza; LLM es el agente; ADMINISTRADOR administra la plataforma y es el único usuario sin comercio.'),
]
review_rows = ''.join(f'<tr><th scope="row">{E(a)}</th><td>{I(b)}</td></tr>' for a, b in REVIEW)

TRIGGERS = [
    ('detalle_venta_mismo_comercio', 'Que un renglón junte la venta de un comercio con el producto de otro.', 'Venta del comercio 1 con producto del comercio 2.'),
    ('detalle_factura_mismo_comercio', 'Lo mismo con la factura y el producto.', 'Factura del comercio 1 con producto del comercio 2.'),
    ('envio_mismo_comercio', 'Enviar la campaña de un comercio al cliente de otro.', 'Campaña del comercio 1, cliente del comercio 2.'),
    ('recomendacion_mismo_comercio', 'Recomendar, en la corrida de un comercio, un producto de otro.', 'Corrida del comercio 1, producto del comercio 2.'),
    ('exigir_firma_del_dueno', 'Que decida alguien que no es DUENO del comercio de la corrida.', 'Decide el usuario LLM; decide el dueño del comercio 2.'),
    ('usuario_firmante_inmutable', 'Cambiarle el rol o el comercio a quien ya decidió (antes lo impedía la llave compuesta).', 'Pasar a ANALISTA al dueño que aprobó.'),
    ('comercio_inmutable', 'Mover a otro comercio una categoría, temporada, producto, venta, factura, conteo, corrida, campaña o cliente. Nunca se mueven: sus renglones débiles no guardan el comercio.', 'Cambiar el comercio de un producto con ventas.'),
    ('decision_inmutable', 'Cambiar una decisión ya tomada.', 'Pasar de APROBADA a RECHAZADA.'),
    ('exigir_autorizacion_aprobada', 'Crear una campaña sin recomendación APROBADA. Ya existía; ahora lee estado_decision.', 'Campaña de una recomendación PENDIENTE.'),
    # BIT-022 (20/oct): relaciones entre entidades fuertes que la v1 cuidaba con
    # llaves compuestas y que la lista original no nombraba.
    ('producto_mismo_comercio', 'Que un producto use la categoría o la temporada de otro comercio.', 'Producto del comercio 1 con categoría del comercio 2.'),
    ('conciliacion_mismo_comercio', 'Contar un producto de otro comercio, o que confirme el conteo alguien de otro comercio.', 'Conteo del comercio 1 sobre un producto del comercio 2.'),
    ('factura_mismo_comercio', 'Que reciba la factura alguien de otro comercio.', 'Factura del comercio 1 recibida por el dueño del comercio 2.'),
    ('campana_mismo_comercio', 'Lanzar una campaña con la recomendación de otro comercio.', 'Campaña del comercio 2 sobre una recomendación aprobada del comercio 1.'),
]
trig_rows = ''.join(f'<tr><td><code>{a}</code></td><td>{I(b)}</td><td>{I(c)}</td></tr>' for a, b, c in TRIGGERS)

GONE = [
    ('zona_climatica', 'Catálogo de cinco valores fijos.', 'CHECK en municipio_zona_climatica.zona_climatica. El comercio obtiene su zona por su municipio.'),
    ('ciclo_producto', 'Catálogo de tres valores fijos.', 'CHECK en producto.ciclo_producto.'),
    ('salida', 'Catálogo de cuatro valores fijos.', 'CHECK en recomendacion.accion_recomendada. Si se aprueba DAR_DE_BAJA (SC-008), se agrega al CHECK.'),
    ('afinidad_climatica', 'Una fila podía colgar de una categoría o de un producto: dos llaves opcionales en la misma tabla.',
     'categoria.afinidad_climatica y, si un producto es la excepción, producto.afinidad_climatica_propia, cada una con la zona para la que se declaró. Se deja de guardar quién la declaró y cuándo.'),
    ('autorizacion', 'Uno a uno con recomendación: cada propuesta tenía a lo más una decisión.',
     'recomendacion.estado_decision, decidido_por_usuario_id, fecha_hora_decision y motivo_rechazo.'),
    ('resultado_campana', 'Uno a uno con campaña.', 'campana.unidades_vendidas, ingreso_obtenido, capital_recuperado y fecha_hora_calculo_resultado.'),
]
gone_rows = ''.join(f'<tr><td><code>{a}</code></td><td>{I(b)}</td><td>{I(c)}</td></tr>' for a, b, c in GONE)

KEYS_GONE = [
    ('comercio_id en recomendacion, envio_campana, detalle_venta y detalle_factura_proveedor', 'Lo heredan de su entidad fuerte.'),
    ('UNIQUE (comercio_id, <id>) en las tablas fuertes', 'Sólo servía de destino a las llaves compuestas con comercio_id. Ahora el comercio lo revisan los disparadores de «Reglas que cuida un disparador».'),
    ('venta_renglon_id y factura_renglon_id', 'Los detalles se identifican por (venta_id, producto_id) y (factura_id, producto_id).'),
    ('autorizacion.rol_firmante y UNIQUE (comercio_id, usuario_id, rol)', 'Lo reemplazan exigir_firma_del_dueno y usuario_firmante_inmutable.'),
    ('comercio.zona_climatica', 'Sale del municipio del comercio.'),
    ('factura_renglon.revisado_por', 'Pasa a factura_proveedor.recibida_por_usuario_id.'),
    ('fuente_publica.es_publica', 'Siempre era verdadero: CHECK (es_publica). El nombre de la tabla ya lo dice.'),
    ('recomendacion.precio_minimo', 'No se va: sigue calculada, como en la v1, con GREATEST(precio_minimo_por_costo, precio_minimo_por_descuento).'),
]
keys_gone = ''.join(f'<li><code>{E(a)}</code>. {I(b)}</li>' for a, b in KEYS_GONE)

KEYS_NEW = [
    ('PRIMARY KEY (venta_id, producto_id) y (factura_id, producto_id)', 'Llaves de las dos entidades débiles de detalle.'),
    ('comercio.municipio_zona_id → municipio_zona_climatica', 'El comercio queda ligado a su municipio.'),
    ('usuario.codigo_rol → rol', 'El rol deja de ser texto libre con CHECK.'),
    ('UNIQUE (comercio_id, folio_ticket_pos) en venta', 'Cargar dos veces el mismo ticket ya no duplica la venta.'),
]
keys_new = ''.join(f'<li><code>{E(a)}</code>. {I(b)}</li>' for a, b in KEYS_NEW)

NAMING = [
    ('Tablas', 'En singular y con el nombre completo de lo que guardan. Una tabla puente lleva el nombre de las dos que une.',
     ['venta_renglon → detalle_venta', 'conciliacion → conciliacion_inventario', 'recomendacion_referencia']),
    ('Llaves', 'La primaria es el nombre corto de la entidad + _id. Una entidad débil no tiene llave propia: la forman las llaves de las entidades de las que depende. La foránea se llama igual que la primaria a la que apunta; si el papel no es obvio, el papel va antes.',
     ['fuente_precio_publica → fuente_id', '(venta_id, producto_id)', 'decidido_por_usuario_id']),
    ('Fechas', 'fecha_<evento> cuando basta el día (DATE); fecha_hora_<evento> cuando importa el momento (TIMESTAMPTZ). Ya no hay columnas que terminen en _en.',
     ['creado_en → fecha_alta', 'vendida_en → fecha_hora_venta']),
    ('Dinero y cantidades', 'Dicen de qué son.', ['precio_original → precio_venta_regular', 'cantidad → cantidad_vendida', 'existencia_fisica → existencia_contada']),
    ('Sí o no', 'Empiezan con es_, esta_ o permite_.', ['activo → esta_activo', 'admite_fraccion → permite_decimales']),
    ('Estados y tipos', 'Siempre con su sujeto. Nunca estado a secas: en el catálogo de municipios, «estado» era la entidad federativa.',
     ['estado → estado_corrida', 'estado → entidad_federativa', 'salida → accion_recomendada']),
    ('Abreviaturas', 'Sólo las que todos conocen: rfc, sku, url, ocr, llm.', ['max_envios_mes → maximo_envios_por_mes']),
]
naming_rows = ''.join(
    f'<tr><th scope="row">{E(a)}</th><td>{I(b)}</td><td class="ej">{"".join(f"<code>{E(x)}</code>" for x in ex)}</td></tr>'
    for a, b, ex in NAMING)

QUESTIONS = [
    '¿Considera débil a conciliacion_inventario? Depende del producto. Si es así, también pierde comercio_id y un disparador revisa que quien confirma el conteo sea del comercio del producto. Respuesta (20/oct): aprobó la maqueta como está; se queda fuerte, con comercio_id.',
    '¿También envio_campana sin llave propia, con (campana_id, cliente_id) como llave primaria? Esa pareja ya es única, como en los detalles. Respuesta (20/oct): se queda con envio_id y la pareja única.',
    '¿Le parece bien que la decisión del dueño viva en recomendacion, o la prefiere en una tabla aparte para separar lo que propone el agente de lo que decide el dueño? Respuesta (20/oct): vive en recomendacion.',
]
q_html = ''.join(f'<li>{I(q)}</li>' for q in QUESTIONS)

CSS_LIGHT = r'''
/* Layout: columna de lectura de ~68ch; el diagrama y el diccionario usan el ancho completo (1180px). */
:root {
  --paper: #F2F4F8;
  --surface: #FFFFFF;
  --ink: #14203D;
  --muted: #5A6580;
  --line: #D3DAE7;
  --navy: #10265C;
  --navy-ink: #FFFFFF;
  --red: #C81E2C;
  --edge: #5B6782;
  --fk-fill: #E2E8F5;
  --ok: #1C7444;
  --ok-fill: #E1F2E8;
  --bad: #B0212D;
  --bad-fill: #FBE4E6;
  --a1: #E4E7EE; --a2: #D8E5F7; --a3: #DBEFE1; --a4: #F3E8D1; --a5: #E7DFF4; --a6: #F5DEE1;
  --f-display: "Didact Gothic", "Century Gothic", "Futura", sans-serif;
  --f-body: "Public Sans", "Segoe UI", system-ui, sans-serif;
  --f-mono: "IBM Plex Mono", "Cascadia Mono", Consolas, ui-monospace, monospace;
}
'''
CSS_DARK = r'''
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    color-scheme: dark;
    --paper: #0B1222; --surface: #121B31; --ink: #E3E8F3; --muted: #9AA5C0; --line: #2B3756;
    --navy: #A4B9F2; --navy-ink: #0B1222; --red: #FF737D; --edge: #8E9AB7; --fk-fill: #233152;
    --ok: #6FD39B; --ok-fill: #15301F; --bad: #FF8089; --bad-fill: #3A1A1F;
    --a1: #262E43; --a2: #1C3150; --a3: #1B3829; --a4: #3A301C; --a5: #2E2747; --a6: #3F242A;
  }
}
:root[data-theme="dark"] {
  color-scheme: dark;
  --paper: #0B1222; --surface: #121B31; --ink: #E3E8F3; --muted: #9AA5C0; --line: #2B3756;
  --navy: #A4B9F2; --navy-ink: #0B1222; --red: #FF737D; --edge: #8E9AB7; --fk-fill: #233152;
  --ok: #6FD39B; --ok-fill: #15301F; --bad: #FF8089; --bad-fill: #3A1A1F;
  --a1: #262E43; --a2: #1C3150; --a3: #1B3829; --a4: #3A301C; --a5: #2E2747; --a6: #3F242A;
}
'''
CSS_BASE = r'''
body { background: var(--paper); color: var(--ink); font: 400 15.5px/1.6 var(--f-body); }
.wrap { max-width: 1180px; margin: 0 auto; padding-inline: 20px; padding-block: 36px 72px; display: grid; gap: 56px; }
.prose { max-width: 68ch; }
h1, h2, h3, h4 { text-wrap: balance; margin: 0; }
h1 { font: 400 clamp(30px, 4.4vw, 44px)/1.12 var(--f-display); letter-spacing: -0.01em; }
h2 { font: 400 27px/1.2 var(--f-display); display: flex; gap: 12px; align-items: baseline; }
h2 .n { font: 600 14px/1 var(--f-mono); color: var(--red); }
h3 { font: 700 17px/1.3 var(--f-body); display: flex; gap: 10px; align-items: center; }
p { margin: 0; }
code { font-family: var(--f-mono); font-size: 0.88em; }
section > .head { display: grid; gap: 10px; margin-bottom: 20px; }
.lede { color: var(--muted); max-width: 68ch; }

/* encabezado */
.top { display: grid; gap: 18px; }
.brand { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.logo { width: 44px; height: 44px; flex: none; }
.eyebrow { font: 600 12px/1.3 var(--f-body); letter-spacing: 0.09em; text-transform: uppercase; color: var(--muted); }
.status { font: 600 12px/1 var(--f-body); letter-spacing: 0.06em; text-transform: uppercase; color: var(--red);
  border: 1px solid currentColor; border-radius: 999px; padding: 5px 10px; }
.facts { display: flex; flex-wrap: wrap; gap: 8px 26px; font-variant-numeric: tabular-nums; color: var(--muted);
  border-top: 1px solid var(--line); padding-top: 14px; }
.facts b { color: var(--ink); font-weight: 700; }

/* tablas de texto */
.tw { overflow-x: auto; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
table.t { border-collapse: collapse; width: 100%; min-width: 720px; }
table.t th, table.t td { text-align: left; vertical-align: top; padding: 12px 16px; border-bottom: 1px solid var(--line); }
table.t tr:last-child th, table.t tr:last-child td { border-bottom: 0; }
table.t thead th { font: 600 12px/1.3 var(--f-body); letter-spacing: 0.07em; text-transform: uppercase; color: var(--muted); }
table.t tbody th { font-weight: 700; width: 24%; }
table.t td.ej { display: flex; flex-direction: column; gap: 4px; }
table.t td.ej code { white-space: nowrap; }

/* diagrama */
.fig { margin: 0; display: grid; gap: 12px; }
.er-scroll { overflow-x: auto; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
svg.er { display: block; width: 100%; min-width: 960px; height: auto; }
figcaption { color: var(--muted); font-size: 14px; max-width: 80ch; }
.er-bg { fill: var(--surface); }
.er-box { fill: var(--surface); stroke: var(--line); stroke-width: 1.2; }
.er-hd { stroke: none; }
.a1 { fill: var(--a1); background: var(--a1); } .a2 { fill: var(--a2); background: var(--a2); }
.a3 { fill: var(--a3); background: var(--a3); } .a4 { fill: var(--a4); background: var(--a4); }
.a5 { fill: var(--a5); background: var(--a5); } .a6 { fill: var(--a6); background: var(--a6); }
.er-sep { stroke: var(--line); stroke-width: 1; }
.er-tn { font: 600 12.5px var(--f-mono); fill: var(--ink); }
.er-r { font: 400 11.5px var(--f-mono); fill: var(--ink); }
.er-r-co { fill: var(--muted); }
.er-ft { font: 400 10.5px var(--f-body); fill: var(--muted); }
.er-el { font: 400 10.5px var(--f-mono); fill: var(--muted); }
.er-e { stroke: var(--edge); stroke-width: 1.3; fill: none; }
.er-m { stroke: var(--edge); stroke-width: 1.3; }
.er-o { fill: var(--surface); stroke: var(--edge); stroke-width: 1.3; }
.er-co { fill: var(--red); }
.er-b { stroke-width: 1; }
.b-pk { fill: var(--navy); stroke: var(--navy); }
.b-fk { fill: var(--fk-fill); stroke: var(--fk-fill); }
.b-fkx { fill: var(--surface); stroke: var(--edge); }
.b-uq { fill: var(--surface); stroke: var(--muted); stroke-dasharray: 2 2; }
.er-bt { font: 600 8.5px var(--f-mono); }
.b-pk-t { fill: var(--navy-ink); } .b-fk-t, .b-fkx-t { fill: var(--ink); } .b-uq-t { fill: var(--muted); }
.er-legend { fill: var(--paper); stroke: var(--line); }
.er-lt { font: 700 12.5px var(--f-body); fill: var(--ink); }
.er-l { font: 400 11.5px var(--f-body); fill: var(--ink); }
.er-chip { stroke: var(--line); }
.er-a { cursor: pointer; }
.er-a:hover .er-box, .er-a:focus-visible .er-box { stroke: var(--navy); stroke-width: 2; }
.er-a:focus { outline: none; }

/* aislamiento */
.iso { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr)); gap: 20px; }
.iso figure { margin: 0; display: grid; gap: 8px; background: var(--surface); border: 1px solid var(--line); border-radius: 8px; padding: 16px; }
.iso h4 { font: 700 15px/1.3 var(--f-body); }
.iso svg { width: 100%; height: auto; display: block; }
.iso-k { font: 600 12.5px var(--f-mono); fill: var(--ink); }
.iso-v { font: 400 12px var(--f-mono); fill: var(--ink); }
.iso-s { font: 400 12px var(--f-body); fill: var(--muted); }
.iso-q { font: 400 11.5px var(--f-mono); fill: var(--ink); }
.iso-r { font: 700 11.5px var(--f-body); }
.iso-r-ok { fill: var(--muted); } .iso-r-no { fill: var(--ok); }
.iso-e { stroke: var(--edge); stroke-width: 1.5; }
.iso-e-no { stroke: var(--ok); stroke-dasharray: 5 4; }
.iso-h { fill: var(--edge); }
.iso-ok { fill: var(--ok-fill); stroke: var(--ok); }
.iso-bad { fill: var(--bad-fill); stroke: var(--bad); }
.iso-res { font: 700 13px var(--f-body); fill: var(--ink); }

/* diccionario */
.areas { display: grid; gap: 40px; }
.area { display: grid; gap: 6px; }
.area-d { color: var(--muted); margin-bottom: 10px; }
.swatch { width: 14px; height: 14px; border-radius: 3px; border: 1px solid var(--line); flex: none; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 500px), 1fr)); gap: 18px; align-items: start; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: 8px; overflow: hidden; scroll-margin-top: 16px; }
.card:target { outline: 2px solid var(--navy); outline-offset: 2px; }
.card-h { display: flex; flex-wrap: wrap; gap: 6px 12px; align-items: baseline; justify-content: space-between; padding: 10px 14px; }
.card-h h4 code { font: 600 15px var(--f-mono); color: var(--ink); }
.metas { display: flex; flex-wrap: wrap; gap: 6px; }
.meta { font-size: 12.5px; color: var(--ink); opacity: 0.85; }
.meta-abs { font-weight: 600; }
.card-d { padding: 10px 14px 4px; color: var(--muted); font-size: 14px; }
table.cols { border-collapse: collapse; width: 100%; }
table.cols td { padding: 7px 14px; border-top: 1px solid var(--line); vertical-align: top; }
table.cols tr:first-child td { border-top: 0; }
td.ck { width: 52px; white-space: nowrap; padding-right: 0; }
td.ct { font: 400 12px/1.9 var(--f-mono); color: var(--muted); white-space: nowrap; text-align: right; }
code.col { font: 500 13.5px var(--f-mono); color: var(--ink); overflow-wrap: anywhere; }
.sub { display: flex; flex-direction: column; gap: 2px; margin-top: 3px; }
.antes, .nota { font-size: 12.5px; color: var(--muted); line-height: 1.45; }
.antes code { text-decoration: line-through; text-decoration-color: var(--red); }
.nueva { font: 700 11px/1 var(--f-body); letter-spacing: 0.06em; text-transform: uppercase; color: var(--red); }
.b { display: inline-block; min-width: 22px; text-align: center; font: 600 10px/16px var(--f-mono); border-radius: 3px; padding: 0 3px; margin-right: 2px; }
span.b-pk { background: var(--navy); color: var(--navy-ink); }
span.b-fk { background: var(--fk-fill); color: var(--ink); }
span.b-fkx { border: 1px solid var(--edge); color: var(--ink); line-height: 14px; }
span.b-uq { border: 1px dashed var(--muted); color: var(--muted); line-height: 14px; }
span.b-co { color: var(--red); font-size: 12px; min-width: 16px; }
span.b-gen { border: 1px solid var(--line); color: var(--muted); line-height: 14px; font-family: var(--f-body); }

/* listas */
ul.plain { margin: 0; padding-left: 1.2em; display: grid; gap: 8px; max-width: 80ch; }
ol.q { margin: 0; padding-left: 1.4em; display: grid; gap: 14px; max-width: 72ch; }
ol.q li::marker { font: 600 14px var(--f-mono); color: var(--red); }
.sub-h { margin-top: 26px; margin-bottom: 10px; }
footer { color: var(--muted); font-size: 13px; border-top: 1px solid var(--line); padding-top: 16px; }
a { color: var(--navy); }
a:focus-visible { outline: 2px solid var(--navy); outline-offset: 2px; }
@media (prefers-reduced-motion: no-preference) { html { scroll-behavior: smooth; } }
'''
CSS = CSS_LIGHT + CSS_DARK + CSS_BASE

PAGE = f'''<title>Modelo ER de Anaquel v2</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Didact+Gothic&family=IBM+Plex+Mono:wght@400;500;600&family=Public+Sans:wght@400;600;700&display=swap">
<style>{CSS}</style>
<main class="wrap">
<header class="top">
  <div class="brand">{LOGO}<span class="eyebrow">Anaquel · Base de Datos · Grupo 501</span><span class="status">Propuesta sin aplicar</span></div>
  <h1>Modelo entidad-relación, versión 2</h1>
  <p class="lede">Incorpora la revisión del 8 de octubre de 2026. Las entidades débiles ya no repiten comercio_id, los detalles se identifican por sus dueños, hay una tabla de roles y el comercio queda ligado a su municipio. Nada de esto está aplicado todavía al esquema.</p>
  <div class="facts"><span><b>26 → {len(TABLES)}</b> tablas</span><span><b>6</b> absorbidas, <b>1</b> nueva</span><span><b>{N_TABLES_RENAMED}</b> tablas y <b>{N_RENAMED}</b> columnas con nombre nuevo</span><span><b>{N_NEW}</b> columnas nuevas</span><span><b>{N_COLS}</b> columnas en total</span></div>
</header>

<section>
  <div class="head"><h2><span class="n">1</span>Revisión del 8 de octubre</h2>
  <p class="lede">Lo que pidió y cómo quedó en esta versión.</p></div>
  <div class="tw"><table class="t"><thead><tr><th>Lo que pidió</th><th>Cómo quedó</th></tr></thead><tbody>{review_rows}</tbody></table></div>
</section>

<section>
  <div class="head"><h2><span class="n">2</span>Diagrama entidad-relación</h2>
  <p class="lede">Cada tabla muestra sus llaves; el resto de sus columnas está en el diccionario (sección 5). Haga clic en una tabla para ir a sus columnas.</p></div>
  <figure class="fig">
    <div class="er-scroll">{ER_SVG}</div>
    <figcaption>Las líneas son llaves foráneas en notación pata de gallo. La pata llega al renglón de la llave foránea, salvo cuando una tabla está encima de la otra; ahí la línea lleva el nombre de la llave. Las cinco tablas que dependen de producto comparten una sola línea de salida.</figcaption>
  </figure>
</section>

<section>
  <div class="head"><h2><span class="n">3</span>Cómo se sigue impidiendo mezclar comercios</h2>
  <p class="lede">Las entidades débiles heredan el comercio de su entidad fuerte, así que ya no lo repiten. El riesgo que cuidaban las llaves compuestas sigue ahí: un renglón de detalle_venta apunta a dos tablas a la vez, la venta y el producto, y una llave foránea simple revisa cada una por separado. Ahora lo revisa un disparador dentro de la base: un INSERT escrito a mano se rechaza igual.</p></div>
  <div class="iso">
    <figure><h4>Sólo llaves foráneas simples</h4>{iso_svg(False)}</figure>
    <figure><h4>Con el disparador detalle_venta_mismo_comercio</h4>{iso_svg(True)}</figure>
  </div>
  <h3 class="sub-h">Reglas que cuida un disparador</h3>
  <p class="prose" style="margin-bottom:14px">Cada una se prueba con dos comercios: con uno solo, el error no se ve.</p>
  <div class="tw"><table class="t"><thead><tr><th>Disparador</th><th>Qué impide</th><th>Prueba negativa</th></tr></thead><tbody>{trig_rows}</tbody></table></div>
</section>

<section>
  <div class="head"><h2><span class="n">4</span>Convención de nombres</h2>
  <p class="lede">Una regla por tipo de dato, aplicada a todo el esquema. Lo tachado en el diccionario es el nombre anterior.</p></div>
  <div class="tw"><table class="t"><thead><tr><th>Qué</th><th>Regla</th><th>Ejemplos</th></tr></thead><tbody>{naming_rows}</tbody></table></div>
</section>

<section>
  <div class="head"><h2><span class="n">5</span>Diccionario de tablas</h2>
  <p class="lede">Las {len(TABLES)} tablas, agrupadas por área, con cada columna, su tipo y su nombre anterior.</p></div>
  <div class="areas">{"".join(dict_html)}</div>
</section>

<section>
  <div class="head"><h2><span class="n">6</span>Lo que desaparece y lo que se agrega</h2>
  <p class="lede">Ninguna regla se pierde: lo que hacían estas tablas lo hace ahora un CHECK, una columna o un disparador.</p></div>
  <div class="tw"><table class="t"><thead><tr><th>Tabla de la versión 1</th><th>Por qué sobraba</th><th>Dónde quedó</th></tr></thead><tbody>{gone_rows}</tbody></table></div>
  <h3 class="sub-h">Columnas y llaves que se quitan</h3>
  <ul class="plain">{keys_gone}</ul>
  <h3 class="sub-h">Llaves que se agregan</h3>
  <ul class="plain">{keys_new}</ul>
</section>

<section>
  <div class="head"><h2><span class="n">7</span>Preguntas para la revisión</h2>
  <p class="lede">Lo que falta confirmar antes de aplicar los cambios al esquema.</p></div>
  <ol class="q">{q_html}</ol>
</section>

<footer>Equipo Anaquel · Grupo 501 · Proyecto Integrador, Ingeniería en Sistemas Computacionales. Documento de trabajo del 8 de octubre de 2026, hecho a partir de db/schema.sql (revisión del 23 de septiembre de 2026). Incorpora la revisión del equipo (db/REVISION_V2.md) y la de la maestra del 8 de octubre (db/REVISION_MAESTRA.md).</footer>
</main>
'''

# ---------------------------------------------------------------- versión para imprimir
# Sólo el diagrama, en dos hojas carta verticales que se unen. El corte pasa entre la segunda y la
# tercera columna de tablas, así que ninguna tabla se parte; las líneas que cruzan el corte siguen
# en la otra hoja a la misma escala. El PDF se saca con Chrome: --print-to-pdf sobre
# <salida>_imprimir.html (comando en V2_LEEME.md).
MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
         'septiembre', 'octubre', 'noviembre', 'diciembre']
HOY = date.today()
FECHA_IMPRESION = f'{HOY.day} de {MESES[HOY.month - 1]} de {HOY.year}'

CORTE = 586                     # la columna 1 termina en 546 y la 2 empieza en 626
IZQ0, DER1 = 10, 1162
ARRIBA, ALTO = 30, 988
assert CORTE - IZQ0 == DER1 - CORTE     # mitades del mismo ancho: la misma escala en las dos hojas
for n in TABLES:
    assert R(n) <= CORTE - 20 or L(n) >= CORTE + 20, ('una tabla cruza el corte', n)
    assert IZQ0 <= L(n) and R(n) <= DER1 and ARRIBA <= T(n) and B(n) <= ARRIBA + ALTO, n
for lx, ly, t in LABELS:
    assert lx + len(t) * 6.4 <= CORTE - 4 or lx >= CORTE + 4, ('una etiqueta cruza el corte', t)


def mitad(x0):
    return (ER_SVG.replace(f'viewBox="0 0 {VW} {VH}"', f'viewBox="{x0} {ARRIBA} {CORTE - IZQ0} {ALTO}"', 1)
            .replace('Clic en una tabla para ver sus columnas.', 'Columnas completas: en la maqueta.'))


SVG_IZQ, SVG_DER = mitad(IZQ0), mitad(CORTE)
assert 'Clic en una tabla' not in SVG_IZQ

PRINT_CSS = r'''
@page { size: letter portrait; margin: 10mm; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { background: #FFFFFF; font-size: 8pt; line-height: 1.4; }
.hoja { height: 257mm; display: grid; gap: 4mm; break-after: page; overflow: hidden; }
.hoja:last-of-type { break-after: auto; }
.hoja-1 { grid-template-columns: 1fr auto; }
.hoja-2 { grid-template-columns: auto 1fr; }
.hoja svg.er { height: 257mm; width: auto; min-width: 0; display: block; }
.hoja-1 svg.er { border-right: 0.4mm dashed #8E9AB7; }
.hoja-2 svg.er { border-left: 0.4mm dashed #8E9AB7; }
.lado { display: flex; flex-direction: column; gap: 3mm; }
.hoja-2 .lado { justify-content: flex-end; }
.lado .logo { width: 11mm; height: 11mm; }
.lado .eyebrow { font-size: 6.5pt; }
.lado h1 { font-size: 15pt; line-height: 1.15; }
.lado .status { font-size: 6.5pt; align-self: flex-start; padding: 1mm 2mm; }
.lado ul { list-style: none; margin: 0; padding: 2.5mm 0 0; display: grid; gap: 1.2mm; color: var(--muted);
  border-top: 0.3mm solid var(--line); font-variant-numeric: tabular-nums; }
.lado ul b { color: var(--ink); }
.lado .hoja-n { font: 600 7pt var(--f-body); letter-spacing: 0.08em; text-transform: uppercase; color: var(--red); }
.lado p { color: var(--muted); font-size: 7pt; }
'''

PRINT_PAGE = f'''<title>Modelo ER de Anaquel v2 · impresión</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Didact+Gothic&family=IBM+Plex+Mono:wght@400;500;600&family=Public+Sans:wght@400;600;700&display=swap">
<style>{CSS_LIGHT}{CSS_BASE}{PRINT_CSS}</style>
<section class="hoja hoja-1">
  <div class="lado">
    {LOGO}
    <span class="eyebrow">Anaquel · Base de Datos · Grupo 501</span>
    <h1>Modelo entidad-relación, versión 2</h1>
    <span class="status">Propuesta sin aplicar</span>
    <ul><li><b>{len(TABLES)}</b> tablas</li><li><b>{N_COLS}</b> columnas</li><li>Con la revisión del 8 de octubre de 2026</li></ul>
    <span class="hoja-n">Hoja 1 de 2 · izquierda</span>
    <p>Para unirlas, recorta esta hoja por la línea punteada y empálmala sobre el margen izquierdo de la hoja 2, de modo que las dos líneas punteadas coincidan. Las líneas del diagrama continúan de una hoja a la otra.</p>
    <p>Impreso el {FECHA_IMPRESION}.</p>
  </div>
  {SVG_IZQ}
</section>
<section class="hoja hoja-2">
  {SVG_DER}
  <div class="lado">
    <span class="hoja-n">Hoja 2 de 2 · derecha</span>
    <p>Las líneas son llaves foráneas en notación pata de gallo. La pata llega al renglón de la llave foránea; cuando una tabla está encima de otra, la línea lleva el nombre de la llave.</p>
    <p>Las cinco tablas que dependen de producto comparten una línea de salida, que cruza el corte entre las dos hojas.</p>
  </div>
</section>
'''

OUT_PRINT = os.path.splitext(OUT)[0] + '_imprimir.html'
for dest, page in ((OUT, PAGE), (OUT_PRINT, PRINT_PAGE)):
    with open(dest, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(page)
print(f'OK {OUT}')
print(f'OK {OUT_PRINT}')
print(f'tablas={len(TABLES)} columnas={N_COLS} renombradas={N_RENAMED} movidas={N_MOVED} nuevas={N_NEW} '
      f'tablas_renombradas={N_TABLES_RENAMED} con_comercio={N_COMERCIO} fk_usuario={N_USUARIO} lienzo={VW}x{VH}')
