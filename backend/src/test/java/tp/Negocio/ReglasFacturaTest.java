package tp.Negocio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tp.Entidad.FacturaVenta;

class ReglasFacturaTest {

    private FacturaVenta factura(Long numero, String estado) {
        FacturaVenta f = new FacturaVenta();
        f.setNumero(numero);
        f.setEstado(estado);
        return f;
    }

    @Test
    void borradorSinNumeroSePuedeEditarEliminarEInformar() {
        FacturaVenta f = factura(null, "BORRADOR");
        assertTrue(ReglasFactura.puedeEditar(f));
        assertTrue(ReglasFactura.puedeEliminar(f));
        assertTrue(ReglasFactura.puedeInformar(f));
        assertFalse(ReglasFactura.puedeAnular(f));
    }

    @Test
    void rechazadaSinNumeroSePuedeReintentar() {
        FacturaVenta f = factura(null, "RECHAZADA");
        assertTrue(ReglasFactura.puedeInformar(f));
        assertTrue(ReglasFactura.puedeEditar(f));
    }

    @Test
    void facturaConNumeroSoloSePuedeAnular() {
        FacturaVenta f = factura(12L, "INFORMADA");
        assertFalse(ReglasFactura.puedeEditar(f));
        assertFalse(ReglasFactura.puedeEliminar(f));
        assertFalse(ReglasFactura.puedeInformar(f));
        assertTrue(ReglasFactura.puedeAnular(f));
    }

    @Test
    void facturaYaAnuladaNoAdmiteNingunaAccion() {
        FacturaVenta f = factura(12L, "ANULADA");
        assertFalse(ReglasFactura.puedeAnular(f));
        assertFalse(ReglasFactura.puedeEditar(f));
        assertFalse(ReglasFactura.puedeInformar(f));
    }

    @Test
    void facturaEliminadaNoAdmiteNingunaAccion() {
        FacturaVenta f = factura(null, "ELIMINADA");
        assertFalse(ReglasFactura.puedeEditar(f));
        assertFalse(ReglasFactura.puedeEliminar(f));
        assertFalse(ReglasFactura.puedeInformar(f));
        assertFalse(ReglasFactura.puedeAnular(f));
    }
}
