package es.jaes.cn_servicies.medical_certificate;

/**
 * Estado de la cobertura medica de un atleta. <b>Se calcula, no se almacena.</b>
 *
 * <p>Ninguno de estos valores es un juicio clinico: son las tres situaciones en
 * que puede estar una fecha de caducidad respecto a hoy, mas la ausencia de
 * certificado.
 */
public enum MedicalCertificateStatus {

    /** En plazo y con mas de 30 dias por delante. */
    VALID,

    /** En plazo, pero caduca dentro de 30 dias o menos. Es el aviso. */
    EXPIRING_SOON,

    /** Caducado. */
    EXPIRED,

    /**
     * No consta ninguno. Solo aparece al preguntar por un atleta, nunca como
     * estado de un certificado concreto.
     */
    MISSING
}
