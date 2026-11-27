# Simulador de Anaquel

Dos comercios ficticios con historial, y **20 escenarios con su salida esperada escrita a mano**.
El segundo comercio no participa en ninguna salida: existe para que el motor tenga algo que **no**
debe tocar (E19, BIT-013).
Sirve para probar el motor de recomendación sin depender del comercio piloto, y para alimentar la
aplicación móvil mientras no existe la API.

**Aquí no se implementan las reglas del dominio.** Este paquete genera datos y declara qué debería
salir; quien lo calcula es el motor (zona de AEGIS, BIT-008). Los números de `escenarios.py` están
escritos a mano a propósito: si los calculara un programa, heredarían el mismo error que se quiere
detectar.

## Cómo se usa

Lo más fácil: **doble clic en `verificar.bat`**. Pide la contraseña de `postgres` si no está en la
ventana, corre todo y se queda abierto para que leas el resultado.

A mano:

```bash
python generar.py --todo      # datos, JSON de la app y tabla de escenarios
python verificar.py           # lo anterior + PostgreSQL + contrato
```

`verificar.py` tiene que terminar en:

```
SIMULADOR OK: 20 escenarios, 47 rechazos esperados
```

Desde el 20/oct/2026 corre contra el esquema v2 (BIT-022): los 47 rechazos son los de
`db/datos_prueba.sql`, cada uno por su restricción o su disparador (cuáles se movieron desde la v1:
`db/V2_LEEME.md`).

Si no imprime eso, algo se rompió y no hay que seguir. Necesita PostgreSQL 16 con la variable
`PGPASSWORD` puesta; la ruta de los binarios se puede cambiar con `PGBIN`.

## Qué hay en cada archivo

| Archivo | Qué hace |
|---|---|
| `comercio_sintetico.py` | La ferretería «El Tornillo Feliz» (60 productos, Mérida, zona por GPS) y la refaccionaria «La Tuerca» (3 productos, Toluca, zona elegida a mano); ventas, conciliaciones, referencias públicas, afinidades climáticas y municipios con coordenadas. Semilla fija: misma semilla, mismos datos. |
| `escenarios.py` | Los 20 escenarios con salida esperada, el folio que los justifica y el porqué en una frase. E17 y E20 traen además la decisión previa que la base tiene que contener: una campaña que no terminó de vender y un rechazo con motivo (BIT-016). |
| `contrato_corrida.py` | La forma del JSON que consume la app, y su validador. |
| `generar.py` | Escribe `salida/comercio_sintetico.sql`, las corridas `corrida.json` y `corrida_2.json`, `municipios.json` (zona sin API), `catalogo.json` (inventario de la app) y `escenarios.md`. |
| `verificar.py` | Carga todo contra PostgreSQL y afirma: lo válido entra, lo inválido truena **por la restricción correcta**, los escenarios son representables, la historia de E17 está completa, la ubicación resuelve a su zona y el contrato se cumple. |

`salida/` se regenera; no se edita a mano.

## Fechas

`FECHA_CORTE` es fija (22 de septiembre de 2026). Si fuera «hoy», los escenarios cambiarían de
resultado cada día y la prueba dejaría de significar algo.
