# Escenarios del simulador

Salidas esperadas escritas a mano. Comercio sintético con fecha de corte 2026-09-22.
Política de pisos del comercio: económico = 70 % del costo · comercial = 30 % del precio original. El escalón es el de BIT-011: 1 = 10 % de rebaja, 2 = 20 %.

| # | Escenario | SKU | Salida esperada | Folios |
|---|---|---|---|---|
| E01 | Permanente parado sobre el umbral → LIQUIDAR | `LLA-014` | LIQUIDAR a $468.00 | BIT-010, BIT-011, BIT-003 |
| E02 | Precio propuesto IGUAL al mínimo → la base lo rechaza | `LLA-014` | rechazo de la base (`recomendacion_precio_entre_limites`) | BIT-003 |
| E03 | Precio propuesto IGUAL al original → la base lo rechaza | `LLA-014` | rechazo de la base (`recomendacion_precio_entre_limites`) | BIT-003 |
| E04 | Estacional DENTRO de su ventana → RETENER | `IMP-003` | RETENER | BIT-001 |
| E05 | Estacional FUERA de ventana con ventana futura → RETENER | `ADO-021` | RETENER | BIT-001 |
| E06 | Perecedero que no alcanza a venderse antes de caducar → LIQUIDAR | `SEL-007` | LIQUIDAR a $85.50 | BIT-010, BIT-011 |
| E07 | Perecedero con holgura → sin recomendación | `PEG-002` | sin recomendación | BIT-010 |
| E08 | Permanente por DEBAJO del umbral → sin recomendación | `DES-010` | sin recomendación | BIT-010 |
| E09 | Permanente casi sin historial y más de un año parado → LIQUIDAR | `TUB-100` | LIQUIDAR a $306.00 | BIT-010, BIT-011 |
| E10 | Afinidad BAJA adelanta el umbral → LIQUIDAR | `CAL-030` | LIQUIDAR a $1032.00 | BIT-012, BIT-010, BIT-011 |
| E11 | Afinidad ALTA retrasa el umbral → sin recomendación | `VEN-040` | sin recomendación | BIT-012, BIT-010 |
| E12 | Estacional de invierno en zona tropical → ABSTENERSE | `CHA-070` | ABSTENERSE | BIT-012, BIT-001 |
| E13 | Existencia bajo el punto de reorden → RESURTIR | `MAR-001` | RESURTIR | HU-11 |
| E14 | Producto sin conciliación vigente → no se evalúa | `SIN-001` | no se evalúa | HU-08, R6 |
| E15 | Producto sin costo unitario → ABSTENERSE | `NOC-001` | ABSTENERSE | BIT-011, R1 |
| E16 | Sin referencia de mercado vigente → LIQUIDAR declarándolo | `PIN-012` | LIQUIDAR a $432.00 | BIT-002, BIT-011 |
| E17 | Campaña aprobada que terminó sin vender → siguiente escalón | `LLA-014` | LIQUIDAR a $416.00 | BIT-011 |
| E18 | El piso no deja ningún escalón → ABSTENERSE | `PIS-001` | ABSTENERSE | BIT-011, BIT-003 |
| E20 | Rechazada por precio bajo → re-propuesta un escalón ARRIBA | `CAL-030` | LIQUIDAR a $1161.00 | BIT-016, BIT-011, HU-07 |
| E19 | Producto de OTRO comercio → no entra a la corrida | `TUE-001` | no se evalúa | BIT-013, RNF-04 |
