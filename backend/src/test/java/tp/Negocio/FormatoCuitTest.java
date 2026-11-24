package tp.Negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FormatoCuitTest {

    @Test
    void muestraElCuitConGuionesAunqueEsteGuardadoSinEllos() {
        assertEquals("20-12345678-6", FormatoCuit.visual("20123456786"));
    }

    @Test
    void siYaTieneGuionesQuedaIgual() {
        assertEquals("20-12345678-6", FormatoCuit.visual("20-12345678-6"));
    }

    @Test
    void unValorQueNoEsUnCuitDeOnceDigitosSeDevuelveSinCambios() {
        assertEquals("1234", FormatoCuit.visual("1234"));
        assertEquals("", FormatoCuit.visual(null));
    }

    @Test
    void soloDigitosQuitaGuionesYEspacios() {
        assertEquals("20123456786", FormatoCuit.soloDigitos(" 20-12345678-6 "));
        assertEquals("", FormatoCuit.soloDigitos(null));
    }
}
