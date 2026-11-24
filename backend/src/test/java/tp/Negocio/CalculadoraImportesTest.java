package tp.Negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CalculadoraImportesTest {

    @Test
    void elPrecioYaIncluyeIvaYSeDiscriminaElNeto() {
        var r = CalculadoraImportes.calcular(2, 121.0, 0, true);
        assertEquals(242.0, r.subtotal(), 0.001);
        assertEquals(200.0, r.neto(), 0.001);
        assertEquals(42.0, r.iva(), 0.001);
    }

    @Test
    void laBonificacionSeAplicaSobreElPrecioConIva() {
        var r = CalculadoraImportes.calcular(2, 121.0, 10, true);
        assertEquals(217.8, r.subtotal(), 0.001);
        assertEquals(180.0, r.neto(), 0.001);
        assertEquals(37.8, r.iva(), 0.001);
    }

    @Test
    void netoMasIvaSiempreSumaElSubtotalRedondeado() {
        var r = CalculadoraImportes.calcular(3, 10.0, 0, true);
        assertEquals(30.0, r.subtotal(), 0.0001);
        assertEquals(r.subtotal(), r.neto() + r.iva(), 0.0001);
    }

    @Test
    void sinDiscriminarIvaTodoVaComoNetoYElIvaEsCero() {
        var r = CalculadoraImportes.calcular(2, 121.0, 0, false);
        assertEquals(242.0, r.subtotal(), 0.001);
        assertEquals(242.0, r.neto(), 0.001);
        assertEquals(0.0, r.iva(), 0.001);
    }
}
