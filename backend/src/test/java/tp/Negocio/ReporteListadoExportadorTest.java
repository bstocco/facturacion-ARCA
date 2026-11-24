package tp.Negocio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import tp.DTO.FacturaReporteDTO;

class ReporteListadoExportadorTest {

    private FacturaReporteDTO factura(String cliente, double total, String estado) {
        return new FacturaReporteDTO(1L, 15L, new Date(0), cliente, "Consumidor Final", "Casa central",
                total, 1, estado, 6, null);
    }

    @Test
    void csvTieneEncabezadoYUnaFilaPorFactura() {
        String csv = new String(ReporteListadoExportador.csv(List.of(factura("Ana", 1234.5, "INFORMADA"))),
                StandardCharsets.UTF_8);
        String[] lineas = csv.substring(1).split("\r\n"); // salta el BOM
        assertTrue(csv.startsWith("﻿Numero;Tipo;Fecha"));
        assertEquals(2, lineas.length);
        assertTrue(lineas[1].contains(";B;"));
        assertTrue(lineas[1].contains(";1.234,50;"));
    }

    @Test
    void csvEscapaSeparadorYComillas() {
        assertEquals("\"Perez; \"\"El Tano\"\"\"", ReporteListadoExportador.csvCampo("Perez; \"El Tano\""));
        assertEquals("Ana", ReporteListadoExportador.csvCampo("Ana"));
    }

    @Test
    void elTotalNoSumaLasAnuladas() {
        assertEquals(100.0, ReporteListadoExportador.totalSinAnuladas(
                List.of(factura("A", 100, "INFORMADA"), factura("B", 50, "ANULADA"))), 0.001);
    }

    @Test
    void pdfDelListadoEsUnPdf() {
        byte[] pdf = ReporteListadoExportador.pdf(List.of(factura("Ana", 10, "BORRADOR")), "ninguno");
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
