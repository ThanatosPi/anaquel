-- =====================================================================
-- Anaquel — Sistema inteligente de rotación de inventario
-- Esquema de base de datos · PostgreSQL 14+
-- Hito H2 · 18 de septiembre de 2026
-- Revisión · 23 de septiembre de 2026 (BIT-012 a BIT-015, BIT-017)
-- Versión 2 · 20 de octubre de 2026 (BIT-021, BIT-022): la que aprobó la
--   maestra de Base de Datos. 26 → 21 tablas. Diccionario completo, nombre
--   anterior de cada columna y razones: db/maqueta_er_v2.html.
--
-- Principio de diseño: las reglas del dominio que se pueden expresar como
-- restricción viven AQUÍ, no en el código. Una invariante que sólo existe en
-- una función de Python se viola el día que alguien escribe un INSERT a mano.
--
-- Lo que esta base hace imposible, no improbable:
--   · proponer un precio que iguale o cruce cualquiera de sus dos límites
--   · autorizar un precio que iguale o cruce esos mismos límites (MSG-018 H2)
--   · emitir una recomendación sobre existencias sin conciliar — incluida la
--     trampa de citar la conciliación de OTRO producto (BIT-013)
--   · registrar una referencia de precio de una fuente que no sea de un tipo
--     público (BIT-002)
--   · ejecutar una campaña sin la aprobación del dueño
--   · que decida alguien que no es el dueño del comercio, o que una decisión
--     tomada se cambie después (BIT-021)
--   · que dos comercios se vean o se mezclen los datos (BIT-013, BIT-021)
--
-- AISLAMIENTO (BIT-013 → BIT-021). La versión 1 lo resolvía con llaves
-- compuestas que arrastraban comercio_id a todas las tablas. La maestra pidió
-- quitarlo de las entidades débiles —recomendacion, envio_campana,
-- detalle_venta y detalle_factura_proveedor—, que lo heredan de su entidad
-- fuerte. La garantía no cambia: la cuidan los disparadores de la sección 9.
-- Una llave foránea simple sigue aceptando la fila de otro comercio; el
-- disparador es el que la rechaza, también si el INSERT se escribe a mano.
--
-- Convenciones (sección 4 de la maqueta): identificadores y comentarios en
-- español; clave sustituta = nombre corto de la entidad + _id, BIGINT
-- GENERATED ALWAYS AS IDENTITY; fecha_ = DATE, fecha_hora_ = TIMESTAMPTZ;
-- dinero en NUMERIC(12,2) —nunca float, que redondea distinto en cada
-- máquina—; cantidades en NUMERIC(12,3). Los catálogos cerrados que no
-- consulta otra tabla son CHECK; los que sí (rol, unidad_medida, municipio)
-- son tabla.
-- =====================================================================

BEGIN;

-- =====================================================================
-- 1 · MUNICIPIOS, COMERCIO, ROLES Y USUARIOS
-- =====================================================================

-- BIT-012 · BIT-014. Catálogo PRECARGADO de los municipios que se cubren. El
-- teléfono entrega coordenadas, no municipios: por eso cada renglón guarda un
-- punto representativo y un radio de cobertura. Vive en la base y no en un
-- servicio externo: se consulta sin internet y se puede enseñar en clase. Cada
-- renglón declara su fuente, igual que las referencias de precio (BIT-002).
-- La zona climática es un CHECK, no una tabla: nadie más la consulta.
CREATE TABLE municipio_zona_climatica (
    municipio_zona_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entidad_federativa TEXT         NOT NULL,
    municipio          TEXT         NOT NULL,
    zona_climatica     TEXT         NOT NULL,
    latitud_centro     NUMERIC(9,6) NOT NULL,
    longitud_centro    NUMERIC(9,6) NOT NULL,
    radio_km           NUMERIC(6,1) NOT NULL DEFAULT 40,
    fuente_dato        TEXT         NOT NULL,
    fecha_hora_captura TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT municipio_unico UNIQUE (entidad_federativa, municipio),
    CONSTRAINT municipio_zona_valida
        CHECK (zona_climatica IN ('TROPICAL', 'TEMPLADO', 'FRIO', 'ARIDO', 'SEMIARIDO')),
    CONSTRAINT municipio_latitud_valida  CHECK (latitud_centro  BETWEEN  -90 AND  90),
    CONSTRAINT municipio_longitud_valida CHECK (longitud_centro BETWEEN -180 AND 180),
    CONSTRAINT municipio_radio_positivo  CHECK (radio_km > 0)
);

-- El orden de este INSERT es el de los municipio_zona_id (1 a 21) y el de
-- simulador/salida/municipios.json; verificar.py compara renglón por renglón.
-- Los cinco primeros son los de la versión 1. Los 16 de la CDMX: centro y área
-- de los polígonos del Marco Geoestadístico (radio = raíz de área/π, así el
-- círculo tiene la superficie de la alcaldía) y la zona del clima que cubre
-- más superficie según el Compendio de información geográfica municipal 2010.
INSERT INTO municipio_zona_climatica
    (entidad_federativa, municipio, zona_climatica, latitud_centro, longitud_centro, radio_km, fuente_dato)
VALUES
    ('Yucatán',          'Mérida',                 'TROPICAL',  20.967200,  -89.624700, 40.0, 'Clasificación Köppen-García (fuente pública)'),
    ('Estado de México', 'Toluca',                 'FRIO',      19.282600,  -99.655700, 30.0, 'Clasificación Köppen-García (fuente pública)'),
    ('Estado de México', 'Nezahualcóyotl',         'TEMPLADO',  19.400600,  -99.014800, 15.0, 'Clasificación Köppen-García (fuente pública)'),
    ('Chihuahua',        'Chihuahua',              'ARIDO',     28.635300, -106.088900, 40.0, 'Clasificación Köppen-García (fuente pública)'),
    ('Coahuila',         'Saltillo',               'SEMIARIDO', 25.423300, -101.000000, 35.0, 'Clasificación Köppen-García (fuente pública)'),
    ('Ciudad de México', 'Azcapotzalco',           'TEMPLADO',  19.485329,  -99.182107,  3.3, 'INEGI, Marco Geoestadístico dic. 2025 (09002) y Compendio geográfico municipal 2010: templado subhúmedo 100 %'),
    ('Ciudad de México', 'Coyoacán',               'TEMPLADO',  19.326667,  -99.150376,  4.1, 'INEGI, Marco Geoestadístico dic. 2025 (09003) y Compendio geográfico municipal 2010: templado subhúmedo 100 %'),
    ('Ciudad de México', 'Cuajimalpa de Morelos',  'FRIO',      19.324321,  -99.310930,  4.8, 'INEGI, Marco Geoestadístico dic. 2025 (09004) y Compendio geográfico municipal 2010: semifrío 50.71 %, templado 49.29 %'),
    ('Ciudad de México', 'Gustavo A. Madero',      'TEMPLADO',  19.504065,  -99.115864,  5.3, 'INEGI, Marco Geoestadístico dic. 2025 (09005) y Compendio geográfico municipal 2010: templado subhúmedo 72.80 %'),
    ('Ciudad de México', 'Iztacalco',              'SEMIARIDO', 19.396912,  -99.094330,  2.7, 'INEGI, Marco Geoestadístico dic. 2025 (09006) y Compendio geográfico municipal 2010: seco semiseco 78.70 %'),
    ('Ciudad de México', 'Iztapalapa',             'TEMPLADO',  19.349166,  -99.056799,  6.0, 'INEGI, Marco Geoestadístico dic. 2025 (09007) y Compendio geográfico municipal 2010: templado subhúmedo 77.48 %'),
    ('Ciudad de México', 'La Magdalena Contreras', 'FRIO',      19.268977,  -99.268413,  4.5, 'INEGI, Marco Geoestadístico dic. 2025 (09008) y Compendio geográfico municipal 2010: semifrío 62.89 %'),
    ('Ciudad de México', 'Milpa Alta',             'FRIO',      19.141773,  -99.049318,  9.6, 'INEGI, Marco Geoestadístico dic. 2025 (09009) y Compendio geográfico municipal 2010: semifrío 67.87 %'),
    ('Ciudad de México', 'Álvaro Obregón',         'TEMPLADO',  19.336176,  -99.246820,  5.5, 'INEGI, Marco Geoestadístico dic. 2025 (09010) y Compendio geográfico municipal 2010: templado subhúmedo 71.82 %'),
    ('Ciudad de México', 'Tláhuac',                'TEMPLADO',  19.276998,  -99.002822,  5.2, 'INEGI, Marco Geoestadístico dic. 2025 (09011) y Compendio geográfico municipal 2010: templado subhúmedo 100 %'),
    ('Ciudad de México', 'Tlalpan',                'FRIO',      19.198338,  -99.206221, 10.0, 'INEGI, Marco Geoestadístico dic. 2025 (09012) y Compendio geográfico municipal 2010: semifrío 62.92 %'),
    ('Ciudad de México', 'Xochimilco',             'TEMPLADO',  19.245145,  -99.090364,  6.0, 'INEGI, Marco Geoestadístico dic. 2025 (09013) y Compendio geográfico municipal 2010: templado subhúmedo 97.38 %'),
    ('Ciudad de México', 'Benito Juárez',          'TEMPLADO',  19.380642,  -99.161135,  2.9, 'INEGI, Marco Geoestadístico dic. 2025 (09014) y Compendio geográfico municipal 2010: templado subhúmedo 100 %'),
    ('Ciudad de México', 'Cuauhtémoc',             'TEMPLADO',  19.431373,  -99.149056,  3.2, 'INEGI, Marco Geoestadístico dic. 2025 (09015) y Compendio geográfico municipal 2010: templado subhúmedo 100 %'),
    ('Ciudad de México', 'Miguel Hidalgo',         'TEMPLADO',  19.428069,  -99.204584,  3.9, 'INEGI, Marco Geoestadístico dic. 2025 (09016) y Compendio geográfico municipal 2010: templado subhúmedo 100 %'),
    ('Ciudad de México', 'Venustiano Carranza',    'SEMIARIDO', 19.430495,  -99.093106,  3.3, 'INEGI, Marco Geoestadístico dic. 2025 (09017) y Compendio geográfico municipal 2010: seco semiseco 91.42 %');

-- BIT-014. Coordenadas → municipio más cercano DENTRO de su radio. Si ningún
-- municipio de la tabla cubre el punto, no devuelve nada, y la aplicación
-- muestra el selector manual: nunca se adivina una zona. Es una aproximación
-- a propósito —distancia al centro, no límites municipales— y tiene una red de
-- seguridad: el dueño confirma «Tu negocio está en Mérida, Yucatán» antes de
-- que se guarde. Unas coordenadas fuera de rango tampoco resuelven (MSG-018 H6).
CREATE OR REPLACE FUNCTION zona_por_coordenadas(p_latitud NUMERIC, p_longitud NUMERIC)
RETURNS TABLE (municipio_zona_id BIGINT, entidad_federativa TEXT, municipio TEXT,
               zona_climatica TEXT, distancia_km NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT m.municipio_zona_id, m.entidad_federativa, m.municipio, m.zona_climatica,
           round(d.km::numeric, 1)
    FROM   municipio_zona_climatica m
    CROSS  JOIN LATERAL (
        -- Fórmula del semiverseno; 6371 km es el radio medio de la Tierra.
        SELECT 2 * 6371 * asin(sqrt(
                   power(sin(radians((m.latitud_centro - p_latitud)::float8) / 2), 2) +
                   cos(radians(p_latitud::float8)) * cos(radians(m.latitud_centro::float8)) *
                   power(sin(radians((m.longitud_centro - p_longitud)::float8) / 2), 2)
               )) AS km
    ) d
    WHERE  p_latitud BETWEEN -90 AND 90 AND p_longitud BETWEEN -180 AND 180
      AND  d.km <= m.radio_km
    ORDER  BY d.km
    LIMIT  1;
$$;

-- Todo cuelga del comercio. RNF-04 y BIT-002 prohíben que un comercio vea
-- datos internos de otro; BIT-013 y BIT-021 hacen que eso lo garantice la base.
CREATE TABLE comercio (
    comercio_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Vacío sólo mientras el dueño no confirma su ubicación: la app no deja
    -- avanzar sin elegir un municipio. La zona climática sale de aquí.
    municipio_zona_id     BIGINT REFERENCES municipio_zona_climatica,
    rfc                   TEXT,
    nombre_comercio       TEXT         NOT NULL,
    giro_comercial        TEXT         NOT NULL,   -- ferretería, refaccionaria, boutique, farmacia…
    zona_horaria          TEXT         NOT NULL DEFAULT 'America/Mexico_City',

    -- BIT-012. El municipio sale del permiso de ubicación del teléfono y, si el
    -- dueño lo niega, de su propia elección. Se guarda DE DÓNDE salió, porque
    -- la justificación de la recomendación tiene que poder decirlo.
    origen_zona_climatica TEXT         NOT NULL DEFAULT 'MANUAL',
    latitud               NUMERIC(9,6),
    longitud              NUMERIC(9,6),

    fecha_alta            DATE         NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT comercio_rfc_unico UNIQUE (rfc),
    CONSTRAINT comercio_nombre_no_vacio CHECK (length(trim(nombre_comercio)) > 0),
    CONSTRAINT comercio_origen_zona_valido CHECK (origen_zona_climatica IN ('GPS', 'MANUAL')),

    -- Decir que el municipio salió del GPS sin guardar las coordenadas es una
    -- afirmación sin respaldo.
    CONSTRAINT comercio_zona_gps_con_coordenadas CHECK (
        origen_zona_climatica <> 'GPS' OR (latitud IS NOT NULL AND longitud IS NOT NULL)
    ),
    -- Y si el GPS dio coordenadas fuera de todo radio, la app va al selector
    -- manual: un origen GPS siempre trae su municipio.
    CONSTRAINT comercio_gps_con_municipio CHECK (
        origen_zona_climatica <> 'GPS' OR municipio_zona_id IS NOT NULL
    ),
    -- Al pasar a modo manual se borran las coordenadas (decisión de Sebastián,
    -- 24/sep, MSG-013; cierra MSG-018 H6).
    CONSTRAINT comercio_manual_sin_coordenadas CHECK (
        origen_zona_climatica <> 'MANUAL' OR (latitud IS NULL AND longitud IS NULL)
    ),
    CONSTRAINT comercio_latitud_valida  CHECK (latitud  IS NULL OR latitud  BETWEEN  -90 AND  90),
    CONSTRAINT comercio_longitud_valida CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180)
);

-- Sólo el DUENO autoriza (HU-04). LLM es la cuenta del agente: propone y
-- nunca autoriza (antes se llamaba SISTEMA). ADMINISTRADOR administra la
-- plataforma y no pertenece a ningún comercio.
CREATE TABLE rol (
    codigo_rol      TEXT PRIMARY KEY,
    descripcion_rol TEXT NOT NULL
);

INSERT INTO rol (codigo_rol, descripcion_rol) VALUES
    ('DUENO',         'Dueño del comercio. Es el único que aprueba o rechaza una recomendación.'),
    ('ADMINISTRADOR', 'Administra la plataforma: da de alta municipios y comercios. No pertenece a ningún comercio.'),
    ('ANALISTA',      'Consulta el comercio y prepara información. No decide.'),
    ('LLM',           'El agente de Anaquel. Propone recomendaciones y nunca las autoriza. Uno por comercio.');

CREATE TABLE usuario (
    usuario_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id        BIGINT      REFERENCES comercio,
    codigo_rol         TEXT        NOT NULL,
    correo_electronico TEXT        NOT NULL,
    nombre_completo    TEXT        NOT NULL,
    hash_contrasena    TEXT        NOT NULL,
    esta_activo        BOOLEAN     NOT NULL DEFAULT true,
    fecha_alta         DATE        NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT usuario_rol_valido FOREIGN KEY (codigo_rol) REFERENCES rol (codigo_rol),
    CONSTRAINT usuario_correo_unico UNIQUE (correo_electronico),
    -- El ADMINISTRADOR es el único sin comercio; todos los demás tienen uno.
    CONSTRAINT usuario_comercio_segun_rol CHECK (
        (codigo_rol = 'ADMINISTRADOR') = (comercio_id IS NULL)
    )
);

-- Una sola cuenta del agente por comercio: si hubiera dos, no se sabría cuál
-- propuso qué.
CREATE UNIQUE INDEX ux_usuario_llm_por_comercio ON usuario (comercio_id) WHERE codigo_rol = 'LLM';

-- =====================================================================
-- 2 · CATÁLOGO DE PRODUCTO
-- =====================================================================

-- BIT-017. En qué se cuenta el producto. Las cantidades admiten tres
-- decimales porque hay cosas que se venden por metro o por kilo, pero sin la
-- unidad, la app decía «2.125 piezas» (MSG-012). Sigue siendo tabla porque las
-- cantidades de otras tablas consultan si la unidad acepta decimales.
CREATE TABLE unidad_medida (
    codigo_unidad      TEXT    PRIMARY KEY,
    descripcion_unidad TEXT    NOT NULL,
    permite_decimales  BOOLEAN NOT NULL      -- una pieza no se cuenta en fracciones
);

INSERT INTO unidad_medida (codigo_unidad, descripcion_unidad, permite_decimales) VALUES
    ('PIEZA', 'Se cuenta por unidades enteras.',        false),
    ('METRO', 'Se vende por longitud: cable, manguera a granel.', true),
    ('KILO',  'Se vende por peso: clavos, tornillería a granel.',  true),
    ('LITRO', 'Se vende por volumen: pintura o solvente a granel.', true);

-- BIT-012. La categoría fija la afinidad climática del caso general; el
-- producto puede sobrescribirla con la suya. La afinidad se declara PARA una
-- zona: sin zona no se sabe dónde aplica.
CREATE TABLE categoria (
    categoria_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id             BIGINT NOT NULL REFERENCES comercio,
    nombre_categoria        TEXT   NOT NULL,
    afinidad_climatica      TEXT,
    zona_climatica_afinidad TEXT,
    CONSTRAINT categoria_unica UNIQUE (comercio_id, nombre_categoria),
    CONSTRAINT categoria_afinidad_valida
        CHECK (afinidad_climatica IS NULL OR afinidad_climatica IN ('ALTA', 'MEDIA', 'BAJA', 'NO_APLICA')),
    CONSTRAINT categoria_zona_afinidad_valida CHECK (
        zona_climatica_afinidad IS NULL OR
        zona_climatica_afinidad IN ('TROPICAL', 'TEMPLADO', 'FRIO', 'ARIDO', 'SEMIARIDO')
    ),
    CONSTRAINT categoria_afinidad_con_zona
        CHECK ((afinidad_climatica IS NULL) = (zona_climatica_afinidad IS NULL))
);

-- La ventana de temporada pertenece al comercio, no a un catálogo global:
-- «regreso a clases» no cae igual para una papelería que para una boutique.
--
-- BIT-015. El día tiene que existir en ese mes: no hay 31 de febrero. El 29 de
-- febrero sí se admite, y en año no bisiesto el motor lo lee como 28.
CREATE TABLE temporada (
    temporada_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id      BIGINT   NOT NULL REFERENCES comercio,
    nombre_temporada TEXT     NOT NULL,
    dia_inicio       SMALLINT NOT NULL,
    mes_inicio       SMALLINT NOT NULL,
    dia_fin          SMALLINT NOT NULL,
    mes_fin          SMALLINT NOT NULL,
    CONSTRAINT temporada_unica             UNIQUE (comercio_id, nombre_temporada),
    CONSTRAINT temporada_mes_inicio_valido CHECK (mes_inicio BETWEEN 1 AND 12),
    CONSTRAINT temporada_mes_fin_valido    CHECK (mes_fin    BETWEEN 1 AND 12),
    CONSTRAINT temporada_dia_inicio_valido CHECK (
        dia_inicio BETWEEN 1 AND CASE mes_inicio WHEN 2 THEN 29
                                                 WHEN 4 THEN 30 WHEN 6  THEN 30
                                                 WHEN 9 THEN 30 WHEN 11 THEN 30
                                                 ELSE 31 END
    ),
    CONSTRAINT temporada_dia_fin_valido CHECK (
        dia_fin BETWEEN 1 AND CASE mes_fin WHEN 2 THEN 29
                                           WHEN 4 THEN 30 WHEN 6  THEN 30
                                           WHEN 9 THEN 30 WHEN 11 THEN 30
                                           ELSE 31 END
    )
);

-- El producto es del comercio. Su ciclo, su temporada y su afinidad propia son
-- decisión de ESE comercio sobre ESE producto, no atributos del catálogo de la
-- categoría: lección heredada de GEA_ECE, donde derivar la selección de un
-- catálogo compartido produjo un producto cartesiano entre usuarios.
CREATE TABLE producto (
    producto_id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id                    BIGINT        NOT NULL REFERENCES comercio,
    codigo_sku                     TEXT          NOT NULL,
    categoria_id                   BIGINT        REFERENCES categoria,
    temporada_id                   BIGINT        REFERENCES temporada,
    codigo_unidad                  TEXT          NOT NULL DEFAULT 'PIEZA',      -- BIT-017
    nombre_producto                TEXT          NOT NULL,
    ciclo_producto                 TEXT          NOT NULL,
    afinidad_climatica_propia      TEXT,
    zona_climatica_afinidad_propia TEXT,
    precio_venta_regular           NUMERIC(12,2) NOT NULL,
    costo_compra_unitario          NUMERIC(12,2),   -- NULL mientras no se extraiga de factura (R1)
    stock_minimo                   INTEGER,         -- HU-11: punto de reorden
    stock_maximo                   INTEGER,
    existencia_sistema             NUMERIC(12,3) NOT NULL DEFAULT 0,
    fecha_caducidad                DATE,            -- sólo perecederos
    estado_producto                TEXT          NOT NULL DEFAULT 'ACTIVO',
    fecha_alta                     DATE          NOT NULL DEFAULT CURRENT_DATE,

    CONSTRAINT producto_sku_unico UNIQUE (comercio_id, codigo_sku),
    CONSTRAINT producto_unidad_valida FOREIGN KEY (codigo_unidad) REFERENCES unidad_medida (codigo_unidad),
    CONSTRAINT producto_ciclo_valido
        CHECK (ciclo_producto IN ('PERECEDERO', 'ESTACIONAL', 'PERMANENTE')),
    CONSTRAINT producto_precio_positivo CHECK (precio_venta_regular > 0),
    CONSTRAINT producto_costo_no_negativo CHECK (costo_compra_unitario IS NULL OR costo_compra_unitario >= 0),
    CONSTRAINT producto_existencia_no_negativa CHECK (existencia_sistema >= 0),
    CONSTRAINT producto_stock_valido CHECK (
        (stock_minimo IS NULL OR stock_minimo >= 0) AND
        (stock_maximo IS NULL OR stock_maximo >= 0) AND
        (stock_minimo IS NULL OR stock_maximo IS NULL OR stock_minimo <= stock_maximo)
    ),
    CONSTRAINT producto_estado_valido CHECK (estado_producto IN ('ACTIVO', 'DESCONTINUADO')),

    -- BIT-001: un estacional sin ventana declarada no se puede evaluar, y el
    -- agente tiene que abstenerse. Se exige la ventana desde el catálogo para
    -- que la abstención sea la excepción y no la regla (R8).
    CONSTRAINT producto_estacional_con_temporada
        CHECK (ciclo_producto <> 'ESTACIONAL' OR temporada_id IS NOT NULL),

    -- Un perecedero sin caducidad no tiene contra qué medir su rotación.
    CONSTRAINT producto_perecedero_con_caducidad
        CHECK (ciclo_producto <> 'PERECEDERO' OR fecha_caducidad IS NOT NULL),

    CONSTRAINT producto_afinidad_valida CHECK (
        afinidad_climatica_propia IS NULL OR
        afinidad_climatica_propia IN ('ALTA', 'MEDIA', 'BAJA', 'NO_APLICA')
    ),
    CONSTRAINT producto_zona_afinidad_valida CHECK (
        zona_climatica_afinidad_propia IS NULL OR
        zona_climatica_afinidad_propia IN ('TROPICAL', 'TEMPLADO', 'FRIO', 'ARIDO', 'SEMIARIDO')
    ),
    CONSTRAINT producto_afinidad_con_zona
        CHECK ((afinidad_climatica_propia IS NULL) = (zona_climatica_afinidad_propia IS NULL))
);

CREATE INDEX ix_producto_comercio ON producto (comercio_id) WHERE estado_producto = 'ACTIVO';
CREATE INDEX ix_producto_ciclo    ON producto (comercio_id, ciclo_producto);

-- =====================================================================
-- 3 · MOVIMIENTO: VENTAS, COMPRAS Y EXISTENCIAS
-- =====================================================================

CREATE TABLE venta (
    venta_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id      BIGINT      NOT NULL REFERENCES comercio,
    folio_ticket_pos TEXT,                        -- folio del punto de venta, si lo trae
    fecha_hora_venta TIMESTAMPTZ NOT NULL,
    fecha_hora_carga TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Una carga repetida no duplica ventas. Las ventas sin folio no tienen esa
    -- protección: en PostgreSQL varios NULL no chocan.
    CONSTRAINT venta_folio_unico UNIQUE (comercio_id, folio_ticket_pos)
);

-- Entidad débil: se identifica por su venta y su producto, y hereda el
-- comercio de la venta. El disparador detalle_venta_mismo_comercio exige que
-- el producto sea de ese comercio.
CREATE TABLE detalle_venta (
    venta_id                BIGINT        NOT NULL REFERENCES venta ON DELETE CASCADE,
    producto_id             BIGINT        NOT NULL REFERENCES producto,
    cantidad_vendida        NUMERIC(12,3) NOT NULL,
    precio_unitario_cobrado NUMERIC(12,2) NOT NULL,
    subtotal                NUMERIC(12,2) GENERATED ALWAYS AS
                                (round(cantidad_vendida * precio_unitario_cobrado, 2)) STORED,
    PRIMARY KEY (venta_id, producto_id),
    CONSTRAINT detalle_venta_cantidad_positiva  CHECK (cantidad_vendida > 0),
    CONSTRAINT detalle_venta_precio_no_negativo CHECK (precio_unitario_cobrado >= 0)
);

CREATE INDEX ix_detalle_venta_producto ON detalle_venta (producto_id);
CREATE INDEX ix_venta_fecha            ON venta (comercio_id, fecha_hora_venta DESC);

-- HU-05 · R1: el punto de venta del piloto puede no registrar el costo de
-- compra. Se extrae de la fotografía de la factura, con su confianza. Quien
-- recibe la factura empareja cada renglón con su producto.
CREATE TABLE factura_proveedor (
    factura_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id             BIGINT      NOT NULL REFERENCES comercio,
    recibida_por_usuario_id BIGINT      REFERENCES usuario,
    nombre_proveedor        TEXT,
    folio_factura           TEXT,
    fecha_emision           DATE,
    ruta_imagen_factura     TEXT,
    fecha_hora_captura      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Entidad débil: se identifica por su factura y su producto. El producto es
-- obligatorio: el renglón se empareja al recibir la factura.
CREATE TABLE detalle_factura_proveedor (
    factura_id            BIGINT        NOT NULL REFERENCES factura_proveedor ON DELETE CASCADE,
    producto_id           BIGINT        NOT NULL REFERENCES producto,
    descripcion_leida_ocr TEXT,
    cantidad_comprada     NUMERIC(12,3),
    costo_compra_unitario NUMERIC(12,2),          -- histórico: el de ESA compra
    confianza_ocr         NUMERIC(4,3),           -- 0.000 a 1.000
    PRIMARY KEY (factura_id, producto_id),
    CONSTRAINT detalle_factura_cantidad_positiva
        CHECK (cantidad_comprada IS NULL OR cantidad_comprada > 0),
    CONSTRAINT detalle_factura_costo_no_negativo
        CHECK (costo_compra_unitario IS NULL OR costo_compra_unitario >= 0),
    CONSTRAINT detalle_factura_confianza_valida
        CHECK (confianza_ocr IS NULL OR confianza_ocr BETWEEN 0 AND 1)
);

-- HU-02 · HU-08 · R6: la conciliación es un HECHO FECHADO, no una columna que
-- se sobrescribe. Sin ella no se puede proponer nada sobre el producto, y hay
-- que poder demostrar cuándo se concilió y con qué evidencia. Conserva
-- comercio_id: la maestra la aprobó como entidad fuerte.
CREATE TABLE conciliacion_inventario (
    conciliacion_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id                  BIGINT        NOT NULL REFERENCES comercio,
    producto_id                  BIGINT        NOT NULL REFERENCES producto,
    confirmado_por_usuario_id    BIGINT        REFERENCES usuario,
    existencia_sistema_al_contar NUMERIC(12,3) NOT NULL,
    existencia_contada           NUMERIC(12,3) NOT NULL,
    metodo_conteo                TEXT          NOT NULL,
    ruta_foto_anaquel            TEXT,              -- la foto de anaquel, si el método es FOTO
    confianza_vision             NUMERIC(4,3),
    fecha_hora_conteo            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT conciliacion_metodo_valido CHECK (metodo_conteo IN ('FOTO', 'MANUAL', 'CARGA')),
    CONSTRAINT conciliacion_existencias_no_negativas
        CHECK (existencia_sistema_al_contar >= 0 AND existencia_contada >= 0),
    -- HU-02: la actualización por fotografía SIEMPRE se confirma. El
    -- clasificador propone; una persona firma.
    CONSTRAINT conciliacion_foto_confirmada
        CHECK (metodo_conteo <> 'FOTO' OR confirmado_por_usuario_id IS NOT NULL),
    -- La recomendación apunta a (producto, conciliación): así no puede citar la
    -- conciliación de otro producto.
    CONSTRAINT conciliacion_del_producto UNIQUE (producto_id, conciliacion_id)
);

CREATE INDEX ix_conciliacion_producto ON conciliacion_inventario (producto_id, fecha_hora_conteo DESC);

-- =====================================================================
-- 4 · REFERENCIA DE MERCADO (BIT-002 · RNF-04)
-- =====================================================================

-- Sólo fuentes PÚBLICAS: el tipo de fuente sólo admite los cuatro tipos
-- públicos. La v1 tenía además una columna es_publica que no admitía false; la
-- maestra la quitó por redundante con el tipo. Las fuentes son de todos: lo
-- publicado ya es público.
CREATE TABLE fuente_precio_publica (
    fuente_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_fuente       TEXT        NOT NULL,
    tipo_fuente         TEXT        NOT NULL,
    url_base            TEXT        NOT NULL,
    dias_vigencia       INTEGER     NOT NULL,      -- a los cuántos días caduca su dato
    fecha_hora_registro TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fuente_nombre_unico UNIQUE (nombre_fuente),
    CONSTRAINT fuente_tipo_valido
        CHECK (tipo_fuente IN ('TIENDA_EN_LINEA', 'MARKETPLACE', 'BASE_ABIERTA', 'INDICE_PUBLICO')),
    CONSTRAINT fuente_vigencia_positiva CHECK (dias_vigencia > 0)
);

-- HU-09: cada referencia trae origen, fecha de captura y vencimiento.
CREATE TABLE referencia_precio_mercado (
    referencia_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id              BIGINT        NOT NULL REFERENCES producto,
    fuente_id                BIGINT        NOT NULL REFERENCES fuente_precio_publica,
    url_publicacion          TEXT          NOT NULL,
    descripcion_publicada    TEXT          NOT NULL,   -- cómo venía descrito allá (R7)
    precio_publicado         NUMERIC(12,2) NOT NULL,
    confianza_emparejamiento NUMERIC(4,3),
    fecha_hora_captura       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_hora_vencimiento   TIMESTAMPTZ   NOT NULL,

    CONSTRAINT referencia_precio_positivo CHECK (precio_publicado > 0),
    CONSTRAINT referencia_vigencia_posterior CHECK (fecha_hora_vencimiento > fecha_hora_captura),
    CONSTRAINT referencia_confianza_valida
        CHECK (confianza_emparejamiento IS NULL OR confianza_emparejamiento BETWEEN 0 AND 1),
    -- Para que una recomendación sólo pueda citar referencias de SU producto
    -- (ver recomendacion_referencia).
    CONSTRAINT referencia_del_producto UNIQUE (producto_id, referencia_id)
);

CREATE INDEX ix_referencia_producto ON referencia_precio_mercado (producto_id, fecha_hora_vencimiento DESC);

-- =====================================================================
-- 5 · ANÁLISIS Y RECOMENDACIÓN — el corazón
-- =====================================================================

-- RNF-01: el análisis completo cabe en una corrida nocturna. La corrida es
-- una entidad para poder medir cuánto tardó y qué produjo.
CREATE TABLE corrida_nocturna (
    corrida_id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id                  BIGINT      NOT NULL REFERENCES comercio,
    fecha_hora_inicio            TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_hora_fin               TIMESTAMPTZ,
    estado_corrida               TEXT        NOT NULL DEFAULT 'EN_CURSO',
    cantidad_productos_evaluados INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT corrida_estado_valido CHECK (estado_corrida IN ('EN_CURSO', 'TERMINADA', 'FALLIDA')),
    CONSTRAINT corrida_fin_posterior
        CHECK (fecha_hora_fin IS NULL OR fecha_hora_fin >= fecha_hora_inicio)
);

-- Una recomendación por producto y corrida, y la decisión del dueño sobre ella
-- (la tabla autorizacion de la v1 vive aquí desde BIT-021). Entidad débil de la
-- corrida: hereda su comercio.
--
-- Aquí viven las invariantes que definen el proyecto:
--
--   BIT-003 · precio_minimo < precio_propuesto < precio_venta_regular, ambas
--   desigualdades ESTRICTAS, y sólo para LIQUIDAR. El precio mínimo es el
--   MAYOR de los dos pisos, y se calcula solo: no se captura. Las salidas son
--   un CHECK: DAR_DE_BAJA (SC-008) entra el día que el CCB la apruebe.
--
--   HU-08 · la recomendación apunta a (producto, conciliación). No puede
--   omitirla y no puede citar la de otro producto (BIT-013).
--
--   BIT-011 · se guarda qué escalón de la escalera se propuso, y si es una
--   re-propuesta, a qué recomendación anterior DEL MISMO producto sigue.
--
--   HU-04 · HU-07 · el agente recomienda, NUNCA ejecuta. La decisión es del
--   dueño del comercio (exigir_firma_del_dueno), no cambia una vez tomada
--   (decision_inmutable) y un rechazo no existe sin motivo.
--
--   MSG-018 H2 · BIT-022 · el dueño puede autorizar un precio distinto del
--   propuesto, dentro de los mismos límites estrictos. Si la propuesta salió
--   sin referencia de mercado vigente, aprobarla exige aceptar ese riesgo.
CREATE TABLE recomendacion (
    recomendacion_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    corrida_id                  BIGINT        NOT NULL REFERENCES corrida_nocturna,
    producto_id                 BIGINT        NOT NULL REFERENCES producto,
    conciliacion_id             BIGINT        NOT NULL,
    recomendacion_anterior_id   BIGINT,       -- BIT-011: la re-propuesta sabe de dónde viene
    decidido_por_usuario_id     BIGINT        REFERENCES usuario,
    accion_recomendada          TEXT          NOT NULL,
    justificacion               TEXT          NOT NULL,
    dias_sin_venta              INTEGER,
    capital_inmovilizado        NUMERIC(12,2),

    -- BIT-012. Qué afinidad climática se usó en ESTA corrida. Sin guardarla, la
    -- justificación no se puede auditar después: el dueño puede cambiarla
    -- mañana y entonces nadie sabría con qué se decidió hoy.
    afinidad_climatica_aplicada TEXT,

    -- sólo LIQUIDAR
    precio_venta_regular        NUMERIC(12,2),     -- copia del precio al recomendar
    precio_minimo_por_costo     NUMERIC(12,2),
    precio_minimo_por_descuento NUMERIC(12,2),
    precio_minimo               NUMERIC(12,2) GENERATED ALWAYS AS
                                    (GREATEST(precio_minimo_por_costo, precio_minimo_por_descuento)) STORED,
    precio_propuesto            NUMERIC(12,2),
    recuperacion_esperada       NUMERIC(12,2),
    escalon_descuento           SMALLINT,          -- BIT-011: 1 = 10 %, 2 = 20 %…
    sin_contraste_de_mercado    BOOLEAN       NOT NULL DEFAULT false,   -- BIT-002: sin referencia vigente

    -- sólo RETENER
    fecha_reactivacion_estimada DATE,
    -- sólo RESURTIR
    cantidad_a_resurtir         NUMERIC(12,3),

    fecha_hora_generacion       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- La decisión del dueño.
    estado_decision             TEXT          NOT NULL DEFAULT 'PENDIENTE',
    fecha_hora_decision         TIMESTAMPTZ,
    motivo_rechazo              TEXT,
    precio_autorizado           NUMERIC(12,2),
    acepto_riesgo_sin_contraste BOOLEAN       NOT NULL DEFAULT false,

    CONSTRAINT recomendacion_unica_por_corrida UNIQUE (corrida_id, producto_id),
    CONSTRAINT recomendacion_del_producto      UNIQUE (recomendacion_id, producto_id),

    -- BIT-013. La conciliación es del MISMO producto. La versión anterior del
    -- esquema dejaba citar la conciliación de cualquier otro producto (MSG-005).
    CONSTRAINT recomendacion_conciliacion_del_producto FOREIGN KEY (producto_id, conciliacion_id)
        REFERENCES conciliacion_inventario (producto_id, conciliacion_id),
    CONSTRAINT recomendacion_anterior_del_producto FOREIGN KEY (recomendacion_anterior_id, producto_id)
        REFERENCES recomendacion (recomendacion_id, producto_id),
    CONSTRAINT recomendacion_anterior_no_es_ella CHECK (
        recomendacion_anterior_id IS NULL OR recomendacion_anterior_id <> recomendacion_id
    ),

    CONSTRAINT recomendacion_accion_valida
        CHECK (accion_recomendada IN ('LIQUIDAR', 'RETENER', 'RESURTIR', 'ABSTENERSE')),

    -- El precio propuesto existe si y sólo si la salida lleva precio.
    CONSTRAINT recomendacion_precio_solo_liquidar CHECK (
        (accion_recomendada = 'LIQUIDAR' AND precio_propuesto            IS NOT NULL
                                         AND precio_venta_regular        IS NOT NULL
                                         AND precio_minimo_por_costo     IS NOT NULL
                                         AND precio_minimo_por_descuento IS NOT NULL)
        OR
        (accion_recomendada <> 'LIQUIDAR' AND precio_propuesto IS NULL)
    ),

    -- BIT-003. Las dos desigualdades, estrictas. Proponer el piso cumple los
    -- guardarraíles y aun así está mal, pero eso no lo puede juzgar la base:
    -- lo verifica la prueba de recuperación esperada (7.2).
    CONSTRAINT recomendacion_precio_entre_limites CHECK (
        accion_recomendada <> 'LIQUIDAR' OR (
            precio_propuesto > GREATEST(precio_minimo_por_costo, precio_minimo_por_descuento)
            AND precio_propuesto < precio_venta_regular
        )
    ),

    CONSTRAINT recomendacion_pisos_no_negativos CHECK (
        (precio_minimo_por_costo     IS NULL OR precio_minimo_por_costo     >= 0) AND
        (precio_minimo_por_descuento IS NULL OR precio_minimo_por_descuento >= 0)
    ),

    CONSTRAINT recomendacion_escalon_solo_liquidar CHECK (
        escalon_descuento IS NULL OR (accion_recomendada = 'LIQUIDAR' AND escalon_descuento BETWEEN 1 AND 9)
    ),

    CONSTRAINT recomendacion_contraste_solo_liquidar
        CHECK (NOT sin_contraste_de_mercado OR accion_recomendada = 'LIQUIDAR'),

    -- RETENER siempre dice CUÁNDO vuelve; si no se sabe, la salida correcta
    -- es ABSTENERSE, no retener indefinidamente (BIT-001).
    CONSTRAINT recomendacion_retener_con_fecha CHECK (
        accion_recomendada <> 'RETENER' OR fecha_reactivacion_estimada IS NOT NULL
    ),

    CONSTRAINT recomendacion_resurtir_con_cantidad CHECK (
        accion_recomendada <> 'RESURTIR' OR (cantidad_a_resurtir IS NOT NULL AND cantidad_a_resurtir > 0)
    ),

    CONSTRAINT recomendacion_afinidad_valida CHECK (
        afinidad_climatica_aplicada IS NULL OR
        afinidad_climatica_aplicada IN ('ALTA', 'MEDIA', 'BAJA', 'NO_APLICA')
    ),

    -- La decisión: pendiente, o tomada por alguien en un momento.
    CONSTRAINT recomendacion_estado_decision_valido
        CHECK (estado_decision IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    CONSTRAINT recomendacion_decision_completa CHECK (
        (estado_decision = 'PENDIENTE') = (decidido_por_usuario_id IS NULL) AND
        (estado_decision = 'PENDIENTE') = (fecha_hora_decision IS NULL)
    ),
    -- HU-07: «que la siguiente venga mejor planteada» exige saber por qué se
    -- rechazó esta.
    CONSTRAINT recomendacion_rechazo_con_motivo CHECK (
        estado_decision <> 'RECHAZADA' OR (motivo_rechazo IS NOT NULL
                                           AND length(trim(motivo_rechazo)) > 0)
    ),
    CONSTRAINT recomendacion_motivo_solo_si_rechazo CHECK (
        estado_decision = 'RECHAZADA' OR motivo_rechazo IS NULL
    ),

    -- MSG-018 H2. El precio que autorizó el dueño queda guardado: existe si y
    -- sólo si se aprobó una liquidación, y respeta la misma regla de BIT-003.
    CONSTRAINT recomendacion_precio_autorizado_si_aprobada CHECK (
        (estado_decision = 'APROBADA' AND accion_recomendada = 'LIQUIDAR') = (precio_autorizado IS NOT NULL)
    ),
    CONSTRAINT recomendacion_precio_autorizado_entre_limites CHECK (
        precio_autorizado IS NULL OR (
            precio_autorizado > GREATEST(precio_minimo_por_costo, precio_minimo_por_descuento)
            AND precio_autorizado < precio_venta_regular
        )
    ),
    -- BIT-002. Aprobar una propuesta sin referencia de mercado vigente es
    -- posible, pero el dueño tiene que decir que lo sabe.
    CONSTRAINT recomendacion_acepta_riesgo CHECK (
        NOT (estado_decision = 'APROBADA' AND sin_contraste_de_mercado AND NOT acepto_riesgo_sin_contraste)
    )
);

CREATE INDEX ix_recomendacion_corrida  ON recomendacion (corrida_id);
CREATE INDEX ix_recomendacion_producto ON recomendacion (producto_id, fecha_hora_generacion DESC);

-- Qué referencias de mercado sostuvieron el precio. Sin esto, «contrastado
-- con el mercado» es una afirmación sin respaldo (HU-09). BIT-013: sólo puede
-- citar referencias del MISMO producto de la recomendación; producto_id
-- completa las dos llaves compuestas.
CREATE TABLE recomendacion_referencia (
    referencia_id    BIGINT NOT NULL,
    recomendacion_id BIGINT NOT NULL,
    producto_id      BIGINT NOT NULL,
    PRIMARY KEY (referencia_id, recomendacion_id),
    CONSTRAINT recomendacion_referencia_de_la_recomendacion
        FOREIGN KEY (recomendacion_id, producto_id)
        REFERENCES recomendacion (recomendacion_id, producto_id) ON DELETE CASCADE,
    CONSTRAINT recomendacion_referencia_del_producto
        FOREIGN KEY (producto_id, referencia_id)
        REFERENCES referencia_precio_mercado (producto_id, referencia_id)
);

-- =====================================================================
-- 6 · CAMPAÑA
-- =====================================================================

-- La campaña de liquidación sale sólo de una recomendación APROBADA
-- (disparador exigir_autorizacion_aprobada) y guarda también su resultado
-- (HU-12; la tabla resultado_campana de la v1 vive aquí desde la revisión).
CREATE TABLE campana (
    campana_id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id                  BIGINT        NOT NULL REFERENCES comercio,
    recomendacion_id             BIGINT        NOT NULL REFERENCES recomendacion,
    texto_mensaje                TEXT          NOT NULL,
    ruta_imagen_promocional      TEXT,
    fecha_hora_inicio            TIMESTAMPTZ   NOT NULL,
    fecha_hora_fin               TIMESTAMPTZ   NOT NULL,
    fecha_alta                   DATE          NOT NULL DEFAULT CURRENT_DATE,
    unidades_vendidas            NUMERIC(12,3),
    ingreso_obtenido             NUMERIC(12,2),
    capital_recuperado           NUMERIC(12,2),
    fecha_hora_calculo_resultado TIMESTAMPTZ,
    CONSTRAINT campana_una_por_recomendacion UNIQUE (recomendacion_id),
    CONSTRAINT campana_ventana_valida CHECK (fecha_hora_fin > fecha_hora_inicio),
    -- El resultado se calcula completo o no se calcula.
    CONSTRAINT campana_resultado_completo CHECK (
        num_nulls(unidades_vendidas, ingreso_obtenido, capital_recuperado,
                  fecha_hora_calculo_resultado) IN (0, 4)
    ),
    CONSTRAINT campana_resultado_no_negativo CHECK (
        (unidades_vendidas IS NULL OR unidades_vendidas >= 0) AND
        (ingreso_obtenido  IS NULL OR ingreso_obtenido  >= 0)
    )
);

-- =====================================================================
-- 7 · CLIENTES Y ENVÍOS
-- =====================================================================

-- RG-01: el contacto respeta consentimiento y frecuencia máxima.
CREATE TABLE cliente (
    cliente_id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id               BIGINT      NOT NULL REFERENCES comercio,
    nombre_cliente            TEXT,
    telefono                  TEXT,
    correo_electronico        TEXT,
    fecha_hora_consentimiento TIMESTAMPTZ,
    fecha_hora_revocacion     TIMESTAMPTZ,
    maximo_envios_por_mes     SMALLINT    NOT NULL DEFAULT 4,
    CONSTRAINT cliente_max_envios_valido CHECK (maximo_envios_por_mes BETWEEN 0 AND 31),
    CONSTRAINT cliente_revocacion_posterior CHECK (
        fecha_hora_revocacion IS NULL OR fecha_hora_consentimiento IS NULL OR
        fecha_hora_revocacion >= fecha_hora_consentimiento
    )
);

-- Hereda el comercio de la campaña; envio_mismo_comercio exige que el cliente
-- sea de ese comercio.
CREATE TABLE envio_campana (
    envio_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campana_id       BIGINT      NOT NULL REFERENCES campana,
    cliente_id       BIGINT      NOT NULL REFERENCES cliente,
    canal_envio      TEXT        NOT NULL,
    fecha_hora_envio TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT envio_unico UNIQUE (campana_id, cliente_id),
    CONSTRAINT envio_canal_valido CHECK (canal_envio IN ('APP', 'CORREO', 'WHATSAPP'))
);

-- =====================================================================
-- 8 · VISTAS DE APOYO
-- =====================================================================

-- La última conciliación de cada producto: lo que el motor necesita para
-- saber si puede proponer algo sobre él. Dos conteos a la misma hora se
-- desempatan por el más reciente en capturarse (MSG-018 H3).
CREATE VIEW v_conciliacion_vigente AS
SELECT DISTINCT ON (producto_id)
       comercio_id, producto_id, conciliacion_id, existencia_contada, fecha_hora_conteo,
       (existencia_sistema_al_contar - existencia_contada) AS diferencia
FROM   conciliacion_inventario
ORDER  BY producto_id, fecha_hora_conteo DESC, conciliacion_id DESC;

-- Referencias de mercado que siguen vigentes hoy. Sin una de estas, el
-- sistema se abstiene de proponer precio en vez de inventarlo (R7).
CREATE VIEW v_referencia_vigente AS
SELECT producto_id, referencia_id, precio_publicado, fecha_hora_captura, fecha_hora_vencimiento
FROM   referencia_precio_mercado
WHERE  fecha_hora_vencimiento > now();

-- =====================================================================
-- 9 · DISPARADORES: AISLAMIENTO Y DECISIÓN (BIT-021)
--
-- Cada regla lleva su nombre al principio del mensaje y como CONSTRAINT del
-- error: datos_prueba.sql y verificar.py comprueban que cada rechazo salga
-- POR esa regla. Las cuatro primeras son las de las entidades débiles; las
-- cuatro siguientes cubren relaciones entre entidades fuertes que la v1
-- protegía con llaves compuestas y la lista de la maqueta no nombraba
-- (BIT-022). PostgreSQL dispara en orden alfabético por nombre del
-- disparador: por eso decision_inmutable va antes que exigir_firma_del_dueno.
-- =====================================================================

-- Sale con el nombre de la regla, para que el rechazo diga POR QUÉ.
CREATE OR REPLACE FUNCTION rechazar(p_regla TEXT, p_mensaje TEXT)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '%: %', p_regla, p_mensaje
        USING ERRCODE = 'check_violation', CONSTRAINT = p_regla;
END;
$$;

-- --- entidades débiles ----------------------------------------------

CREATE OR REPLACE FUNCTION detalle_venta_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT comercio_id FROM venta WHERE venta_id = NEW.venta_id) IS DISTINCT FROM
       (SELECT comercio_id FROM producto WHERE producto_id = NEW.producto_id) THEN
        PERFORM rechazar('detalle_venta_mismo_comercio',
            'el renglón mezcla la venta de un comercio con el producto de otro.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_detalle_venta_mismo_comercio
    BEFORE INSERT OR UPDATE ON detalle_venta
    FOR EACH ROW EXECUTE FUNCTION detalle_venta_mismo_comercio();

CREATE OR REPLACE FUNCTION detalle_factura_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT comercio_id FROM factura_proveedor WHERE factura_id = NEW.factura_id) IS DISTINCT FROM
       (SELECT comercio_id FROM producto WHERE producto_id = NEW.producto_id) THEN
        PERFORM rechazar('detalle_factura_mismo_comercio',
            'el renglón empareja la factura de un comercio con el producto de otro.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_detalle_factura_mismo_comercio
    BEFORE INSERT OR UPDATE ON detalle_factura_proveedor
    FOR EACH ROW EXECUTE FUNCTION detalle_factura_mismo_comercio();

CREATE OR REPLACE FUNCTION envio_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT comercio_id FROM campana WHERE campana_id = NEW.campana_id) IS DISTINCT FROM
       (SELECT comercio_id FROM cliente WHERE cliente_id = NEW.cliente_id) THEN
        PERFORM rechazar('envio_mismo_comercio',
            'una campaña sólo le escribe a clientes de su propio comercio.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_envio_mismo_comercio
    BEFORE INSERT OR UPDATE ON envio_campana
    FOR EACH ROW EXECUTE FUNCTION envio_mismo_comercio();

CREATE OR REPLACE FUNCTION recomendacion_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT comercio_id FROM corrida_nocturna WHERE corrida_id = NEW.corrida_id) IS DISTINCT FROM
       (SELECT comercio_id FROM producto WHERE producto_id = NEW.producto_id) THEN
        PERFORM rechazar('recomendacion_mismo_comercio',
            'la corrida de un comercio no recomienda sobre el producto de otro.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_recomendacion_mismo_comercio
    BEFORE INSERT OR UPDATE OF corrida_id, producto_id ON recomendacion
    FOR EACH ROW EXECUTE FUNCTION recomendacion_mismo_comercio();

-- --- entidades fuertes que se citan entre sí -------------------------

CREATE OR REPLACE FUNCTION producto_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.categoria_id IS NOT NULL AND
       (SELECT comercio_id FROM categoria WHERE categoria_id = NEW.categoria_id) <> NEW.comercio_id THEN
        PERFORM rechazar('producto_mismo_comercio', 'la categoría es de otro comercio.');
    END IF;
    IF NEW.temporada_id IS NOT NULL AND
       (SELECT comercio_id FROM temporada WHERE temporada_id = NEW.temporada_id) <> NEW.comercio_id THEN
        PERFORM rechazar('producto_mismo_comercio', 'la temporada es de otro comercio.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_producto_mismo_comercio
    BEFORE INSERT OR UPDATE OF comercio_id, categoria_id, temporada_id ON producto
    FOR EACH ROW EXECUTE FUNCTION producto_mismo_comercio();

CREATE OR REPLACE FUNCTION conciliacion_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT comercio_id FROM producto WHERE producto_id = NEW.producto_id) <> NEW.comercio_id THEN
        PERFORM rechazar('conciliacion_mismo_comercio', 'el producto contado es de otro comercio.');
    END IF;
    IF NEW.confirmado_por_usuario_id IS NOT NULL AND
       (SELECT comercio_id FROM usuario WHERE usuario_id = NEW.confirmado_por_usuario_id)
           IS DISTINCT FROM NEW.comercio_id THEN
        PERFORM rechazar('conciliacion_mismo_comercio', 'quien confirma el conteo es de otro comercio.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_conciliacion_mismo_comercio
    BEFORE INSERT OR UPDATE ON conciliacion_inventario
    FOR EACH ROW EXECUTE FUNCTION conciliacion_mismo_comercio();

CREATE OR REPLACE FUNCTION factura_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.recibida_por_usuario_id IS NOT NULL AND
       (SELECT comercio_id FROM usuario WHERE usuario_id = NEW.recibida_por_usuario_id)
           IS DISTINCT FROM NEW.comercio_id THEN
        PERFORM rechazar('factura_mismo_comercio', 'quien recibe la factura es de otro comercio.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_factura_mismo_comercio
    BEFORE INSERT OR UPDATE ON factura_proveedor
    FOR EACH ROW EXECUTE FUNCTION factura_mismo_comercio();

CREATE OR REPLACE FUNCTION campana_mismo_comercio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT c.comercio_id FROM recomendacion r JOIN corrida_nocturna c USING (corrida_id)
        WHERE r.recomendacion_id = NEW.recomendacion_id) <> NEW.comercio_id THEN
        PERFORM rechazar('campana_mismo_comercio', 'la recomendación es de otro comercio.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_campana_mismo_comercio
    BEFORE INSERT OR UPDATE ON campana
    FOR EACH ROW EXECUTE FUNCTION campana_mismo_comercio();

-- --- quién decide y qué no cambia ------------------------------------

-- HU-04. Sólo el DUENO del comercio de la corrida decide. La cuenta LLM —el
-- agente aprobándose a sí mismo— o el dueño de otro comercio, no.
CREATE OR REPLACE FUNCTION exigir_firma_del_dueno()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.decidido_por_usuario_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM   usuario u
        JOIN   corrida_nocturna c ON c.corrida_id = NEW.corrida_id
        WHERE  u.usuario_id = NEW.decidido_por_usuario_id
          AND  u.codigo_rol = 'DUENO'
          AND  u.comercio_id = c.comercio_id
    ) THEN
        PERFORM rechazar('exigir_firma_del_dueno',
            'sólo el dueño del comercio de la corrida decide sobre una recomendación.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_recomendacion_exigir_firma_del_dueno
    BEFORE INSERT OR UPDATE ON recomendacion
    FOR EACH ROW EXECUTE FUNCTION exigir_firma_del_dueno();

-- Una decisión tomada no se cambia: ni el sentido, ni quién, ni cuándo, ni el
-- motivo, ni el precio. Si el dueño cambia de opinión, la siguiente corrida
-- produce una recomendación nueva (BIT-016).
CREATE OR REPLACE FUNCTION decision_inmutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.estado_decision <> 'PENDIENTE' AND
       (NEW.estado_decision, NEW.decidido_por_usuario_id, NEW.fecha_hora_decision,
        NEW.motivo_rechazo, NEW.precio_autorizado, NEW.acepto_riesgo_sin_contraste)
       IS DISTINCT FROM
       (OLD.estado_decision, OLD.decidido_por_usuario_id, OLD.fecha_hora_decision,
        OLD.motivo_rechazo, OLD.precio_autorizado, OLD.acepto_riesgo_sin_contraste) THEN
        PERFORM rechazar('decision_inmutable',
            'la recomendación ya fue ' || lower(OLD.estado_decision) || ' y la decisión no cambia.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_recomendacion_decision_inmutable
    BEFORE UPDATE ON recomendacion
    FOR EACH ROW EXECUTE FUNCTION decision_inmutable();

-- Propuesta de Felipe (BIT-021): la llave compuesta de la v1 también impedía
-- cambiarle el rol o el comercio a quien ya firmó. Sin esto, degradar después
-- al dueño dejaría decisiones firmadas por un ANALISTA.
CREATE OR REPLACE FUNCTION usuario_firmante_inmutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.codigo_rol IS DISTINCT FROM OLD.codigo_rol OR
        NEW.comercio_id IS DISTINCT FROM OLD.comercio_id) AND
       EXISTS (SELECT 1 FROM recomendacion WHERE decidido_por_usuario_id = OLD.usuario_id) THEN
        PERFORM rechazar('usuario_firmante_inmutable',
            'el usuario ya decidió recomendaciones; no cambia de rol ni de comercio.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_usuario_firmante_inmutable
    BEFORE UPDATE OF codigo_rol, comercio_id ON usuario
    FOR EACH ROW EXECUTE FUNCTION usuario_firmante_inmutable();

-- Lo que es de un comercio se queda en ese comercio. Nada legítimo necesita
-- mover un producto, una venta o una campaña a otra tienda, y moverlo
-- arrastraría a sus renglones débiles, que no guardan el comercio.
CREATE OR REPLACE FUNCTION comercio_inmutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.comercio_id IS DISTINCT FROM OLD.comercio_id THEN
        PERFORM rechazar('comercio_inmutable',
            'un renglón de ' || TG_TABLE_NAME || ' no se mueve a otro comercio.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_categoria_comercio_inmutable     BEFORE UPDATE OF comercio_id ON categoria
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_temporada_comercio_inmutable     BEFORE UPDATE OF comercio_id ON temporada
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_producto_comercio_inmutable      BEFORE UPDATE OF comercio_id ON producto
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_venta_comercio_inmutable         BEFORE UPDATE OF comercio_id ON venta
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_factura_comercio_inmutable       BEFORE UPDATE OF comercio_id ON factura_proveedor
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_conciliacion_comercio_inmutable  BEFORE UPDATE OF comercio_id ON conciliacion_inventario
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_corrida_comercio_inmutable       BEFORE UPDATE OF comercio_id ON corrida_nocturna
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_campana_comercio_inmutable       BEFORE UPDATE OF comercio_id ON campana
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();
CREATE TRIGGER tr_cliente_comercio_inmutable       BEFORE UPDATE OF comercio_id ON cliente
    FOR EACH ROW EXECUTE FUNCTION comercio_inmutable();

-- HU-04: el agente recomienda, NUNCA ejecuta. Una campaña no existe sin la
-- aprobación del dueño. Ya existía en la v1 (contra autorizacion); desde
-- BIT-021 lee recomendacion.estado_decision.
CREATE OR REPLACE FUNCTION exigir_autorizacion_aprobada()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT estado_decision FROM recomendacion WHERE recomendacion_id = NEW.recomendacion_id)
       IS DISTINCT FROM 'APROBADA' THEN
        PERFORM rechazar('exigir_autorizacion_aprobada',
            'la campaña apunta a una recomendación que no está aprobada. '
            'El agente recomienda, nunca ejecuta.');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_campana_exige_aprobacion
    BEFORE INSERT OR UPDATE ON campana
    FOR EACH ROW EXECUTE FUNCTION exigir_autorizacion_aprobada();

COMMIT;
