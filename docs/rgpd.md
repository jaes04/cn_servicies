# Protección de datos — reglas técnicas

Este proyecto trata datos personales de **menores de edad** y almacena **archivos que
pueden contener datos de salud**. Eso convierte varias decisiones que parecerían de diseño
en restricciones duras.

**Lee este documento antes de tocar cualquier cosa que almacene, muestre, exporte o
registre datos de personas.**

> Guía técnica para el desarrollo, no asesoramiento jurídico. Las decisiones de
> cumplimiento las valida Jorge con un profesional. Tu trabajo es no desviarte de las que
> ya están tomadas y avisar cuando una tarea parezca cruzar una línea.

Marco aplicable: RGPD (UE 2016/679), LOPDGDD (LO 3/2018) y requisitos federativos de la
FMN.

---

## 1. `AthleteDocument` — el punto más delicado del sistema

La entidad almacena archivos reales asociados a atletas, y `AthleteDocumentType` incluye
`MEDICAL`. En la práctica eso significa que el sistema puede estar guardando **datos de
salud de menores** (RGPD art. 9, categoría especial), que es el nivel de protección más
alto que existe.

### Estado

La intención original del proyecto era guardar **solo metadatos** del certificado
federativo — existencia, fecha de emisión, caducidad — y ningún contenido clínico. El
modelo actual va más allá de eso.

**Esto es una decisión abierta, no un hecho consumado.** Pregunta antes de construir
encima.

### Mientras no se resuelva

- **No amplíes la funcionalidad de documentos médicos.** Nada de previsualización,
  compartición, adjuntar a emails, incluir en exportaciones o en informes.
- **No añadas campos de texto libre** a `AthleteDocument`. `title` ya es uno: es una vía
  directa a que alguien escriba un diagnóstico donde no debe. Si aparece validación de
  ese campo en alguna tarea, plantéalo.
- **No mezcles documentos médicos con los demás** en listados, endpoints genéricos o
  respuestas de la API sin filtro por tipo.
- **Ningún archivo se sirve sin control de acceso.** Nunca una URL pública o adivinable.
  El nombre interno debe ser opaco.
- El acceso a un documento `MEDICAL` debería quedar registrado: quién, cuándo, cuál.

### Qué exige realmente la federación

Para la ficha federativa basta con saber que existe un certificado en vigor y hasta
cuándo. No hace falta el diagnóstico, ni el dictamen clínico, ni las observaciones del
médico. Cualquier diseño que capture menos, es mejor diseño.

Nunca almacenes en campos estructurados: diagnósticos, patologías, antecedentes,
alergias, medicación, tratamientos, ni el veredicto médico más allá de apto/no apto.

---

## 2. Menores y tutores

- **Edad de consentimiento para el tratamiento de datos en España: 14 años**
  (LOPDGDD art. 7). Por debajo, consentimiento de quien ejerce la patria potestad o
  tutela.
- `UserAthlete` con `type = TUTOR` es la entidad que sostiene esa relación. **No es solo
  funcional: es la base legal del tratamiento de los datos del menor.** Trátala como
  crítica.
- `AthleteInviteKey` es el mecanismo por el que se establece ese vínculo. Un fallo aquí
  —clave adivinable, sin caducar, reutilizable— da acceso a los datos de un menor a quien
  no debe. Verifica siempre `expiresAt` y `used`. `keyValue` no aparece en logs, listados,
  ni URLs registradas.
- **Por defecto, ningún dato de un menor sale del ámbito interno del club.** Cuidado
  especial con `Post`, `Comment` y cualquier funcionalidad pública.
- No propongas rankings públicos, galerías de fotos ni perfiles compartibles sin plantear
  antes el problema de datos.

### Comentarios y contenido público

`Comment` tiene `blocked`, lo que implica moderación. Si los autores pueden ser menores,
o si se comentan publicaciones que los mencionan, hay que pensar quién ve qué antes de
ampliar la funcionalidad.

---

## 3. Minimización

Antes de añadir un campo a una entidad de persona: **¿qué funcionalidad concreta se rompe
si no está?** Si la respuesta es "ninguna, pero puede ser útil", no se añade.

Aplica igual a los DTOs de respuesta. En `Athlete`:

- `dni` es un identificador oficial. No va en listados, ni en respuestas que no lo
  necesiten, ni en logs, ni en exportaciones genéricas.
- `birthDate` se usa para categoría deportiva y para saber si es menor. Cuando basta con
  la edad o la categoría, devuelve eso, no la fecha completa.
- `profilePhoto` de un menor es dato personal con la misma protección que el resto.

---

## 4. Logs y direcciones IP

**La IP es dato personal** (criterio del TJUE en *Breyer*). El registro de intentos de
autenticación con IP es legítimo por seguridad, pero:

- Finalidad acotada: seguridad, no analítica ni marketing.
- Retención limitada. No se guarda indefinidamente.
- No se cruza con otros datos para perfilar comportamiento.

### En los logs, nunca

- Nombres, apellidos, `dni`, fechas de nacimiento, emails, teléfonos
- `keyValue` de `AthleteInviteKey`
- Contenido de tokens JWT, `passwordHash`
- `originalFileName` de documentos — el nombre que pone el usuario suele ser descriptivo
  (`informe-cardiologia-maria.pdf`)
- Cualquier metadato de un documento `MEDICAL`
- Cuerpos completos de peticiones o respuestas

Identifica personas por UUID. `Athlete id=3f2a… sin certificado vigente`, no
`María López sin certificado`.

---

## 5. Derechos de las personas

El sistema tiene que poder, sin acceso manual a la base de datos:

- **Acceso**: exportar todos los datos de una persona
- **Rectificación**: corregirlos
- **Supresión**: borrarlos
- **Portabilidad**: entregarlos en formato estructurado

### El problema del borrado en este modelo

`Athlete` tiene `deleteAt`, pero **borrado lógico no es supresión**. Un registro marcado
como borrado sigue conteniendo `dni`, nombre y fecha de nacimiento. Para ejercer el
derecho de supresión hace falta anonimización real e irreversible.

Complicaciones concretas:

- `CompetitionResult` tiene valor histórico para el club. Borrar el atleta no debería
  destruirlo, pero tampoco puede seguir identificándolo.
- `AthleteDocument` **no tiene borrado lógico**: el borrado es físico. Hay que asegurar
  que el archivo en disco se elimina también, no solo la fila.
- `Comment` y `Post` de un usuario borrado necesitan una estrategia propia.

**Cuando implementes borrado, plantea la estrategia antes de codificarla.** No lo
resuelvas sobre la marcha.

---

## 6. Seguridad técnica

- Contraseñas con hash fuerte y salt. `passwordHash` nunca sale de la capa de servicio.
- Todo en HTTPS. El túnel de Cloudflare termina TLS; nada en claro.
- Control de acceso por rol **y** por vínculo: un `ROLE_USER` solo accede a los atletas
  con los que tiene `UserAthlete`. `ROLE_TECHNICAL_STAFF` a los de su ámbito. El rol por
  sí solo no basta.
- Cuando exista multi-tenancy, se suma el filtro por club. Hoy no existe: tenlo presente
  al evaluar riesgos.
- Postgres no accesible desde fuera de la red de Docker.
- Los archivos subidos necesitan copia de seguridad y, si contienen documentos médicos,
  cifrado en reposo. Plantéalo cuando se aborde el despliegue.
- Sin datos reales de producción en desarrollo o pruebas.
- **Cualquier secreto que aparezca en una conversación, un ticket o un commit se considera
  comprometido y hay que rotarlo.**

---

## 7. El club es responsable, la plataforma es encargada

En modelo SaaS cada club es responsable del tratamiento y la plataforma actúa como
encargada. Implica contrato de encargo (RGPD art. 28) con cada club y transparencia sobre
los subencargados (Hetzner, Cloudflare).

Consecuencia técnica: **el club tiene que poder recuperar sus datos y llevárselos.** La
exportación completa por club no es una funcionalidad de "más adelante", es una obligación
contractual. Tenlo en cuenta al diseñar, aunque no se implemente aún.

---

## 8. Problema abierto: `uploads/` en control de versiones

La carpeta `uploads/` está commiteada en el repositorio, con archivos de usuarios dentro.

`DocumentStorageService` e `ImageStorageService` escriben ambos archivos subidos. Si
comparten destino, es posible que haya **documentos de atletas en el historial de git**.

Qué hacer:

1. Comprobar qué hay realmente en `uploads/` y de qué servicio proviene cada archivo.
2. Sacar la carpeta del seguimiento y añadirla a `.gitignore`.
3. Si hay documentos de atletas: quitarlos del historial, no solo del HEAD. Un repositorio
   clonado por otra persona conserva todo lo anterior.

Hasta que esté resuelto, no añadas archivos nuevos a esa carpeta ni la incluyas en
imágenes de Docker.

---

## 9. Señales de alarma

Si una tarea implica cualquiera de estas cosas, **para y pregunta antes de implementar**:

- Tocar `AthleteDocument`, especialmente el tipo `MEDICAL`
- Añadir un campo de texto libre a una entidad de persona
- Exponer `dni`, `birthDate` o `profilePhoto` en una respuesta nueva
- Exportar o enviar datos de atletas fuera del sistema
- Hacer accesible cualquier dato sin autenticación
- Cualquier integración externa que reciba datos personales
- Analítica, tracking o telemetría
- Envío de emails o notificaciones a menores
- Ampliar retención de algo, o eliminar un borrado programado
- Cualquier funcionalidad que publique información de un atleta