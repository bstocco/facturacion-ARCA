package tp.DTO;

import java.util.Date;
import java.util.List;

/** Factura completa, para cargarla en el formulario de edicion. */
public record FacturaDetalleResponseDTO(
        Long id, Long numeroFactura, Date fechaEmision,
        Long clienteId, String clienteEtiqueta,
        String condicionIva, Long puntoVentaId, Long tipoMonedaId,
        String estado, String observaciones, Integer tipoComprobante,
        String cae, String motivoRechazo, String puntoVentaDescripcion,
        double importeNeto, double importeIva, double importeTotal,
        Long listaPrecioId, String listaPrecio,
        List<Item> detalles) {

    public record Item(Long listaPrecioArticuloId, String etiqueta, String descripcion,
            double cantidad, double precioUnitario, double porcentajeBonificacion,
            double importeNeto, double importeIva, double importeSubtotal) {
    }
}
