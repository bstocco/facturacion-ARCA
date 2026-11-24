package tp.Negocio;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import tp.Entidad.*;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Arma el PDF de una factura de venta con CAE y vencimiento del CAE. */
public final class FacturaPdfGenerator {

    private static final Font TITULO = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
    private static final Font NEGRITA = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
    private static final Font NORMAL = new Font(Font.FontFamily.HELVETICA, 10);
    private static final Font CHICA = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.DARK_GRAY);
    private static final Font LETRA = new Font(Font.FontFamily.HELVETICA, 30, Font.BOLD);
    private static final BaseColor GRIS = new BaseColor(230, 230, 230);

    private FacturaPdfGenerator() {
    }

    public static byte[] generar(FacturaVenta f, String cuitEmisor) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            int tipo = f.getTipoComprobante() != null ? f.getTipoComprobante() : TipoComprobanteResolver.FACTURA_C;
            boolean informada = f.getNumero() != null;
            boolean discrimina = tipo == TipoComprobanteResolver.FACTURA_A;

            doc.add(encabezado(f, cuitEmisor, tipo, informada));
            doc.add(espacio());
            doc.add(cliente(f));
            doc.add(espacio());
            doc.add(renglones(f, discrimina));
            doc.add(espacio());
            doc.add(totales(f, discrimina));
            if (f.getObservaciones() != null && !f.getObservaciones().isBlank()) {
                doc.add(espacio());
                doc.add(new Paragraph("Observaciones: " + f.getObservaciones(), NORMAL));
            }
            doc.add(espacio());
            doc.add(pie(f, informada));

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------- secciones

    private static PdfPTable encabezado(FacturaVenta f, String cuitEmisor, int tipo, boolean informada)
            throws DocumentException {
        PdfPTable t = new PdfPTable(new float[] { 4f, 1.4f, 4f });
        t.setWidthPercentage(100);

        PdfPCell izq = celda();
        izq.addElement(new Paragraph("Emisor", NEGRITA));
        izq.addElement(new Paragraph("CUIT: " + FormatoCuit.visual(cuitEmisor), NORMAL));
        if (f.getPuntoVenta() != null) {
            izq.addElement(new Paragraph("Punto de venta: " + f.getPuntoVenta().getNumero() + " - "
                    + f.getPuntoVenta().getDescripcion(), NORMAL));
        }

        PdfPCell centro = celda();
        Paragraph letra = new Paragraph(TipoComprobanteResolver.letra(tipo), LETRA);
        letra.setAlignment(Element.ALIGN_CENTER);
        centro.addElement(letra);
        Paragraph cod = new Paragraph("COD. " + String.format("%02d", tipo), CHICA);
        cod.setAlignment(Element.ALIGN_CENTER);
        centro.addElement(cod);

        PdfPCell der = celda();
        der.addElement(new Paragraph("FACTURA " + TipoComprobanteResolver.letra(tipo), TITULO));
        if (informada) {
            der.addElement(new Paragraph("Nº " + numeroCompleto(f), NEGRITA));
        } else {
            der.addElement(new Paragraph("BORRADOR - sin numero asignado", NEGRITA));
        }
        der.addElement(new Paragraph("Fecha de emision: " + fecha(f.getFechaEmision()), NORMAL));
        if (ReglasFactura.ANULADA.equalsIgnoreCase(f.getEstado())) {
            der.addElement(new Paragraph("COMPROBANTE ANULADO", NEGRITA));
        }

        t.addCell(izq);
        t.addCell(centro);
        t.addCell(der);
        return t;
    }

    private static PdfPTable cliente(FacturaVenta f) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = celda();
        Cliente cli = f.getCliente();
        if (cli == null) {
            c.addElement(new Paragraph("Cliente: Consumidor Final", NORMAL));
        } else {
            c.addElement(new Paragraph("Cliente: " + cli.getDenominacion(), NORMAL));
            c.addElement(new Paragraph("CUIT/CUIL: " + FormatoCuit.visual(cli.getCuitCuil()), NORMAL));
            if (cli.getDomicilio() != null) {
                c.addElement(new Paragraph("Domicilio: " + nz(cli.getDomicilio().getNombreCalle()) + " "
                        + nz(cli.getDomicilio().getNumeroCalle()), NORMAL));
            }
        }
        if (f.getCondicionIva() != null) {
            c.addElement(new Paragraph("Condicion IVA: " + f.getCondicionIva().getDenominacion(), NORMAL));
        }
        t.addCell(c);
        return t;
    }

    private static PdfPTable renglones(FacturaVenta f, boolean discrimina) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[] { 1.2f, 4f, 1.2f, 2f, 1.3f, 2f });
        t.setWidthPercentage(100);
        String[] cab = { "Codigo", "Descripcion", "Cant.", "Precio unit." + (discrimina ? " (IVA incl.)" : ""),
                "Bonif.", "Subtotal" };
        for (String h : cab) {
            PdfPCell c = new PdfPCell(new Phrase(h, NEGRITA));
            c.setBackgroundColor(GRIS);
            c.setPadding(4);
            t.addCell(c);
        }
        if (f.getDetalles() != null) {
            for (FacturaVentaDetalle d : f.getDetalles()) {
                Articulo a = d.getListaPrecioArticulo() != null ? d.getListaPrecioArticulo().getArticulo() : null;
                String desc = (a != null ? a.getDenominacion() : "") + (d.getDescripcion() != null
                        && (a == null || !d.getDescripcion().equals(a.getDenominacion()))
                                ? " - " + d.getDescripcion() : "");
                t.addCell(texto(a != null ? a.getCodigo() : "", Element.ALIGN_LEFT));
                t.addCell(texto(desc, Element.ALIGN_LEFT));
                t.addCell(texto(numero(d.getCantidad()), Element.ALIGN_RIGHT));
                t.addCell(texto(dinero(d.getPrecioUnitario()), Element.ALIGN_RIGHT));
                t.addCell(texto(numero(d.getPorcentajeBonificacion()) + "%", Element.ALIGN_RIGHT));
                t.addCell(texto(dinero(d.getImporteSubtotal()), Element.ALIGN_RIGHT));
            }
        }
        return t;
    }

    private static PdfPTable totales(FacturaVenta f, boolean discrimina) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[] { 6f, 2.5f });
        t.setWidthPercentage(55);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (discrimina) {
            double neto = 0, iva = 0;
            if (f.getDetalles() != null) {
                for (FacturaVentaDetalle d : f.getDetalles()) {
                    neto += d.getImporteNeto();
                    iva += d.getImporteIva();
                }
            }
            fila(t, "Importe Neto Gravado", dinero(neto), NORMAL);
            fila(t, "IVA 21%", dinero(iva), NORMAL);
        }
        fila(t, "IMPORTE TOTAL", dinero(f.getImporteTotal()), NEGRITA);
        return t;
    }

    private static PdfPTable pie(FacturaVenta f, boolean informada) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = celda();
        if (f.getCae() != null && !f.getCae().isBlank()) {
            c.addElement(new Paragraph("CAE: " + f.getCae(), NEGRITA));
            c.addElement(new Paragraph("Vencimiento del CAE: " + fecha(f.getCaeFechaVencimiento()), NEGRITA));
        } else {
            c.addElement(new Paragraph("Comprobante sin CAE: aun no fue informado a ARCA. "
                    + "No tiene validez fiscal.", NEGRITA));
        }
        t.addCell(c);
        return t;
    }

    // --------------------------------------------------------------- helpers

    private static Paragraph espacio() {
        Paragraph p = new Paragraph(" ", CHICA);
        p.setSpacingAfter(2);
        return p;
    }

    private static PdfPCell celda() {
        PdfPCell c = new PdfPCell();
        c.setPadding(6);
        return c;
    }

    private static PdfPCell texto(String s, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(nz(s), NORMAL));
        c.setPadding(4);
        c.setHorizontalAlignment(alineacion);
        return c;
    }

    private static void fila(PdfPTable t, String etiqueta, String valor, Font fuente) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, fuente));
        a.setPadding(4);
        PdfPCell b = new PdfPCell(new Phrase(valor, fuente));
        b.setPadding(4);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
        t.addCell(b);
    }

    private static String numeroCompleto(FacturaVenta f) {
        int pv = f.getPuntoVenta() != null ? f.getPuntoVenta().getNumero() : 0;
        return String.format("%04d-%08d", pv, f.getNumero());
    }

    private static String fecha(Date d) {
        return d == null ? "-" : new SimpleDateFormat("dd/MM/yyyy").format(d);
    }

    private static DecimalFormat formato(String patron) {
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(new Locale("es", "AR")));
    }

    private static String dinero(double v) {
        return "$ " + formato("#,##0.00").format(v);
    }

    private static String numero(double v) {
        return formato("#,##0.##").format(v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
