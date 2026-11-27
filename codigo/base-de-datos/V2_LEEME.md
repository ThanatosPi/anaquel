# Esquema de base de datos, versión 2

El rediseño de la base de datos que pidió la maestra de Base de Datos. Llevó dos revisiones suyas
(6 y 8 de octubre de 2026) y una del equipo, se trabajó en la rama `bd/esquema-v2` y **la maestra
aprobó la maqueta tal como estaba** (Sebastián, 20 de octubre). Desde ese día `db/schema.sql` es la
versión 2 (BIT-022, en `bitacora.md`). La v1 completa sigue en el commit `97f1611`.

## Qué hay

- **`schema.sql`**: el esquema v2 (PostgreSQL 14+). 21 tablas, catálogos precargados (`rol`,
  `unidad_medida` y los 21 municipios), 2 vistas, la función `zona_por_coordenadas()` y los
  disparadores de aislamiento y de decisión (sección 9).
- **`datos_prueba.sql`**: dos comercios de prueba (sección A), 47 bloques que la base tiene que
  rechazar, cada uno por su nombre (sección B), y 8 comprobaciones que tiene que resolver bien
  (sección C).
- **`comparar_esquema.py`**: compara `schema.sql` contra la maqueta, tabla por tabla, columna por
  columna y tipo por tipo. Cuando la app exporte su esquema de Room, también comprueba que el
  teléfono no guarde nada que no exista en la v2.
- **`maqueta_er_v2.py`**: la propuesta aprobada, como datos (tabla, columna, tipo, nombre anterior,
  nota). Genera:
  - `maqueta_er_v2.html`: diagrama, aislamiento, convención de nombres, diccionario de las 21
    tablas, lo que desaparece y las preguntas con su respuesta;
  - `maqueta_er_v2_imprimir.html`: sólo el diagrama, en dos hojas carta que se unen.
  Si cambia un nombre, se cambia en el script, se regenera y `comparar_esquema.py` exige que
  `schema.sql` cambie igual:

  ```
  python db/maqueta_er_v2.py
  ```

  El PDF se saca con Chrome (ruta absoluta de Windows en `--print-to-pdf`):

  ```
  "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless --no-pdf-header-footer --virtual-time-budget=8000 --print-to-pdf="C:\ruta\Modelo ER Anaquel v2 ESVM.pdf" "file:///C:/ruta/al/repo/db/maqueta_er_v2_imprimir.html"
  ```
- **`REVISION_MAESTRA.md`**: la revisión del 8 de octubre, cómo se leyó cada punto y qué quedó.
- **`REVISION_V2.md`**: las dos rondas de Felipe, con la respuesta de REACH debajo de cada punto.

## Dónde quedó el modelo

| Tema | Cómo queda |
|---|---|
| Tablas | 26 → 21. `zona_climatica`, `ciclo_producto` y `salida` pasan a CHECK; `autorizacion` se integra a `recomendacion`, `resultado_campana` a `campana` y `afinidad_climatica` a `categoria`/`producto` (con su zona). Tabla nueva: `rol`. |
| Entidades débiles | `recomendacion`, `envio_campana`, `detalle_venta` y `detalle_factura_proveedor` no llevan `comercio_id`: lo heredan. Los detalles se identifican por `(venta_id, producto_id)` y `(factura_id, producto_id)`. `conciliacion_inventario` se queda fuerte (respuesta a la pregunta 1). |
| Aislamiento entre comercios | Lo cuidan 13 reglas en disparadores (BIT-021 y BIT-022): las 9 de la maqueta y 4 para relaciones entre entidades fuertes que la v1 protegía con llaves compuestas. |
| Zona | El comercio guarda el id de su municipio, de un catálogo precargado: los 5 de la v1 y las 16 alcaldías de la CDMX (Marco Geoestadístico y Compendio geográfico municipal del INEGI). |
| Roles | Tabla `rol`: DUENO, ADMINISTRADOR, ANALISTA y LLM. Sólo DUENO decide; una cuenta LLM por comercio; ADMINISTRADOR es el único sin comercio. |
| Decisión del dueño | Vive en `recomendacion` (respuesta a la pregunta 3), con el precio autorizado y la aceptación del riesgo sin contraste (MSG-018 H2; columnas agregadas después de la aprobación, hay que avisarle a la maestra). |
| Nombres | 8 tablas y 87 columnas renombradas con siete reglas (sección 4 de la maqueta). |

## Los rechazos que se movieron

De B1 a B23 son los mismos casos de la v1. En esta tabla están los que hoy detiene otra regla:

| Bloque | En la v1 | En la v2 | Por qué |
|---|---|---|---|
| B6 | mensaje del disparador | `exigir_autorizacion_aprobada` | El disparador ahora dice su nombre al fallar. |
| B7 | `autorizacion_rechazo_con_motivo` | `recomendacion_rechazo_con_motivo` | La decisión vive en `recomendacion`. |
| B8 | `fuente_solo_publica` | `fuente_tipo_valido` | Se quitó `es_publica` (siempre era verdadero); BIT-002 lo cuida el tipo de fuente. |
| B11 | `recomendacion_salida_fkey` | `recomendacion_accion_valida` | La salida es CHECK, no tabla. |
| B12 | `afinidad_ambito` | `categoria_afinidad_con_zona` | La afinidad vive en la categoría o el producto: el ámbito ya no puede ser ambiguo. Lo que sí puede faltar es la zona. |
| B14 | `producto_categoria_del_comercio` | `producto_mismo_comercio` | Llave compuesta → disparador. |
| B16 | `recomendacion_corrida_del_comercio` | `recomendacion_mismo_comercio` | La recomendación ya no lleva `comercio_id`. |
| B17, B18 | `autorizacion_firma_del_dueno` | `exigir_firma_del_dueno` | Llave compuesta con el rol → disparador. La cuenta SISTEMA ahora es LLM. |
| B22 | `venta_renglon_producto_del_comercio` | `detalle_venta_mismo_comercio` | Entidad débil sin `comercio_id`. |

**Nuevos, B24 a B47:**
- **los otros disparadores:**
  - detalle de factura (B24), envío (B25), firmante inmutable (B26, B27);
  - comercio inmutable (B28), decisión inmutable (B29), campaña sobre PENDIENTE (B30);
  - campaña, conteo y factura del mismo comercio (B31 a B33);
- **H2:** precio autorizado (B34 a B36);
- **roles:** ADMINISTRADOR con comercio y segunda cuenta LLM (B37, B38);
- **ubicación:** MANUAL con coordenadas y GPS sin municipio (B39, B40);
- **duplicados:** folio de venta repetido y producto repetido en un ticket o una factura (B41 a B43);
- **resto:** decisión sin firmante (B44), stock mínimo sobre máximo (B45), afinidad propia sin
  zona (B46) y resultado de campaña a medias (B47).

## Lo que queda pendiente

1. Solicitud de cambio: **escrita como SC-013** (21/oct, `build_sc013.py`), con el esquema, la base
   local y las cuatro pantallas en cuatro bloques. Falta que la resuelva el CCB.
2. Avisarle a la maestra de las tres columnas de H2 y de los cuatro disparadores agregados.
3. Con la regla del INEGI, Nezahualcóyotl, Toluca y Saltillo saldrían en otra zona; no se
   reclasificaron (decide Sebastián).
4. `verification/msg018/review.py` (de AEGIS) reproduce los hallazgos de la v1; AEGIS decide si se
   porta a la v2 o se archiva (MSG-027).

## Reglas

- `git pull` antes de empezar, commits chicos, nunca `git push --force`.
- El contrato `corrida.json` no cambia: la app no lee nombres de tablas. `municipios.json` y
  `catalogo.json` sí llevan los nombres v2, porque son el reflejo de sus tablas.
- Las decisiones del dominio de `CLAUDE.md` siguen igual. Lo que cambia es dónde y con qué nombre
  se guardan, y que el aislamiento lo cuidan disparadores en vez de llaves compuestas.
- Se pierde quién declaró una afinidad climática y cuándo. Si la maestra o el equipo lo quieren,
  se agrega a `categoria`.

## Cómo se verifica (se pega la salida, no se resume)

1. `python db/comparar_esquema.py` → `ESQUEMA OK: 21 tablas, 172 columnas iguales a la maqueta …`
2. `simulador\verificar.bat` (pide la contraseña de `postgres`) → carga el esquema y comprueba que hay
   21 tablas. Corre `datos_prueba.sql` y exige cada rechazo de la sección B por su nombre, en
   orden. Después carga los comercios sintéticos y compara `municipios.json` y `catalogo.json`
   renglón por renglón contra la base. Debe terminar en
   `SIMULADOR OK: 20 escenarios, 47 rechazos esperados`.
3. `git diff --ignore-cr-at-eol --stat -- simulador/salida/corrida*.json` vacío: el contrato con la
   app no se movió.
4. `app\gradlew.bat :mobile:assembleDebug :mobile:testDebugUnitTest`: la app lee los catálogos v2.
