package tp.Negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.Test;
import tp.Entidad.*;

class FacturaPdfGeneratorTest {

    private static Date fecha(int anio, int mes, int dia) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(anio, mes - 1, dia);
        return c.getTime();
    }

    private FacturaVenta factura(Long numero, String estado, int tipo, String cae, Date vtoCae) {
        PuntoVenta pv = new PuntoVenta();
        pv.setNumero(1);
        pv.setDescripcion("Local Mendoza Centro");
        CondicionIva ci = new CondicionIva();
        ci.setDenominacion("Responsable Inscripto");
        Domicilio dom = new Domicilio();
        dom.setNombreCalle("San Martin");
        dom.setNumeroCalle("123");
        Cliente cli = new Cliente();
        cli.setDenominacion("Sofia Romero");
        cli.setCuitCuil("23890123457");
        cli.setDomicilio(dom);
        Articulo art = new Articulo();
        art.setCodigo("A006");
        art.setDenominacion("Alfajor Arcor");
        ListaPrecioArticulo lpa = new ListaPrecioArticulo();
        lpa.setArticulo(art);
        FacturaVentaDetalle d = new FacturaVentaDetalle();
        d.setListaPrecioArticulo(lpa);
        d.setDescripcion("Alfajor triple");
        d.setCantidad(2);
        d.setPrecioUnitario(800);
        d.setImporteNeto(1322.31);
        d.setImporteIva(277.69);
        d.setImporteSubtotal(1600);

        FacturaVenta f = new FacturaVenta();
        f.setNumero(numero);
        f.setEstado(estado);
        f.setTipoComprobante(tipo);
        f.setFechaEmision(fecha(2026, 11, 18));
        f.setPuntoVenta(pv);
        f.setCondicionIva(ci);
        f.setCliente(cli);
        f.setCae(cae);
        f.setCaeFechaVencimiento(vtoCae);
        f.setImporteTotal(1600);
        f.setObservaciones("Entregar por la tarde");
        f.setDetalles(new ArrayList<>());
        f.getDetalles().add(d);
        return f;
    }

    private String texto(byte[] pdf) throws Exception {
        PdfReader r = new PdfReader(pdf);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= r.getNumberOfPages(); i++) {
            sb.append(PdfTextExtractor.getTextFromPage(r, i)).append('\n');
        }
        r.close();
        return sb.toString();
    }

    @Test
    void generaUnPdfValido() {
        byte[] pdf = FacturaPdfGenerator.generar(
                factura(2L, "INFORMADA", 1, "74123456789012", fecha(2026, 11, 28)), "20473735106");
        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void incluyeCaeYSuVencimiento() throws Exception {
        String t = texto(FacturaPdfGenerator.generar(
                factura(2L, "INFORMADA", 1, "74123456789012", fecha(2026, 11, 28)), "20473735106"));
        assertTrue(t.contains("74123456789012"), t);
        assertTrue(t.contains("28/11/2026"), t);
        assertTrue(t.contains("CAE"), t);
    }

    @Test
    void muestraNumeroConPuntoDeVentaLetraYDatosDelCliente() throws Exception {
        String t = texto(FacturaPdfGenerator.generar(
                factura(2L, "INFORMADA", 1, "74123456789012", fecha(2026, 11, 28)), "20473735106"));
        assertTrue(t.contains("0001-00000002"), t);
        assertTrue(t.contains("FACTURA"), t);
        assertTrue(t.contains("Sofia Romero"), t);
        assertTrue(t.contains("23-89012345-7"), t); // CUIT con guiones solo visuales
        assertTrue(t.contains("20-47373510-6"), t); // CUIT emisor
        assertTrue(t.contains("Alfajor Arcor"), t);
        assertTrue(t.contains("18/11/2026"), t);
    }

    @Test
    void facturaADiscriminaIvaPeroLaCNo() throws Exception {
        String a = texto(FacturaPdfGenerator.generar(
                factura(2L, "INFORMADA", 1, "74123456789012", fecha(2026, 11, 28)), "20473735106"));
        assertTrue(a.contains("1.322,31"), a);
        assertTrue(a.contains("277,69"), a);

        String c = texto(FacturaPdfGenerator.generar(
                factura(2L, "INFORMADA", 11, "74123456789012", fecha(2026, 11, 28)), "20473735106"));
        assertFalse(c.contains("277,69"), c);
        assertTrue(c.contains("1.600,00"), c);
    }

    @Test
    void borradorSinCaeIndicaQueNoTieneValidezFiscal() throws Exception {
        String t = texto(FacturaPdfGenerator.generar(factura(null, "BORRADOR", 11, null, null), "20473735106"));
        assertTrue(t.contains("BORRADOR"), t);
        assertTrue(t.contains("sin CAE"), t);
        assertFalse(t.contains("0001-"), t);
    }

    @Test
    void sinClienteIndicaConsumidorFinal() throws Exception {
        FacturaVenta f = factura(2L, "INFORMADA", 11, "74123456789012", fecha(2026, 11, 28));
        f.setCliente(null);
        assertTrue(texto(FacturaPdfGenerator.generar(f, "20473735106")).contains("Consumidor Final"));
    }
}
