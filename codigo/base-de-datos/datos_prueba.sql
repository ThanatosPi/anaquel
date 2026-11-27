-- =====================================================================
-- Anaquel · datos sintéticos y pruebas de invariantes (esquema v2)
--
-- Tres partes:
--   A) DOS comercios de juguete con lo mínimo para que el motor tenga qué leer
--   B) los INSERT y UPDATE que TIENEN QUE FALLAR
--   C) lo que la base TIENE QUE resolver bien
--
-- La parte B es la que importa. Un esquema se valida enseñando lo que
-- rechaza, no lo que acepta: si alguno de esos bloques pasa, la invariante
-- correspondiente no está protegida por la base y hay que arreglarla.
--
-- Hay DOS comercios a propósito (BIT-013). Con uno solo, una relación que
-- acepta filas de otro comercio pasa todas las pruebas sin que nadie lo note:
-- así estuvo el esquema hasta la revisión de AEGIS (MSG-005). Desde BIT-021 el
-- aislamiento lo cuidan disparadores, y cada uno tiene aquí su prueba.
--
-- simulador/verificar.py no sólo cuenta los errores: comprueba que cada uno
-- falle POR la restricción o el disparador que dice su comentario. Un bloque
-- que truena por otra razón no prueba nada. Los rechazos que se movieron desde
-- la v1, y por qué, están en db/V2_LEEME.md.
--
-- Datos 100 % sintéticos. Ningún dato real de ningún comercio identificable
-- entra a este repositorio (regla del proyecto). Los 21 municipios ya vienen
-- precargados en schema.sql.
-- =====================================================================

-- =====================================================================
-- A · COMERCIOS DE PRUEBA
-- =====================================================================
BEGIN;

-- Comercio 1: el municipio salió del permiso de ubicación (GPS, con
-- coordenadas, Mérida). Comercio 2: el dueño negó el permiso y eligió Toluca a
-- mano. Existe para que las pruebas de aislamiento tengan contra qué chocar.
INSERT INTO comercio (municipio_zona_id, nombre_comercio, giro_comercial, origen_zona_climatica, latitud, longitud) VALUES
    (1, 'Ferretería de prueba',    'ferretería',    'GPS',    20.967200, -89.624700),
    (2, 'Refaccionaria de prueba', 'refaccionaria', 'MANUAL', NULL,      NULL);

INSERT INTO usuario (comercio_id, codigo_rol, correo_electronico, nombre_completo, hash_contrasena) VALUES
    (1,    'DUENO',         'dueno@ejemplo.invalid',   'Dueño de prueba',           'no-es-un-hash-real'),  -- 1
    (1,    'LLM',           'agente@ejemplo.invalid',  'Agente Anaquel',            'no-es-un-hash-real'),  -- 2
    (2,    'DUENO',         'dueno2@ejemplo.invalid',  'Dueño de la refaccionaria', 'no-es-un-hash-real'),  -- 3
    (NULL, 'ADMINISTRADOR', 'admin@ejemplo.invalid',   'Administración Anaquel',    'no-es-un-hash-real');  -- 4

-- BIT-012. La categoría fija la afinidad del caso general, siempre para una zona.
INSERT INTO categoria (comercio_id, nombre_categoria, afinidad_climatica, zona_climatica_afinidad) VALUES
    (1, 'Herramienta manual', NULL,    NULL),         -- 1
    (1, 'Jardinería',         'ALTA',  'TROPICAL'),   -- 2
    (1, 'Pintura',            'MEDIA', 'TROPICAL'),   -- 3
    (2, 'Frenos',             NULL,    NULL);         -- 4

INSERT INTO temporada (comercio_id, nombre_temporada, dia_inicio, mes_inicio, dia_fin, mes_fin) VALUES
    (1, 'Lluvias',       1,  6, 30,  9),
    (1, 'Fin de año',    1, 11, 15,  1);   -- cruza el año: lo resuelve el motor, no la base

INSERT INTO producto
    (comercio_id, codigo_sku, nombre_producto, categoria_id, ciclo_producto, temporada_id,
     afinidad_climatica_propia, zona_climatica_afinidad_propia,
     precio_venta_regular, costo_compra_unitario, stock_minimo, existencia_sistema, fecha_caducidad) VALUES
    -- permanente que rota bien: candidato a RESURTIR
    (1, 'MAR-001', 'Martillo de uña 16 oz',   1, 'PERMANENTE', NULL, NULL,   NULL,       180.00, 110.00, 5,  12, NULL),
    -- permanente parado: candidato a LIQUIDAR
    (1, 'LLA-014', 'Llave Stillson 14"',      1, 'PERMANENTE', NULL, NULL,   NULL,       520.00, 390.00, 2,   7, NULL),
    -- estacional fuera de ventana: candidato a RETENER, nunca a liquidar. Su
    -- afinidad propia manda sobre la de Pintura (lección GEA_ECE).
    (1, 'IMP-003', 'Impermeabilizante 19 L',  3, 'ESTACIONAL',    1, 'ALTA', 'TROPICAL', 1250.00, 890.00, 3,  9, NULL),
    -- perecedero
    (1, 'SEL-007', 'Sellador acrílico 300 ml',3, 'PERECEDERO', NULL, NULL,   NULL,        95.00,  62.00, 10, 20,
     CURRENT_DATE + 120),
    -- el producto del OTRO comercio
    (2, 'BAL-100', 'Balatas delanteras',      4, 'PERMANENTE', NULL, NULL,   NULL,       640.00, 410.00, 4,   8, NULL);

-- Una venta, para tener contra qué probar el aislamiento de detalle_venta.
INSERT INTO venta (comercio_id, folio_ticket_pos, fecha_hora_venta) VALUES
    (1, 'PRUEBA-0001', now() - interval '3 days');
INSERT INTO detalle_venta (venta_id, producto_id, cantidad_vendida, precio_unitario_cobrado) VALUES
    (1, 1, 2, 180.00);

-- Una factura recibida por el dueño, con su renglón ya emparejado al producto.
INSERT INTO factura_proveedor (comercio_id, recibida_por_usuario_id, nombre_proveedor, folio_factura, fecha_emision) VALUES
    (1, 1, 'Distribuidora de prueba', 'F-0001', CURRENT_DATE - 5);
INSERT INTO detalle_factura_proveedor
    (factura_id, producto_id, descripcion_leida_ocr, cantidad_comprada, costo_compra_unitario) VALUES
    (1, 2, 'LLAVE STILLSON 14 PULG', 3, 390.00);

INSERT INTO fuente_precio_publica (nombre_fuente, tipo_fuente, url_base, dias_vigencia) VALUES
    ('Catálogo tienda en línea A', 'TIENDA_EN_LINEA', 'https://ejemplo.invalid/a', 30),
    ('Marketplace B',              'MARKETPLACE',     'https://ejemplo.invalid/b', 15);

INSERT INTO referencia_precio_mercado
    (producto_id, fuente_id, url_publicacion, descripcion_publicada, precio_publicado,
     confianza_emparejamiento, fecha_hora_captura, fecha_hora_vencimiento) VALUES
    (2, 1, 'https://ejemplo.invalid/a/llave-14', 'Llave para tubo 14 pulgadas',
     498.00, 0.910, now() - interval '1 day', now() + interval '30 days'),   -- referencia 1: LLA-014, vigente
    (4, 2, 'https://ejemplo.invalid/b/sellador', 'Sellador acrílico transparente',
     88.00, 0.850, now() - interval '40 days', now() - interval '25 days');  -- referencia 2: SEL-007, VENCIDA

-- Conciliación: sin esto no se puede recomendar nada sobre el producto.
INSERT INTO conciliacion_inventario
    (comercio_id, producto_id, existencia_sistema_al_contar, existencia_contada, metodo_conteo,
     confirmado_por_usuario_id) VALUES
    (1, 1, 12, 12, 'CARGA',  NULL),   -- 1 · MAR-001
    (1, 2,  7,  6, 'FOTO',   1),      -- 2 · LLA-014, una unidad fantasma: el caso de R6
    (1, 3,  9,  9, 'MANUAL', 1),      -- 3 · IMP-003
    (1, 4, 20, 20, 'CARGA',  NULL),   -- 4 · SEL-007
    (2, 5,  8,  8, 'MANUAL', 3);      -- 5 · BAL-100, del otro comercio

INSERT INTO corrida_nocturna (comercio_id, estado_corrida, cantidad_productos_evaluados, fecha_hora_inicio, fecha_hora_fin) VALUES
    (1, 'TERMINADA', 4, now() - interval '1 hour', now()),   -- corrida 1 · comercio 1
    (2, 'TERMINADA', 1, now() - interval '1 hour', now()),   -- corrida 2 · comercio 2
    (1, 'TERMINADA', 1, now() - interval '30 minutes', now());  -- corrida 3 · comercio 1, la del sellador

-- Las tres salidas vigentes, cada una con lo que le toca producir. El motor
-- las escribe PENDIENTES; la decisión llega después, por UPDATE.
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion,
     dias_sin_venta, capital_inmovilizado, afinidad_climatica_aplicada,
     precio_venta_regular, precio_minimo_por_costo, precio_minimo_por_descuento, precio_propuesto,
     recuperacion_esperada, escalon_descuento, sin_contraste_de_mercado,
     fecha_reactivacion_estimada, cantidad_a_resurtir)
VALUES
    -- 1 · LIQUIDAR: piso por costo 364, piso por descuento 390 → el mínimo es
    -- 390, el mayor de los dos. El propuesto (455) queda estrictamente entre 390
    -- y 520, y no es el piso.
    (1, 2, 2, 'LIQUIDAR',
     'Sin venta en 154 días. Existencia conciliada 6. Referencia pública vigente en $498.',
     154, 2340.00, 'MEDIA',
     520.00, 364.00, 390.00, 455.00, 2730.00, NULL, false, NULL, NULL),   -- anterior a BIT-011: sin escalón

    -- 2 · RETENER: estacional fuera de ventana, con fecha de reactivación
    (1, 3, 3, 'RETENER',
     'Estacional fuera de su ventana (Lluvias, 1-jun a 30-sep). No se liquida: se retiene.',
     88, 8010.00, 'ALTA',
     NULL, NULL, NULL, NULL, NULL, NULL, false, DATE '2027-06-01', NULL),

    -- 3 · RESURTIR: bajo punto de reorden
    (1, 1, 1, 'RESURTIR',
     'Rotación sana y existencia por debajo del punto de reorden.',
     3, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL, NULL, false, NULL, 24),

    -- 4 · LIQUIDAR sin contraste: la única referencia del sellador venció
    -- (BIT-002). Mínimo 66.50; propuesto 85.50, el escalón 1.
    (3, 4, 4, 'LIQUIDAR',
     'Caduca en 120 días al ritmo actual. Emitida SIN referencia de mercado vigente.',
     40, 1240.00, 'MEDIA',
     95.00, 62.00, 66.50, 85.50, 1710.00, 1, true, NULL, NULL);

-- Liga la recomendación de liquidación con la referencia de SU producto.
INSERT INTO recomendacion_referencia (referencia_id, recomendacion_id, producto_id) VALUES (1, 1, 2);

-- El dueño aprueba la liquidación al precio propuesto y rechaza el resurtido
-- con motivo. La 2 y la 4 siguen pendientes.
UPDATE recomendacion
SET    estado_decision = 'APROBADA', decidido_por_usuario_id = 1,
       fecha_hora_decision = now(), precio_autorizado = 455.00
WHERE  recomendacion_id = 1;
UPDATE recomendacion
SET    estado_decision = 'RECHAZADA', decidido_por_usuario_id = 1, fecha_hora_decision = now(),
       motivo_rechazo = 'El proveedor sube precio la semana que entra; resurtir después.'
WHERE  recomendacion_id = 3;

INSERT INTO campana (comercio_id, recomendacion_id, texto_mensaje, fecha_hora_inicio, fecha_hora_fin) VALUES
    (1, 1, 'Llave Stillson 14" a $455. Quedan 6 piezas.', now(), now() + interval '14 days');

INSERT INTO cliente (comercio_id, nombre_cliente, telefono, fecha_hora_consentimiento) VALUES
    (1, 'Cliente de prueba',               '5500000001', now() - interval '60 days'),   -- 1
    (2, 'Cliente de la refaccionaria',     '5500000002', now() - interval '60 days');   -- 2

INSERT INTO envio_campana (campana_id, cliente_id, canal_envio) VALUES (1, 1, 'WHATSAPP');

COMMIT;


-- =====================================================================
-- B · LO QUE LA BASE TIENE QUE RECHAZAR
--
-- Cada bloque va en su propia transacción y termina en ROLLBACK, así que
-- correr este archivo entero no deja basura. Todos deben producir error, y
-- cada uno por la restricción o el disparador que dice su comentario.
-- =====================================================================

-- B1 · BIT-003 · precio propuesto IGUAL al mínimo.
--      Cumple «no baja del piso» y aun así está mal: el piso es un piso, no
--      una meta. Esperado: viola recomendacion_precio_entre_limites
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion,
     precio_venta_regular, precio_minimo_por_costo, precio_minimo_por_descuento, precio_propuesto)
VALUES (1, 4, 4, 'LIQUIDAR', 'Debe fallar: propone exactamente el piso.',
        95.00, 62.00, 66.50, 66.50);
ROLLBACK;

-- B2 · BIT-003 · precio propuesto IGUAL al regular.
--      Esperado: viola recomendacion_precio_entre_limites
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion,
     precio_venta_regular, precio_minimo_por_costo, precio_minimo_por_descuento, precio_propuesto)
VALUES (1, 4, 4, 'LIQUIDAR', 'Debe fallar: no hay descuento real.',
        95.00, 62.00, 66.50, 95.00);
ROLLBACK;

-- B3 · BIT-003 · precio por DEBAJO del piso por costo.
--      Esperado: viola recomendacion_precio_entre_limites
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion,
     precio_venta_regular, precio_minimo_por_costo, precio_minimo_por_descuento, precio_propuesto)
VALUES (1, 4, 4, 'LIQUIDAR', 'Debe fallar: regala el producto.',
        95.00, 62.00, 66.50, 50.00);
ROLLBACK;

-- B4 · HU-08 · recomendación sin conciliación.
--      Esperado: viola NOT NULL de conciliacion_id
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion, cantidad_a_resurtir)
VALUES (1, 4, NULL, 'RESURTIR', 'Debe fallar: nadie verificó que el producto esté.', 5);
ROLLBACK;

-- B5 · BIT-001 · RETENER sin fecha de reactivación.
--      Retener «indefinidamente» es esconder el problema, no resolverlo.
--      Esperado: viola recomendacion_retener_con_fecha
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion)
VALUES (1, 4, 4, 'RETENER', 'Debe fallar: retener sin decir hasta cuándo.');
ROLLBACK;

-- B6 · HU-04 · campaña sobre una recomendación RECHAZADA.
--      El agente recomienda, nunca ejecuta.
--      Esperado: lo detiene el disparador exigir_autorizacion_aprobada
BEGIN;
INSERT INTO campana (comercio_id, recomendacion_id, texto_mensaje, fecha_hora_inicio, fecha_hora_fin)
VALUES (1, 3, 'Debe fallar: el dueño dijo que no.', now(), now() + interval '7 days');
ROLLBACK;

-- B7 · HU-07 · rechazo sin motivo.
--      Esperado: viola recomendacion_rechazo_con_motivo
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'RECHAZADA', decidido_por_usuario_id = 1, fecha_hora_decision = now(),
       motivo_rechazo = NULL
WHERE  recomendacion_id = 2;
ROLLBACK;

-- B8 · RNF-04 / BIT-002 · fuente de precios de un tipo que no es público.
--      En la v1 lo atajaba es_publica; desde la v2, el tipo de fuente.
--      Esperado: viola fuente_tipo_valido
BEGIN;
INSERT INTO fuente_precio_publica (nombre_fuente, tipo_fuente, url_base, dias_vigencia)
VALUES ('Sistema interno de otro comercio', 'SISTEMA_INTERNO', 'https://ejemplo.invalid/privado', 30);
ROLLBACK;

-- B9 · R8 · producto estacional sin ventana declarada.
--      Sin ventana no se puede distinguir dormido de muerto.
--      Esperado: viola producto_estacional_con_temporada
BEGIN;
INSERT INTO producto (comercio_id, codigo_sku, nombre_producto, ciclo_producto, precio_venta_regular)
VALUES (1, 'XXX-999', 'Debe fallar: estacional sin temporada', 'ESTACIONAL', 100.00);
ROLLBACK;

-- B10 · HU-02 · conciliación por fotografía sin que nadie la confirme.
--       El clasificador propone; una persona firma.
--       Esperado: viola conciliacion_foto_confirmada
BEGIN;
INSERT INTO conciliacion_inventario (comercio_id, producto_id, existencia_sistema_al_contar,
                                     existencia_contada, metodo_conteo, confirmado_por_usuario_id)
VALUES (1, 1, 12, 9, 'FOTO', NULL);
ROLLBACK;

-- B11 · la cuarta salida NO existe todavía (SC-008 sin resolver).
--       Esperado: viola recomendacion_accion_valida
BEGIN;
INSERT INTO recomendacion (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion)
VALUES (1, 4, 4, 'DAR_DE_BAJA', 'Debe fallar hasta que el CCB apruebe SC-008.');
ROLLBACK;

-- B12 · BIT-012 · afinidad climática sin la zona para la que se declaró.
--       Una afinidad que no dice dónde aplica no la puede aplicar nadie.
--       Esperado: viola categoria_afinidad_con_zona
BEGIN;
INSERT INTO categoria (comercio_id, nombre_categoria, afinidad_climatica, zona_climatica_afinidad)
VALUES (1, 'Debe fallar: afinidad sin zona', 'BAJA', NULL);
ROLLBACK;

-- B13 · BIT-012 · comercio que dice que su municipio salió del GPS y no trae
--       coordenadas. Afirmar el origen sin el dato es una afirmación sin
--       respaldo, igual que una referencia de precio sin fuente.
--       Esperado: viola comercio_zona_gps_con_coordenadas
BEGIN;
INSERT INTO comercio (municipio_zona_id, nombre_comercio, giro_comercial, origen_zona_climatica)
VALUES (1, 'Debe fallar: GPS sin coordenadas', 'ferretería', 'GPS');
ROLLBACK;

-- ---------------------------------------------------------------------
-- De aquí en adelante, BIT-013 y BIT-021: aislamiento entre comercios,
-- integridad entre filas y quién decide.
-- ---------------------------------------------------------------------

-- B14 · BIT-013 · producto de la ferretería con una categoría de la
--       refaccionaria. Esperado: lo detiene producto_mismo_comercio
BEGIN;
INSERT INTO producto (comercio_id, codigo_sku, nombre_producto, categoria_id, ciclo_producto, precio_venta_regular)
VALUES (1, 'FUG-001', 'Debe fallar: categoría de otro comercio', 4, 'PERMANENTE', 100.00);
ROLLBACK;

-- B15 · BIT-013 · HU-08 · recomendación del sellador (producto 4) que cita la
--       conciliación del martillo (producto 1): el hallazgo central de MSG-005.
--       Esperado: viola recomendacion_conciliacion_del_producto
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion, cantidad_a_resurtir)
VALUES (1, 4, 1, 'RESURTIR', 'Debe fallar: conciliación de otro producto.', 5);
ROLLBACK;

-- B16 · BIT-021 · recomendación sobre el sellador de la ferretería colgada de
--       la corrida de la refaccionaria.
--       Esperado: lo detiene recomendacion_mismo_comercio
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion, cantidad_a_resurtir)
VALUES (2, 4, 4, 'RESURTIR', 'Debe fallar: corrida de otro comercio.', 5);
ROLLBACK;

-- B17 · BIT-021 · HU-04 · decide el dueño de OTRO comercio.
--       Esperado: lo detiene exigir_firma_del_dueno
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'APROBADA', decidido_por_usuario_id = 3, fecha_hora_decision = now()
WHERE  recomendacion_id = 2;
ROLLBACK;

-- B18 · BIT-021 · HU-04 · decide la cuenta LLM: el agente aprobándose a sí
--       mismo. Esperado: lo detiene exigir_firma_del_dueno
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'APROBADA', decidido_por_usuario_id = 2, fecha_hora_decision = now()
WHERE  recomendacion_id = 2;
ROLLBACK;

-- B19 · BIT-013 · HU-09 · la liquidación de la llave (recomendación 1)
--       citando la referencia de mercado del sellador (referencia 2).
--       Esperado: viola recomendacion_referencia_del_producto
BEGIN;
INSERT INTO recomendacion_referencia (referencia_id, recomendacion_id, producto_id)
VALUES (2, 1, 2);
ROLLBACK;

-- B20 · BIT-015 · temporada que empieza el 31 de febrero.
--       Esperado: viola temporada_dia_inicio_valido
BEGIN;
INSERT INTO temporada (comercio_id, nombre_temporada, dia_inicio, mes_inicio, dia_fin, mes_fin)
VALUES (1, 'Debe fallar: día que no existe', 31, 2, 15, 3);
ROLLBACK;

-- B21 · BIT-011 · BIT-013 · re-propuesta del sellador que dice venir de la
--       liquidación de la llave. La cadena de escalones tiene que ser del
--       mismo producto. Esperado: viola recomendacion_anterior_del_producto
BEGIN;
INSERT INTO recomendacion
    (corrida_id, producto_id, conciliacion_id, accion_recomendada, justificacion,
     cantidad_a_resurtir, recomendacion_anterior_id)
VALUES (1, 4, 4, 'RESURTIR', 'Debe fallar: antecedente de otro producto.', 5, 1);
ROLLBACK;

-- B22 · BIT-021 · renglón de una venta de la ferretería con un producto de
--       la refaccionaria. Esperado: lo detiene detalle_venta_mismo_comercio
BEGIN;
INSERT INTO detalle_venta (venta_id, producto_id, cantidad_vendida, precio_unitario_cobrado)
VALUES (1, 5, 1, 640.00);
ROLLBACK;

-- B23 · BIT-017 · producto con una unidad de medida que no está en el
--       catálogo. Esperado: viola producto_unidad_valida
BEGIN;
INSERT INTO producto (comercio_id, codigo_sku, nombre_producto, ciclo_producto, codigo_unidad, precio_venta_regular)
VALUES (1, 'UNI-001', 'Debe fallar: unidad inventada', 'PERMANENTE', 'CUBETA', 100.00);
ROLLBACK;

-- B24 · BIT-021 · renglón de una factura de la ferretería emparejado con un
--       producto de la refaccionaria.
--       Esperado: lo detiene detalle_factura_mismo_comercio
BEGIN;
INSERT INTO detalle_factura_proveedor (factura_id, producto_id, cantidad_comprada, costo_compra_unitario)
VALUES (1, 5, 1, 410.00);
ROLLBACK;

-- B25 · BIT-021 · RG-01 · la campaña de la ferretería enviada a un cliente de
--       la refaccionaria. Esperado: lo detiene envio_mismo_comercio
BEGIN;
INSERT INTO envio_campana (campana_id, cliente_id, canal_envio) VALUES (1, 2, 'APP');
ROLLBACK;

-- B26 · BIT-021 · degradar a ANALISTA al dueño que ya aprobó la liquidación.
--       Sus decisiones quedarían firmadas por alguien que no decide.
--       Esperado: lo detiene usuario_firmante_inmutable
BEGIN;
UPDATE usuario SET codigo_rol = 'ANALISTA' WHERE usuario_id = 1;
ROLLBACK;

-- B27 · BIT-021 · mover a otro comercio al dueño que ya decidió.
--       Esperado: lo detiene usuario_firmante_inmutable
BEGIN;
UPDATE usuario SET comercio_id = 2 WHERE usuario_id = 1;
ROLLBACK;

-- B28 · BIT-021 · mover la llave de la ferretería a la refaccionaria: sus
--       ventas, conteos y recomendaciones quedarían colgando de otra tienda.
--       Esperado: lo detiene comercio_inmutable
BEGIN;
UPDATE producto SET comercio_id = 2 WHERE producto_id = 2;
ROLLBACK;

-- B29 · BIT-021 · cambiar de APROBADA a RECHAZADA una decisión ya tomada
--       (MSG-018 H1). Esperado: lo detiene decision_inmutable
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'RECHAZADA', motivo_rechazo = 'Cambié de opinión.', precio_autorizado = NULL
WHERE  recomendacion_id = 1;
ROLLBACK;

-- B30 · HU-04 · campaña sobre una recomendación PENDIENTE.
--       Esperado: lo detiene exigir_autorizacion_aprobada
BEGIN;
INSERT INTO campana (comercio_id, recomendacion_id, texto_mensaje, fecha_hora_inicio, fecha_hora_fin)
VALUES (1, 2, 'Debe fallar: nadie lo ha aprobado.', now(), now() + interval '7 days');
ROLLBACK;

-- B31 · BIT-021 · la refaccionaria lanza una campaña con la recomendación
--       aprobada de la ferretería. Esperado: lo detiene campana_mismo_comercio
BEGIN;
INSERT INTO campana (comercio_id, recomendacion_id, texto_mensaje, fecha_hora_inicio, fecha_hora_fin)
VALUES (2, 1, 'Debe fallar: recomendación de otro comercio.', now(), now() + interval '7 days');
ROLLBACK;

-- B32 · BIT-021 · la ferretería registra el conteo de un producto de la
--       refaccionaria. Esperado: lo detiene conciliacion_mismo_comercio
BEGIN;
INSERT INTO conciliacion_inventario (comercio_id, producto_id, existencia_sistema_al_contar,
                                     existencia_contada, metodo_conteo, confirmado_por_usuario_id)
VALUES (1, 5, 8, 8, 'MANUAL', 1);
ROLLBACK;

-- B33 · BIT-021 · factura de la ferretería «recibida» por el dueño de la
--       refaccionaria. Esperado: lo detiene factura_mismo_comercio
BEGIN;
INSERT INTO factura_proveedor (comercio_id, recibida_por_usuario_id, nombre_proveedor, folio_factura)
VALUES (1, 3, 'Debe fallar', 'F-0002');
ROLLBACK;

-- B34 · MSG-018 H2 · aprobar una liquidación sin decir a qué precio.
--       Esperado: viola recomendacion_precio_autorizado_si_aprobada
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'APROBADA', decidido_por_usuario_id = 1, fecha_hora_decision = now(),
       precio_autorizado = NULL, acepto_riesgo_sin_contraste = true
WHERE  recomendacion_id = 4;
ROLLBACK;

-- B35 · MSG-018 H2 · BIT-003 · el dueño autoriza exactamente el mínimo.
--       Misma regla que el precio propuesto: el piso no es una meta.
--       Esperado: viola recomendacion_precio_autorizado_entre_limites
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'APROBADA', decidido_por_usuario_id = 1, fecha_hora_decision = now(),
       precio_autorizado = 66.50, acepto_riesgo_sin_contraste = true
WHERE  recomendacion_id = 4;
ROLLBACK;

-- B36 · MSG-018 H2 · BIT-002 · aprobar una propuesta sin referencia vigente
--       sin aceptar el riesgo. Esperado: viola recomendacion_acepta_riesgo
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'APROBADA', decidido_por_usuario_id = 1, fecha_hora_decision = now(),
       precio_autorizado = 80.00, acepto_riesgo_sin_contraste = false
WHERE  recomendacion_id = 4;
ROLLBACK;

-- B37 · un ADMINISTRADOR colgado de un comercio: administra la plataforma,
--       no una tienda. Esperado: viola usuario_comercio_segun_rol
BEGIN;
INSERT INTO usuario (comercio_id, codigo_rol, correo_electronico, nombre_completo, hash_contrasena)
VALUES (1, 'ADMINISTRADOR', 'admin2@ejemplo.invalid', 'Debe fallar', 'no-es-un-hash-real');
ROLLBACK;

-- B38 · una segunda cuenta del agente para el mismo comercio.
--       Esperado: viola ux_usuario_llm_por_comercio
BEGIN;
INSERT INTO usuario (comercio_id, codigo_rol, correo_electronico, nombre_completo, hash_contrasena)
VALUES (1, 'LLM', 'agente-bis@ejemplo.invalid', 'Debe fallar', 'no-es-un-hash-real');
ROLLBACK;

-- B39 · MSG-018 H6 · comercio en modo manual que conserva coordenadas.
--       Esperado: viola comercio_manual_sin_coordenadas
BEGIN;
INSERT INTO comercio (municipio_zona_id, nombre_comercio, giro_comercial, origen_zona_climatica, latitud, longitud)
VALUES (2, 'Debe fallar: manual con coordenadas', 'ferretería', 'MANUAL', 19.282600, -99.655700);
ROLLBACK;

-- B40 · BIT-014 · origen GPS con coordenadas que no cayeron en ningún
--       municipio: la app tenía que ir al selector manual.
--       Esperado: viola comercio_gps_con_municipio
BEGIN;
INSERT INTO comercio (municipio_zona_id, nombre_comercio, giro_comercial, origen_zona_climatica, latitud, longitud)
VALUES (NULL, 'Debe fallar: GPS sin municipio', 'ferretería', 'GPS', 15.000000, -105.000000);
ROLLBACK;

-- B41 · el mismo ticket del punto de venta cargado dos veces.
--       Esperado: viola venta_folio_unico
BEGIN;
INSERT INTO venta (comercio_id, folio_ticket_pos, fecha_hora_venta) VALUES (1, 'PRUEBA-0001', now());
ROLLBACK;

-- B42 · el mismo producto en dos renglones del mismo ticket: la carga los
--       suma en uno. Esperado: viola detalle_venta_pkey
BEGIN;
INSERT INTO detalle_venta (venta_id, producto_id, cantidad_vendida, precio_unitario_cobrado)
VALUES (1, 1, 1, 180.00);
ROLLBACK;

-- B43 · el mismo producto en dos renglones de la misma factura.
--       Esperado: viola detalle_factura_proveedor_pkey
BEGIN;
INSERT INTO detalle_factura_proveedor (factura_id, producto_id, cantidad_comprada, costo_compra_unitario)
VALUES (1, 2, 1, 390.00);
ROLLBACK;

-- B44 · una recomendación APROBADA sin que nadie la haya decidido.
--       Esperado: viola recomendacion_decision_completa
BEGIN;
UPDATE recomendacion SET estado_decision = 'APROBADA', fecha_hora_decision = now()
WHERE  recomendacion_id = 2;
ROLLBACK;

-- B45 · stock mínimo por encima del máximo.
--       Esperado: viola producto_stock_valido
BEGIN;
INSERT INTO producto (comercio_id, codigo_sku, nombre_producto, ciclo_producto, precio_venta_regular,
                      stock_minimo, stock_maximo)
VALUES (1, 'STK-001', 'Debe fallar: mínimo mayor que máximo', 'PERMANENTE', 100.00, 10, 4);
ROLLBACK;

-- B46 · BIT-012 · afinidad propia del producto sin su zona.
--       Esperado: viola producto_afinidad_con_zona
BEGIN;
INSERT INTO producto (comercio_id, codigo_sku, nombre_producto, ciclo_producto, precio_venta_regular,
                      afinidad_climatica_propia)
VALUES (1, 'AFI-001', 'Debe fallar: afinidad sin zona', 'PERMANENTE', 100.00, 'ALTA');
ROLLBACK;

-- B47 · HU-12 · resultado de campaña a medias: unidades sin ingreso.
--       Esperado: viola campana_resultado_completo
BEGIN;
UPDATE campana SET unidades_vendidas = 2 WHERE campana_id = 1;
ROLLBACK;


-- =====================================================================
-- C · LO QUE LA BASE TIENE QUE RESOLVER BIEN
--
-- Si alguna de estas falla, levanta un error y el conteo de la parte B deja
-- de cuadrar: verificar.py lo detecta igual.
-- =====================================================================

-- C1 · BIT-014 · unas coordenadas del centro de Mérida resuelven a TROPICAL.
DO $$
DECLARE r RECORD;
BEGIN
    SELECT * INTO r FROM zona_por_coordenadas(20.9700, -89.6200);
    IF r.zona_climatica IS DISTINCT FROM 'TROPICAL' OR r.municipio IS DISTINCT FROM 'Mérida' THEN
        RAISE EXCEPTION 'C1 falla: Mérida resolvió a % / %', r.municipio, r.zona_climatica;
    END IF;
END $$;

-- C2 · BIT-014 · un punto en el Pacífico no cae en ningún radio: la función no
--      devuelve nada y la aplicación tiene que ir al selector manual.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM zona_por_coordenadas(15.0000, -105.0000)) THEN
        RAISE EXCEPTION 'C2 falla: un punto sin municipio resolvió a una zona';
    END IF;
END $$;

-- C3 · BIT-015 · el 29 de febrero SÍ se admite como inicio de temporada.
BEGIN;
INSERT INTO temporada (comercio_id, nombre_temporada, dia_inicio, mes_inicio, dia_fin, mes_fin)
VALUES (1, 'Prueba de bisiesto', 29, 2, 31, 3);
ROLLBACK;

-- C4 · BIT-013 · ninguna recomendación cita una conciliación de otro producto
--      ni de otro comercio.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM recomendacion r
               JOIN conciliacion_inventario c USING (conciliacion_id)
               JOIN corrida_nocturna k ON k.corrida_id = r.corrida_id
               WHERE r.producto_id <> c.producto_id OR k.comercio_id <> c.comercio_id) THEN
        RAISE EXCEPTION 'C4 falla: hay recomendaciones con conciliación ajena';
    END IF;
END $$;

-- C5 · el catálogo de municipios viene precargado: 21 renglones.
DO $$
BEGIN
    IF (SELECT count(*) FROM municipio_zona_climatica) <> 21 THEN
        RAISE EXCEPTION 'C5 falla: hay % municipios y se esperaban 21',
            (SELECT count(*) FROM municipio_zona_climatica);
    END IF;
END $$;

-- C6 · BIT-014 · el Zócalo cae en Cuauhtémoc, aunque también lo cubra el
--      radio de Nezahualcóyotl: gana el centro más cercano.
DO $$
DECLARE r RECORD;
BEGIN
    SELECT * INTO r FROM zona_por_coordenadas(19.4326, -99.1332);
    IF r.municipio IS DISTINCT FROM 'Cuauhtémoc' OR r.zona_climatica IS DISTINCT FROM 'TEMPLADO' THEN
        RAISE EXCEPTION 'C6 falla: el Zócalo resolvió a % / %', r.municipio, r.zona_climatica;
    END IF;
END $$;

-- C7 · MSG-018 H2 · el dueño SÍ puede aprobar el sellador a un precio distinto
--      del propuesto, dentro de los límites, si acepta el riesgo de que no hay
--      referencia vigente.
BEGIN;
UPDATE recomendacion
SET    estado_decision = 'APROBADA', decidido_por_usuario_id = 1, fecha_hora_decision = now(),
       precio_autorizado = 80.00, acepto_riesgo_sin_contraste = true
WHERE  recomendacion_id = 4;
ROLLBACK;

-- C8 · el subtotal del renglón de venta se calcula solo: 2 × 180 = 360.
DO $$
BEGIN
    IF (SELECT subtotal FROM detalle_venta WHERE venta_id = 1 AND producto_id = 1) <> 360.00 THEN
        RAISE EXCEPTION 'C8 falla: el subtotal no es 360.00';
    END IF;
END $$;
