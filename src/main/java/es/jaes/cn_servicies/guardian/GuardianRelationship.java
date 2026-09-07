package es.jaes.cn_servicies.guardian;

/**
 * Parentesco entre tutor y atleta.
 *
 * <p>Vive en el vinculo, no en el tutor: la misma persona es madre de un atleta
 * y puede ser tutora legal de otro.
 *
 * <p>Deliberadamente corto y cerrado. No hay valor libre: un campo de texto en
 * una entidad de persona acaba recogiendo lo que no debe.
 */
public enum GuardianRelationship {
    MOTHER,
    FATHER,
    LEGAL_GUARDIAN,
    OTHER
}
