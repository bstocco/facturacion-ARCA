package tp.Negocio;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import tp.DTO.FacturaReporteDTO;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Exporta el listado de facturas (con los filtros aplicados) a PDF y a Excel (CSV). */
public final class ReporteListadoExportador {

    private static final String[] COLUMNAS = { "Numero", "Tipo", "Fecha", "Cliente", "Condicion IVA",
            "Punto de venta", "Articulo", "Cantidad", "Importe total", "Estado" };

    private static final Font TITULO = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
    private static final Font NEGRITA = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
    private static final Font NORMAL = new Font(Font.FontFamily.HELVETICA, 8);
    private static final BaseColor GRIS = new BaseColor(230, 230, 230);

    private ReporteListadoExportador() {
    }

    public static byte[] pdf(List<FacturaReporteDTO> facturas, String filtros) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(doc, salida);
            doc.open();
            doc.add(new Paragraph("Reporte de facturas de venta", TITULO));
            doc.add(new Paragraph("Generado: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())
                    + "   |   Filtros: " + filtros, NORMAL));
            doc.add(new Paragraph(" ", NORMAL));

            PdfPTable t = new PdfPTable(new float[] { 1f, 0.6f, 1.1f, 2.6f, 1.6f, 1.6f, 2.6f, 1f, 1.4f, 1.2f });
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String c : COLUMNAS) {
                PdfPCell celda = new PdfPCell(new Phrase(c, NEGRITA));
                celda.setBackgroundColor(GRIS);
                celda.setPadding(4);
                t.addCell(celda);
            }
            for (FacturaReporteDTO f : facturas) {
                String[] v = fila(f);
                for (int i = 0; i < v.length; i++) {
                    PdfPCell celda = new PdfPCell(new Phrase(v[i], NORMAL));
                    celda.setPadding(3);
                    if (i == 7 || i == 8) {
                        celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    }
                    t.addCell(celda);
                }
            }
            doc.add(t);
            doc.add(new Paragraph(" ", NORMAL));
            doc.add(new Paragraph("Comprobantes: " + facturas.size() + "   |   Importe total (sin anuladas): $ "
                    + dinero(totalSinAnuladas(facturas)), NEGRITA));
            doc.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del listado: " + e.getMessage(), e);
        }
    }

    /** CSV separado por ';' con BOM UTF-8: Excel en castellano lo abre directo, con acentos y columnas. */
    public static byte[] csv(List<FacturaReporteDTO> facturas) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(";", COLUMNAS)).append("\r\n");
        for (FacturaReporteDTO f : facturas) {
            String[] v = fila(f);
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    sb.append(';');
                }
                sb.append(csvCampo(v[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static double totalSinAnuladas(List<FacturaReporteDTO> facturas) {
        return facturas.stream().filter(f -> !ReglasFactura.ANULADA.equalsIgnoreCase(f.getEstado()))
                .mapToDouble(FacturaReporteDTO::getImporteTotal).sum();
    }

    static String csvCampo(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String[] fila(FacturaReporteDTO f) {
        String articulo = f.getArticulo() == null ? "-"
                : f.getArticulo() + (f.getItemsAdicionales() > 0 ? " (+" + f.getItemsAdicionales() + " mas)" : "");
        return new String[] {
                f.getNumeroFactura() != null ? String.valueOf(f.getNumeroFactura()) : "-",
                f.getTipoComprobante() != null ? TipoComprobanteResolver.letra(f.getTipoComprobante()) : "-",
                f.getFechaEmision() != null ? new SimpleDateFormat("dd/MM/yyyy").format(f.getFechaEmision()) : "-",
                nz(f.getClienteDenominacion()), nz(f.getCondicionIva()), nz(f.getPuntoVentaDescripcion()),
                articulo, formato("#,##0.##").format(f.getCantidadTotal()), dinero(f.getImporteTotal()),
                nz(f.getEstado()) };
    }

    private static DecimalFormat formato(String patron) {
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(new Locale("es", "AR")));
    }

    private static String dinero(double v) {
        return formato("#,##0.00").format(v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
