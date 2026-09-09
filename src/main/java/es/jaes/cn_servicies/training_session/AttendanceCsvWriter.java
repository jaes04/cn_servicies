package es.jaes.cn_servicies.training_session;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte un informe de grupo en un CSV que se pueda abrir de verdad.
 *
 * <p><b>Separador punto y coma, no coma.</b> Excel con configuracion regional
 * española espera {@code ;}: con comas abre el archivo entero en una sola
 * columna, y quien lo recibe concluye que el sistema exporta mal.
 *
 * <p><b>Con BOM al principio.</b> Sin el, Excel lee el UTF-8 como si fuera la
 * codificacion de Windows y "Alevin A" aparece como "AlevÃ­n A". El BOM es
 * exactamente lo que le dice que el archivo es UTF-8.
 *
 * <p><b>Se neutralizan las formulas.</b> Un campo que empiece por {@code =},
 * {@code +}, {@code -} o {@code @} lo interpreta la hoja de calculo como una
 * formula, no como texto: es la inyeccion CSV de toda la vida. Aqui el riesgo es
 * pequeño —los nombres los teclea el club— pero el arreglo cuesta una linea y no
 * depende de que nadie se acuerde.
 */
@Component
public class AttendanceCsvWriter {

    /** Excel espera esto en España. */
    private static final char SEPARADOR = ';';

    /** Lo que hace que Excel no destroce las tildes. */
    private static final String BOM = "﻿";

    private static final List<String> CABECERAS = List.of(
            "Atleta", "Sesiones", "Presente", "Tarde", "Ausente",
            "Justificada", "Sin registrar", "% Asistencia");

    public String write(GroupAttendanceResponse informe) {
        StringBuilder csv = new StringBuilder(BOM);

        escribirFila(csv, CABECERAS);
        for (AthleteAttendanceResponse atleta : informe.getAthletes()) {
            escribirFila(csv, List.of(
                    atleta.getAthleteName(),
                    String.valueOf(atleta.getSessions()),
                    String.valueOf(atleta.getPresent()),
                    String.valueOf(atleta.getLate()),
                    String.valueOf(atleta.getAbsent()),
                    String.valueOf(atleta.getExcused()),
                    String.valueOf(atleta.getUnrecorded()),
                    porcentaje(atleta.getAttendanceRate())));
        }
        return csv.toString();
    }

    /**
     * Con coma decimal, que es lo que Excel en español entiende como numero. Con
     * punto lo lee como texto y no deja ni ordenar la columna.
     */
    private String porcentaje(Double tasa) {
        if (tasa == null) {
            return "";
        }
        return String.valueOf(Math.round(tasa * 1000) / 10.0).replace('.', ',');
    }

    private void escribirFila(StringBuilder csv, List<String> campos) {
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                csv.append(SEPARADOR);
            }
            csv.append(escapar(campos.get(i)));
        }
        csv.append("\r\n");
    }

    /**
     * Entrecomilla si hace falta y duplica las comillas de dentro, que es como se
     * escapa en CSV.
     */
    private String escapar(String valor) {
        String texto = valor == null ? "" : neutralizarFormula(valor);
        boolean necesitaComillas = texto.indexOf(SEPARADOR) >= 0
                || texto.indexOf('"') >= 0
                || texto.indexOf('\n') >= 0
                || texto.indexOf('\r') >= 0;

        if (!necesitaComillas) {
            return texto;
        }
        return '"' + texto.replace("\"", "\"\"") + '"';
    }

    /** Un apostrofo delante y la hoja de calculo lo trata como texto. */
    private String neutralizarFormula(String valor) {
        if (valor.isEmpty()) {
            return valor;
        }
        char primero = valor.charAt(0);
        if (primero == '=' || primero == '+' || primero == '-' || primero == '@') {
            return "'" + valor;
        }
        return valor;
    }
}
