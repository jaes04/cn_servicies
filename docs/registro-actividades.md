# Registro de actividades de tratamiento (RGPD art. 30)

**Borrador técnico, pendiente de revisión jurídica.** Está redactado a partir del modelo de
datos real —ver `inventario-datos.md`— para que quien tenga formación jurídica pueda
validarlo o corregirlo sin leer el código. **Las bases legales que aparecen aquí son
propuestas, no conclusiones**, y los plazos de conservación están deliberadamente sin
rellenar: hoy el sistema no borra nada automáticamente, y decidir cuánto se guarda cada
cosa es justamente lo que hay que resolver.

**Fecha de corte:** 15 de septiembre de 2026 · **Fuente:** rama `develop`

> Según el roadmap, el servicio **no está desplegado en producción** y no trata todavía
> datos reales de familias. Confírmalo antes de entregar este documento: cambia si el
> registro describe un tratamiento en curso o uno previsto.

---

## Parte 0 — Quién es quién

| Papel | Quién | Estado |
|---|---|---|
| **Responsable del tratamiento** | Cada club deportivo cliente | Uno por contrato |
| **Encargado del tratamiento** | La plataforma `cn_servicies` | **Falta decidir la forma jurídica con la que se firma** |
| Subencargados | Hetzner (alojamiento), Cloudflare (túnel y publicación web) | Previstos, no contratados aún |
| Subencargado futuro | Proveedor SMTP, en cuanto entre la recuperación de contraseña | Sin elegir |

**Este documento tiene dos partes porque hay dos registros distintos.** El del art. 30.2 es
el que tiene que llevar el encargado —la plataforma— y es la Parte A. El del art. 30.1 lo
lleva cada club como responsable, y la Parte B es la plantilla que se le entrega ya
rellenada con lo que el sistema hace de verdad.

---

## Parte A — Registro del encargado (art. 30.2)

Contenido mínimo que exige el artículo:

**A.1 Identificación.** Nombre y datos de contacto del encargado, y de cada responsable por
cuenta del cual actúa. *Pendiente de la forma jurídica y de la lista de clubes.*

**A.2 Delegado de protección de datos.** *Pendiente de la valoración escrita sobre si es
obligatorio (art. 37 RGPD y art. 34 LOPDGDD). Hay tratamiento de datos de menores a escala,
así que la pregunta no es retórica.*

**A.3 Categorías de tratamientos realizados por cuenta de cada responsable:**

1. Alojamiento y gestión de la base de datos de deportistas, tutores y cuentas de acceso
2. Almacenamiento de documentos asociados a la ficha del deportista
3. Registro y conservación de consentimientos de los tutores
4. Registro de metadatos del certificado médico federativo
5. Gestión de temporadas, grupos, horarios y sesiones de entrenamiento
6. Registro de asistencia y generación de informes
7. Publicación del blog del club
8. Copias de seguridad y mantenimiento técnico

**A.4 Transferencias internacionales.** *No previstas.* Hetzner tiene centros de datos en la
UE y Cloudflare presta servicio de red. **Hay que verificar la ubicación efectiva y revisar
el DPA de Cloudflare**, que es donde puede aparecer una transferencia.

**A.5 Descripción general de las medidas de seguridad (art. 32.1).** Ver Parte C: se
describe lo que existe y lo que no, sin adornos.

---

## Parte B — Fichas de tratamiento del responsable (art. 30.1)

Ocho actividades. En todas, el **responsable** es el club y el **encargado** es la
plataforma; no se repite en cada ficha.

### B.1 Gestión de deportistas

- **Finalidad:** inscribir y mantener la ficha del deportista, y sostener la relación con el club
- **Interesados:** deportistas, **mayoritariamente menores de edad**
- **Datos:** nombre y apellidos, fecha de nacimiento, documento de identidad (DNI, NIE o pasaporte, opcional), sexo
- **Origen:** el propio interesado o su tutor legal, a través del club
- **Base legal propuesta:** ejecución de la relación asociativa (art. 6.1.b) y, **para menores de 14, consentimiento del titular de la patria potestad o tutela** (art. 8 RGPD y art. 7 LOPDGDD)
- **Destinatarios:** ninguno fuera del club, salvo lo que exija la federación
- **Conservación:** ⚠️ **PENDIENTE.** Hoy solo hay borrado lógico: la fila conserva nombre y DNI
- **Nota técnica:** el sistema **bloquea el alta de un menor de 14 sin consentimiento de tutor registrado**, en la misma transacción

### B.2 Gestión de tutores legales

- **Finalidad:** identificar a quien ejerce la patria potestad y puede otorgar el consentimiento
- **Interesados:** madres, padres y tutores legales
- **Datos:** nombre y apellidos, DNI, email, teléfono, parentesco
- **Base legal propuesta:** ejecución de la relación asociativa y cumplimiento de la obligación legal de recabar el consentimiento del art. 8
- **Conservación:** ⚠️ **PENDIENTE.** Ligada a la del consentimiento, que es la prueba

### B.3 Consentimientos

- **Finalidad:** acreditar la licitud del tratamiento de los datos de un menor (art. 7.1)
- **Datos:** finalidad consentida, si se concedió o se denegó, fecha de la decisión, tipo de evidencia, referencia al soporte, **dirección IP solo cuando la evidencia es formulario web**, fecha de revocación
- **Base legal propuesta:** cumplimiento de una obligación legal (art. 6.1.c): el registro existe **porque** la norma exige poder demostrar el consentimiento
- **Conservación:** ⚠️ **PENDIENTE**, y es la más delicada: el registro es **append-only** a propósito —revocar no borra, escribe `revoked_at`— porque el historial completo *es* la prueba. Un plazo de borrado aquí destruye la prueba de lo que se hizo antes
- **Nota técnica:** las cuatro finalidades son independientes y revocables por separado: tratamiento de datos, imagen, comunicaciones y datos de salud. **Una negativa se guarda igual que una concesión**, porque "dijo que no" y "no se le ha preguntado" no permiten lo mismo

### B.4 Aptitud médica federativa

- **Finalidad:** acreditar que el deportista tiene certificado médico en vigor
- **Interesados:** deportistas, mayoritariamente menores
- **Datos:** fecha de emisión, temporada que cubre —su caducidad es el final de la temporada—, quién validó el papel y cuándo
- **Categoría especial (art. 9):** sí. Es un **dato de salud, aunque esté minimizado a dos fechas**
- **Base legal propuesta:** ⚠️ **a decidir.** Consentimiento explícito (art. 9.2.a) es la vía más directa; conviene valorar si la exigencia federativa aporta alguna otra
- **Conservación:** ⚠️ **PENDIENTE**
- **Nota técnica:** **no se almacena el veredicto médico.** No hay campo `apto`, ni diagnóstico, ni observaciones: la existencia de un certificado en plazo *es* la aptitud, y el estado se calcula contra la temporada activa

### B.5 Documentos de la ficha del deportista — apagado en el despliegue inicial

- **Estado:** **la subida de archivos está apagada por defecto.** Para el despliegue inicial el club no sube papeles: registra su entrega (ficha B.5.b). Si este tratamiento se enciende algún día, esta ficha vuelve a aplicar entera
- **Finalidad:** conservar la documentación que aporta la familia
- **Datos:** archivo, título libre, nombre original del archivo, tipo, quién lo subió
- **Categoría especial (art. 9):** ⚠️ **posiblemente sí.** El tipo `MEDICAL` permite subir archivos clínicos reales
- **Base legal propuesta:** ⚠️ **a decidir, solo si se enciende**
- **Conservación:** ⚠️ **PENDIENTE.** El borrado es físico y se lleva el archivo del disco. Queda por decidir qué pasa con los archivos que ya existieran
- **Nota técnica:** **sin cifrado en reposo y sin registro de accesos.** Encendido, es el punto más expuesto del sistema

### B.5.b Entregas de papeles

- **Finalidad:** saber si cada deportista tiene entregada y en vigor la documentación que el club le pide
- **Interesados:** deportistas, mayoritariamente menores
- **Datos:** tipo de papel —solicitud de licencia, documento de identidad o permiso de viaje—, temporada a la que corresponde la licencia, caducidad del documento de identidad, fechas de salida y vuelta del permiso de viaje, fecha de entrega y quién la anotó
- **Lo que no se guarda:** el papel, que se queda en el club; el número del documento; el destino del viaje; notas de ningún tipo
- **Base legal propuesta:** ejecución de la relación asociativa (art. 6.1.b) para licencia y documento de identidad; para el permiso de viaje, ⚠️ **a validar** —lo exige la salida de un menor al extranjero, no la actividad del club en sí—
- **Destinatarios:** ninguno fuera del club
- **Conservación:** ⚠️ **PENDIENTE**. Especialmente la del permiso de viaje una vez pasado el viaje
- **Nota técnica:** el permiso de viaje **solo se admite para menores de edad** el día de salida. El certificado médico no está aquí —tiene su tabla propia por ser dato de salud— ni el derecho de imagen, que es un consentimiento
- **Custodia del papel:** del club, fuera del sistema. ⚠️ **A validar:** si hace falta conservar fotocopia del documento de identidad o basta con comprobarlo

### B.6 Entrenamientos y asistencia

- **Finalidad:** organizar los grupos y llevar el control de asistencia
- **Interesados:** deportistas, mayoritariamente menores
- **Datos:** pertenencia a grupo con fechas de alta y baja, motivo de baja de una lista cerrada, y **estado de asistencia por sesión**: presente, ausente, justificada o tarde
- **Base legal propuesta:** ejecución de la relación asociativa (art. 6.1.b) y/o interés legítimo del club (art. 6.1.f)
- **Conservación:** ⚠️ **PENDIENTE**, y aquí es donde más volumen hay: es un registro **diario** de la presencia de un menor durante toda la temporada
- **Nota técnica:** **no existe campo de observaciones**, deliberadamente, ni se guarda el motivo de una falta justificada. El motivo de baja de un grupo es una lista cerrada **sin valor de lesión**

### B.7 Resultados de competición

- **Finalidad:** registrar las marcas del deportista
- **Datos:** fecha, prueba, tiempo
- **Base legal propuesta:** ejecución de la relación asociativa; el club puede alegar interés legítimo para el histórico deportivo
- **Conservación:** ⚠️ **PENDIENTE**, y **choca de frente con el derecho de supresión**: borrar al deportista destruye el histórico del club, y conservarlo tal cual lo sigue identificando. La salida prevista es la seudonimización

### B.8 Cuentas de acceso y seguridad

- **Finalidad:** dar acceso a la aplicación y protegerla
- **Interesados:** personal del club, tutores y deportistas con cuenta
- **Datos:** usuario, email, contraseña con hash, rol, foto de perfil
- **Base legal propuesta:** ejecución del contrato para la cuenta; interés legítimo (art. 6.1.f) para el futuro registro de eventos de autenticación
- **Conservación:** ⚠️ **PENDIENTE**
- **Nota técnica:** **el registro de eventos de autenticación todavía no existe.** Cuando exista guardará la IP, que es dato personal, y necesitará su propio plazo. La propuesta de partida es 90 días para los accesos correctos y 12 meses para los fallidos

### B.9 Comunicación pública del club

- **Finalidad:** publicar noticias y actividad del club
- **Datos:** los que el club decida incluir, **incluidas imágenes de deportistas**
- **Base legal propuesta:** consentimiento (art. 6.1.a), que es la finalidad `IMAGE` del registro de consentimientos y **es revocable por separado**
- **Destinatarios:** **público general.** Es la única parte del sistema accesible sin autenticación
- **Conservación:** ⚠️ **PENDIENTE**

---

## Parte C — Medidas de seguridad (art. 32)

Descrito como está, no como debería estar.

### Lo que hay

- **Aislamiento entre clubes en tres capas**: filtro de Hibernate por club, **Row Level Security de PostgreSQL** —que es lo que impide que una carga por identificador devuelva la fila de otro club— y el club tomado del token firmado, nunca de un parámetro de la petición. Hay tests que fallan si alguna de las capas se cae
- **Autenticación con JWT** y control de acceso por rol en cada ruta
- **Control de acceso por objeto**: quien no tiene vínculo con el deportista no llega a sus documentos, aunque conozca el identificador
- **Cada entrenador, acotado a los grupos que lleva**: solo ve y registra la asistencia, las fichas, los resultados y los documentos de los deportistas que hoy están en sus grupos. Estar asignado como entrenador exige rol técnico
- **Contraseñas con hash BCrypt**
- **Minimización aplicada en las salidas**: el listado de asistencia y la exportación a CSV llevan el nombre y nada más, ni DNI ni fecha de nacimiento
- **Sin datos personales en los logs**: las personas se identifican por identificador interno
- **Campos de texto libre evitados** en los registros de menores, que es donde acaban apareciendo los datos de salud no buscados

### Lo que no hay todavía

| Medida | Estado |
|---|---|
| Cifrado en reposo de base de datos y archivos | **No** |
| Registro de auditoría de accesos | **No.** Hoy no se puede responder quién abrió la ficha de un menor |
| Registro de eventos de autenticación | **No** |
| Borrado automático por plazos | **No.** No hay ningún plazo definido |
| Exportación y supresión de los datos de una persona | **No** |
| Copias de seguridad | **No** |
| Doble factor para cuentas de administración | **No** |
| Límite de intentos de acceso | **No** |
| TLS y despliegue endurecido | Previsto, sin desplegar |

---

## Parte D — Lo que este registro no puede cerrar sin una decisión

1. **Plazos de conservación de cada actividad.** Es el hueco más grande y el que más código arrastra: sin plazos no hay borrado automático que escribir.
2. **Base legal de la aptitud médica y de los documentos clínicos.**
3. **Si el tratamiento B.5 debe seguir existiendo** o el sistema pasa a guardar solo metadatos.
4. **Si el histórico de asistencia y resultados puede seudonimizarse** en lugar de borrarse al ejercer supresión.
5. **Si hace falta DPD.**
6. **Cuánto tiempo se conserva la IP** del consentimiento otorgado por formulario web.
7. **Qué verificación se exige** para dar por bueno que quien consiente es realmente el tutor legal.
8. **Si algún club es municipal o depende de administración pública**, porque entonces entra el ENS.
