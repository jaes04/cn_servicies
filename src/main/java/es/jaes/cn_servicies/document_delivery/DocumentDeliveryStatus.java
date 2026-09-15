package es.jaes.cn_servicies.document_delivery;

/**
 * Estado de un papel entregado. <b>Se calcula, no se almacena</b>, igual que el
 * del certificado medico: guardarlo significaria que un documento caduca sin que
 * nadie se entere hasta que alguien lo actualice a mano.
 */
public enum DocumentDeliveryStatus {

    /** En plazo y con mas de 30 dias por delante. */
    VALID,

    /** En plazo, pero deja de valer dentro de 30 dias o menos. Es el aviso. */
    EXPIRING_SOON,

    /**
     * Ya no vale. En la licencia significa ademas que la ultima que trajo es de una
     * temporada anterior: hay que pedirle que renueve, que no es lo mismo que
     * pedirle una que nunca trajo.
     */
    EXPIRED,

    /** No consta ninguno. */
    MISSING,

    /** No hace falta: el documento de identidad de un atleta cuya ficha no tiene numero. */
    NOT_REQUIRED
}
