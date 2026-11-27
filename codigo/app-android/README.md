# Anaquel Android

Versión 0.2.2 de la aplicación del dueño del comercio, según MSG-007, MSG-013 y MSG-015. Kotlin,
Jetpack Compose y Material 3 Expressive. Los datos provienen del simulador local.

## Abrir y ejecutar

1. Abrir esta carpeta (`anaquel/app`) en Android Studio.
2. Usar JDK 21 e instalar Android SDK 37. Android Studio crea `local.properties`
   con la ubicación del SDK; ese archivo es local y no se versiona.
3. Ejecutar el módulo `mobile` en un emulador o teléfono con Android 8.0 o posterior.

Desde esta carpeta, en Windows:

```powershell
.\gradlew.bat :mobile:assembleDebug :mobile:testDebugUnitTest
.\gradlew.bat :mobile:connectedDebugAndroidTest
```

El APK se genera en `mobile/build/outputs/apk/debug/mobile-debug.apk`.
La tarea `syncSimulator` copia `corrida.json`, `corrida_2.json`, `municipios.json` y `catalogo.json`
desde `../simulador/salida/` a los assets generados. Para actualizar los datos,
REACH regenera el simulador y se vuelve a compilar. No se editan los JSON a mano.

## Alcance

Inicio, filtros y orden de recomendaciones, detalle, precio con límites estrictos,
confirmaciones, rechazo con motivo, abstención, antecedente de campaña y resumen.
Las autorizaciones existentes se respetan. Las nuevas decisiones se guardan en
DataStore, con identidad local de demostración, fecha, precio y aceptación de
referencia vencida cuando corresponde. La cola sobrevive a un reinicio; desinstalar
o borrar los datos de la app la elimina.

También incluye configuración de ubicación aproximada con confirmación y respaldo
manual de cinco zonas, Ajustes, búsqueda de los 60 productos del catálogo y conteos
físicos con historial. La cámara toma una foto y vuelve al ingreso manual: el dueño
escribe y confirma la cantidad. La foto temporal se elimina al regresar.

La ubicación GPS confirmada guarda las coordenadas aproximadas del negocio;
cambiar a modo manual las borra. Los registros GPS de 0.2.0 requieren confirmarse
de nuevo porque no incluían el punto. El inventario muestra piezas, metros, kilos
o litros y bloquea fracciones cuando la unidad no las admite. Los conteos antiguos
incompatibles se conservan como antecedentes inválidos. Ajustes pide corrección sólo
si el último conteo de ese producto es inválido. Al pasar de GPS a MANUAL, el municipio
queda vacío para que el dueño lo escriba si lo desea; una elección manual anterior
se conserva. Evidencia de estas correcciones: [MSG-015](REVIEW_MSG015.md).

Esta entrega no incluye API, autenticación de servidor, envío de decisiones o
conteos, ejecución de campañas, cambios de precios ni clasificación de imágenes.
La zona y los conteos locales no alteran las corridas recibidas del simulador.

## Estructura

- `mobile/src/main/java/mx/anaquel/app/domain`: contrato local y reglas de decisión.
- `mobile/src/main/java/mx/anaquel/app/data`: lectura del simulador y cola persistente.
- `mobile/src/main/java/mx/anaquel/app/ui`: pantallas y tema.
- `mobile/src/test`: límites del dominio y contrato.
- `mobile/src/androidTest`: recorrido real en el emulador y persistencia.
- `artifacts`: capturas de la verificación.

La revisión de ejecución y sus límites están en [IMPLEMENTACION.md](IMPLEMENTACION.md).
La evidencia vigente está en [REVIEW_MSG013.md](REVIEW_MSG013.md);
[INCREMENTO_02_VERIFICACION.md](INCREMENTO_02_VERIFICACION.md) conserva la de 0.2.0;
[VERIFICACION.md](VERIFICACION.md) conserva la del primer incremento.
Material 3 Expressive usa una versión preliminar fijada; revisar su estabilidad
antes de distribuir en producción.
