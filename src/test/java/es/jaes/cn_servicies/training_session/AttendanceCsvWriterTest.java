package es.jaes.cn_servicies.training_session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 2.4.b: el CSV.
 *
 * <p>Test sin Spring: aqui no hay base ni contexto, solo texto. Lo que se
 * comprueba es lo que hace que el archivo se abra bien en el ordenador de un
 * club español —separador, BOM, coma decimal— y que un nombre raro no rompa el
 * formato ni se convierta en una formula.
 */
class AttendanceCsvWriterTest {

    private final AttendanceCsvWriter writer = new AttendanceCsvWriter();

    @Test
    @DisplayName("cabecera y una fila por atleta, separadas por punto y coma")
    void formatoBasico() {
        String csv = writer.write(informeCon(atleta("Ana Nadadora", 4, 3, 0, 1, 0, 0, 0.75)));

        String[] lineas = csv.split("\r\n");
        assertThat(lineas[0]).endsWith("Atleta;Sesiones;Presente;Tarde;Ausente;Justificada;Sin registrar;% Asistencia");
        assertThat(lineas[1]).isEqualTo("Ana Nadadora;4;3;0;1;0;0;75,0");
    }

    /** Sin esto, Excel lee el UTF-8 como Windows-1252 y "Alevín" sale roto. */
    @Test
    @DisplayName("empieza con el BOM de UTF-8")
    void llevaBom() {
        String csv = writer.write(informeCon(atleta("Ana", 1, 1, 0, 0, 0, 0, 1.0)));

        assertThat(csv).startsWith("﻿");
    }

    /** Con punto, Excel en español lo lee como texto y no deja ni ordenar. */
    @Test
    @DisplayName("el porcentaje va con coma decimal")
    void comaDecimal() {
        String csv = writer.write(informeCon(atleta("Ana", 3, 1, 0, 2, 0, 0, 0.3333)));

        assertThat(csv).contains(";33,3");
    }

    @Test
    @DisplayName("un atleta sin sesiones deja el porcentaje vacío, no un cero que engaña")
    void sinSesiones() {
        String csv = writer.write(informeCon(atleta("Ana", 0, 0, 0, 0, 0, 0, null)));

        assertThat(csv.split("\r\n")[1]).isEqualTo("Ana;0;0;0;0;0;0;");
    }

    @Test
    @DisplayName("un nombre con punto y coma se entrecomilla en vez de partir la fila")
    void nombreConSeparador() {
        String csv = writer.write(informeCon(atleta("Pérez; Ana", 1, 1, 0, 0, 0, 0, 1.0)));

        assertThat(csv.split("\r\n")[1]).startsWith("\"Pérez; Ana\";");
    }

    @Test
    @DisplayName("las comillas de dentro se duplican")
    void nombreConComillas() {
        String csv = writer.write(informeCon(atleta("Ana \"La Rana\"", 1, 1, 0, 0, 0, 0, 1.0)));

        assertThat(csv.split("\r\n")[1]).startsWith("\"Ana \"\"La Rana\"\"\";");
    }

    /**
     * Inyección CSV: un campo que empieza por {@code =} lo ejecuta la hoja de
     * cálculo. Aquí el riesgo es pequeño —los nombres los teclea el club— pero
     * el arreglo cuesta una línea.
     */
    @Test
    @DisplayName("un nombre que empieza por = no se convierte en fórmula")
    void nombreQueParecereFormula() {
        String csv = writer.write(informeCon(atleta("=1+1", 1, 1, 0, 0, 0, 0, 1.0)));

        assertThat(csv.split("\r\n")[1]).startsWith("'=1+1;");
    }

    @Test
    @DisplayName("un informe sin atletas deja solo la cabecera")
    void informeVacio() {
        GroupAttendanceResponse informe = new GroupAttendanceResponse();
        informe.setGroupName("Alevín A");
        informe.setAthletes(List.of());

        assertThat(writer.write(informe).split("\r\n")).hasSize(1);
    }

    // ----------------------------------------------------------------

    private GroupAttendanceResponse informeCon(AthleteAttendanceResponse... atletas) {
        GroupAttendanceResponse informe = new GroupAttendanceResponse();
        informe.setGroupName("Alevín A");
        informe.setAthletes(List.of(atletas));
        return informe;
    }

    private AthleteAttendanceResponse atleta(String nombre, int sesiones, int present, int late,
                                             int absent, int excused, int sinRegistrar,
                                             Double tasa) {
        AthleteAttendanceResponse atleta = new AthleteAttendanceResponse();
        atleta.setAthleteId(UUID.randomUUID());
        atleta.setAthleteName(nombre);
        atleta.setSessions(sesiones);
        atleta.setPresent(present);
        atleta.setLate(late);
        atleta.setAbsent(absent);
        atleta.setExcused(excused);
        atleta.setUnrecorded(sinRegistrar);
        atleta.setAttendanceRate(tasa);
        return atleta;
    }
}
