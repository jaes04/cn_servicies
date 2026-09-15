package es.jaes.cn_servicies.document_delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los extremos de las dos reglas de fechas del bloque 3a, sin base de datos.
 *
 * <p>Van aparte porque un error de un dia aqui no se ve en ningun test de
 * endpoint —las fechas de esos tests caen lejos de los bordes— y es el que hace
 * que un nadador aparezca sin documentacion el dia en que todavia la tenia.
 */
class DocumentDeliveryRulesTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 15);

    @Test
    @DisplayName("el día en que caduca todavía vale")
    void elUltimoDiaVale() {
        assertThat(DocumentDeliveryService.statusOf(HOY, HOY))
                .isNotEqualTo(DocumentDeliveryStatus.EXPIRED);
    }

    @Test
    @DisplayName("el día siguiente ya no")
    void elDiaSiguienteNo() {
        assertThat(DocumentDeliveryService.statusOf(HOY.minusDays(1), HOY))
                .isEqualTo(DocumentDeliveryStatus.EXPIRED);
    }

    @Test
    @DisplayName("con 29 días por delante avisa; con 30, no")
    void elMargenDelAviso() {
        assertThat(DocumentDeliveryService.statusOf(HOY.plusDays(29), HOY))
                .isEqualTo(DocumentDeliveryStatus.EXPIRING_SOON);
        assertThat(DocumentDeliveryService.statusOf(HOY.plusDays(30), HOY))
                .isEqualTo(DocumentDeliveryStatus.VALID);
    }

    @Test
    @DisplayName("la víspera de cumplir 18 todavía es menor; el día que los cumple, ya no")
    void laMayoriaDeEdad() {
        LocalDate cumple18Manana = HOY.minusYears(18).plusDays(1);
        LocalDate cumple18Hoy = HOY.minusYears(18);

        assertThat(DocumentDeliveryService.esMenorEn(cumple18Manana, HOY)).isTrue();
        assertThat(DocumentDeliveryService.esMenorEn(cumple18Hoy, HOY)).isFalse();
    }
}
