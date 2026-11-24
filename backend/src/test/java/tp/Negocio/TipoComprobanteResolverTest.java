package tp.Negocio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TipoComprobanteResolverTest {

    @Test
    void emisorMonotributistaSiempreEmiteFacturaC() {
        assertEquals(11, TipoComprobanteResolver.resolver(false, 1));
        assertEquals(11, TipoComprobanteResolver.resolver(false, 5));
    }

    @Test
    void emisorResponsableInscriptoAReceptorInscriptoEmiteA() {
        assertEquals(1, TipoComprobanteResolver.resolver(true, 1));
    }

    @Test
    void emisorResponsableInscriptoAMonotributistaEmiteA() {
        assertEquals(1, TipoComprobanteResolver.resolver(true, 6));
    }

    @Test
    void emisorResponsableInscriptoAConsumidorFinalEmiteB() {
        assertEquals(6, TipoComprobanteResolver.resolver(true, 5));
    }

    @Test
    void soloLaFacturaCNoDiscriminaIvaEnArca() {
        assertTrue(TipoComprobanteResolver.discriminaIva(1));
        assertTrue(TipoComprobanteResolver.discriminaIva(6));
        assertFalse(TipoComprobanteResolver.discriminaIva(11));
    }

    @Test
    void letraDelComprobante() {
        assertEquals("A", TipoComprobanteResolver.letra(1));
        assertEquals("B", TipoComprobanteResolver.letra(6));
        assertEquals("C", TipoComprobanteResolver.letra(11));
    }
}
