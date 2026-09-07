package es.jaes.cn_servicies.guardian;

/**
 * Finalidades para las que se pide consentimiento, separadas a proposito.
 *
 * <p>Una sola casilla que lo cubra todo no es consentimiento valido: el RGPD
 * exige que sea especifico por finalidad y que se pueda retirar una sin
 * arrastrar las demas. El caso claro es {@link #IMAGE}: un tutor puede aceptar
 * que su hijo entrene y nade en competicion, y negarse a que aparezca en las
 * fotos del blog. Tiene que poder revocarse solo.
 *
 * <p>Cerrado y corto. Anadir una finalidad nueva es una decision, no un detalle
 * de implementacion: cada valor de aqui es una base legal distinta.
 */
public enum ConsentType {

    /** Tratamiento de los datos personales del atleta para la gestion deportiva. */
    DATA_PROCESSING,

    /** Captacion y publicacion de imagen. Se revoca por separado, siempre. */
    IMAGE,

    /** Comunicaciones del club al tutor. */
    COMMUNICATIONS,

    /**
     * Tratamiento del dato de salud, que en este sistema es solo la vigencia
     * del certificado medico federativo. Categoria especial del art. 9 RGPD.
     */
    HEALTH_DATA
}
