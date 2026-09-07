package es.jaes.cn_servicies.guardian;

/**
 * Como se recogio el consentimiento.
 *
 * <p>Existe porque la carga de la prueba es del responsable del tratamiento
 * (RGPD art. 7.1): el club tiene que poder demostrar que lo obtuvo, y "consta
 * que si" no es demostrarlo. Un enum cerrado, en vez de un campo de texto,
 * evita ademas que alguien escriba en la evidencia lo que no debe.
 */
public enum ConsentEvidenceType {

    /** Formulario en papel firmado y archivado por el club. */
    PAPER_FORM,

    /** Formulario del sistema. Es el unico caso en que la IP de origen tiene sentido. */
    ONLINE_FORM,

    /** Correo electronico del tutor conservado por el club. */
    EMAIL
}
