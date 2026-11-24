package tp.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tp.DTO.FacturaDetalleResponseDTO;
import tp.DTO.FacturaReporteDTO;
import tp.DTO.FacturaRequestDTO;
import tp.DTO.PaginaDTO;

import tp.Negocio.ReporteListadoExportador;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {

    @Autowired
    private FacturaService facturaService;

    @GetMapping
    public PaginaDTO<FacturaReporteDTO> buscarFacturasFiltradas(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long articuloId,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) Boolean informada,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {

        return facturaService.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, articuloId, numero, informada, pagina, tamanio);
    }

    /** Exporta el listado con los mismos filtros de la grilla: formato=pdf o formato=excel (CSV). */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "pdf") String formato,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long articuloId,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) Boolean informada) {

        List<FacturaReporteDTO> facturas = facturaService.buscarParaExportar(fechaDesde, fechaHasta, estado,
                montoMinimo, clienteId, articuloId, numero, informada);
        if ("excel".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header("Content-Disposition", "attachment; filename=\"facturas.csv\"")
                    .body(ReporteListadoExportador.csv(facturas));
        }
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        List<String> filtros = new ArrayList<>();
        if (fechaDesde != null) filtros.add("desde " + df.format(fechaDesde));
        if (fechaHasta != null) filtros.add("hasta " + df.format(fechaHasta));
        if (estado != null && !estado.isBlank()) filtros.add("estado " + estado);
        if (montoMinimo != null) filtros.add("monto minimo " + montoMinimo);
        if (clienteId != null) filtros.add("cliente #" + clienteId);
        if (articuloId != null) filtros.add("articulo #" + articuloId);
        if (numero != null) filtros.add("numero " + numero);
        if (informada != null) filtros.add(informada ? "informadas a ARCA" : "no informadas a ARCA");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"facturas.pdf\"")
                .body(ReporteListadoExportador.pdf(facturas, filtros.isEmpty() ? "ninguno" : String.join(", ", filtros)));
    }

    @GetMapping("/{id}")
    public FacturaDetalleResponseDTO obtener(@PathVariable Long id) {
        return facturaService.obtener(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = facturaService.generarPdf(id);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"factura-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@RequestBody FacturaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facturaService.crear(dto));
    }

    @PutMapping("/{id}")
    public FacturaReporteDTO actualizar(@PathVariable Long id, @RequestBody FacturaRequestDTO dto) {
        return facturaService.actualizar(id, dto);
    }

    @PostMapping("/{id}/informar")
    public FacturaReporteDTO informar(@PathVariable Long id) {
        return facturaService.informar(id);
    }

    @PostMapping("/{id}/anular")
    public FacturaReporteDTO anular(@PathVariable Long id) {
        return facturaService.anular(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        facturaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
