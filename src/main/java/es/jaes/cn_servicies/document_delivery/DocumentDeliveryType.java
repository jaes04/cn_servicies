package es.jaes.cn_servicies.document_delivery;

/**
 * Papeles que la familia entrega al club y de los que el sistema solo apunta
 * que se entregaron y hasta cuando valen.
 *
 * <p>Cada tipo vale de una forma distinta, y esa es la razon de que sean tipos y
 * no una lista libre:
 * <ul>
 *   <li>{@link #LICENSE_APPLICATION}: por temporada.</li>
 *   <li>{@link #IDENTITY_DOCUMENT}: hasta la caducidad del documento.</li>
 *   <li>{@link #TRAVEL_PERMIT}: durante las fechas de un viaje.</li>
 * </ul>
 *
 * <p><b>Faltan dos a proposito.</b> El certificado medico no esta: es dato de
 * salud y vive en {@code MedicalCertificate}, con sus propios permisos y su
 * propia policy; un tipo medico aqui seria una puerta lateral para meterlo donde
 * no tiene esa proteccion. Y el derecho de imagen tampoco: es un consentimiento
 * {@code IMAGE} con evidencia en papel, y registrarlo tambien como documento
 * dejaria dos registros de la misma firma que acabarian diciendo cosas
 * distintas.
 *
 * <p>Añadir un valor es una linea, pero es cambio de contrato.
 */
public enum DocumentDeliveryType {

    /** Solicitud de licencia federativa. Una por temporada. */
    LICENSE_APPLICATION,

    /** DNI, NIE o pasaporte. Vale hasta que caduca. */
    IDENTITY_DOCUMENT,

    /** Permiso de viaje de un menor para una competicion en el extranjero. */
    TRAVEL_PERMIT
}
