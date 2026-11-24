package tp.Controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tp.ClasesArca.WsfeClient;
import tp.DTO.FacturaDetalleRequestDTO;
import tp.DTO.FacturaDetalleResponseDTO;
import tp.DTO.FacturaReporteDTO;
import tp.DTO.FacturaRequestDTO;
import tp.DTO.PaginaDTO;
import tp.Entidad.*;
import tp.Exceptions.RecursoNoEncontradoException;
import tp.Exceptions.ReglaNegocioException;
import tp.Negocio.CalculadoraImportes;
import tp.Negocio.FormatoCuit;
import tp.Negocio.ReglasFactura;
import tp.Negocio.TipoComprobanteResolver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class FacturaService {

    @Autowired
    private FacturaRepository facturaRepository;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private WsfeClient wsfeClient;

    // Si es false, no se puede informar a ARCA.
    @Value("${arca.habilitado:false}")
    private boolean arcaHabilitado;

    @Value("${arca.cuit:}")
    private String cuitEmisor;

    // Quien emite (Responsable Inscripto o Monotributista): se cambia desde el sitio.
    @Autowired
    private ConfiguracionService configuracion;

    public PaginaDTO<FacturaReporteDTO> buscarFacturasFiltradas(Date fechaDesde, Date fechaHasta, String estado,
            Double montoMinimo, Long clienteId, Long articuloId, Long numero, Boolean informada,
            int pagina, int tamanio) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamanio, 1), 100);
        return facturaRepository.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, articuloId, numero, informada, p, t);
    }

    /** Todas las facturas que cumplen los filtros (sin paginar), para exportar el listado. */
    public List<FacturaReporteDTO> buscarParaExportar(Date fechaDesde, Date fechaHasta, String estado,
            Double montoMinimo, Long clienteId, Long articuloId, Long numero, Boolean informada) {
        // shortcut: tope de 5000 filas por exportacion; paginar el archivo si el volumen crece
        return facturaRepository.buscarFacturasFiltradas(fechaDesde, fechaHasta, estado, montoMinimo,
                clienteId, articuloId, numero, informada, 0, 5000).contenido();
    }

    @Transactional(readOnly = true)
    public FacturaDetalleResponseDTO obtener(Long id) {
        FacturaVenta f = buscar(id);
        List<FacturaDetalleResponseDTO.Item> items = new ArrayList<>();
        double neto = 0, iva = 0;
        for (FacturaVentaDetalle d : f.getDetalles()) {
            ListaPrecioArticulo lpa = d.getListaPrecioArticulo();
            Articulo a = lpa.getArticulo();
            String etiqueta = a.getCodigo() + " - " + a.getDenominacion() + " (" + lpa.getListaPrecio().getDenominacion() + ")";
            items.add(new FacturaDetalleResponseDTO.Item(lpa.getId(), etiqueta, d.getDescripcion(),
                    d.getCantidad(), d.getPrecioUnitario(), d.getPorcentajeBonificacion(),
                    d.getImporteNeto(), d.getImporteIva(), d.getImporteSubtotal()));
            neto += d.getImporteNeto();
            iva += d.getImporteIva();
        }
        Cliente c = f.getCliente();
        // Todos los renglones son de la misma lista: se toma la del primero
        ListaPrecio lista = f.getDetalles().isEmpty() ? null
                : f.getDetalles().get(0).getListaPrecioArticulo().getListaPrecio();
        return new FacturaDetalleResponseDTO(f.getId(), f.getNumero(), f.getFechaEmision(),
                c != null ? c.getId() : null,
                c != null ? c.getDenominacion() + " (" + FormatoCuit.visual(c.getCuitCuil()) + ")" : null,
                f.getCondicionIva() != null ? f.getCondicionIva().getDenominacion() : null,
                f.getPuntoVenta() != null ? f.getPuntoVenta().getId() : null,
                f.getTipoMoneda() != null ? f.getTipoMoneda().getId() : null,
                f.getEstado(), f.getObservaciones(), f.getTipoComprobante(),
                f.getCae(), f.getMotivoRechazo(),
                f.getPuntoVenta() != null ? f.getPuntoVenta().getDescripcion() : null,
                redondear(neto), redondear(iva), f.getImporteTotal(),
                lista != null ? lista.getId() : null,
                lista != null ? lista.getCodigo() + " - " + lista.getDenominacion() : null, items);
    }

    /** PDF de la factura, con CAE y vencimiento si ya fue informada a ARCA. */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long id) {
        FacturaVenta f = buscar(id);
        if (ReglasFactura.ELIMINADA.equalsIgnoreCase(f.getEstado())) {
            throw new ReglaNegocioException("La factura esta eliminada: no se puede generar el PDF.");
        }
        return tp.Negocio.FacturaPdfGenerator.generar(f, cuitEmisor);
    }

    @Transactional
    public FacturaReporteDTO crear(FacturaRequestDTO dto) {
        FacturaVenta factura = new FacturaVenta();
        Usuario usuario = em.find(Usuario.class, 1L);
        if (usuario == null) {
            throw new RecursoNoEncontradoException("No se encontro el usuario con id 1 para auditoria");
        }
        factura.setUsuarioCarga(usuario);
        factura.setUsuarioModificacion(usuario);
        factura.setEstado(ReglasFactura.BORRADOR);
        factura.setNumero(null);
        factura.setDetalles(new ArrayList<>());

        aplicarDatos(factura, dto);
        return mapearAReporteDTO(facturaRepository.guardar(factura));
    }

    @Transactional
    public FacturaReporteDTO actualizar(Long id, FacturaRequestDTO dto) {
        FacturaVenta factura = buscar(id);
        if (!ReglasFactura.puedeEditar(factura)) {
            throw new ReglaNegocioException(
                    "La factura ya fue informada a ARCA (tiene numero) o esta anulada: no se puede editar.");
        }
        aplicarDatos(factura, dto);
        return mapearAReporteDTO(facturaRepository.guardar(factura));
    }

    /** Borrado logico: la factura queda en el listado con estado ELIMINADA. */
    @Transactional
    public void eliminar(Long id) {
        FacturaVenta factura = buscar(id);
        if (!ReglasFactura.puedeEliminar(factura)) {
            throw new ReglaNegocioException(
                    "La factura ya tiene numero asignado o ya esta eliminada: no se puede eliminar (solo anular).");
        }
        Usuario usuario = em.find(Usuario.class, 1L);
        factura.setEstado(ReglasFactura.ELIMINADA);
        factura.setFechaBaja(new Date());
        factura.setUsuarioBaja(usuario);
        facturaRepository.guardar(factura);
    }

    @Transactional
    public FacturaReporteDTO anular(Long id) {
        FacturaVenta factura = buscar(id);
        if (!ReglasFactura.puedeAnular(factura)) {
            throw new ReglaNegocioException(
                    "Solo se puede anular una factura que ya tiene numero (informada a ARCA) y no esta anulada.");
        }
        factura.setEstado(ReglasFactura.ANULADA);
        factura.setFechaAnulacion(new Date());
        return mapearAReporteDTO(facturaRepository.guardar(factura));
    }

    /**
     * Pide el CAE a ARCA y completa numero, cae, vencimiento y resultado. Si ARCA
     * rechaza, la factura queda RECHAZADA y sin numero (se puede editar y reintentar).
     * Si ARCA no responde se lanza excepcion y @Transactional revierte.
     */
    @Transactional
    public FacturaReporteDTO informar(Long id) {
        if (!arcaHabilitado) {
            throw new ReglaNegocioException("La integracion con ARCA esta deshabilitada (arca.habilitado=false).");
        }
        FacturaVenta factura = buscar(id);
        if (!ReglasFactura.puedeInformar(factura)) {
            throw new ReglaNegocioException(
                    "Solo se pueden informar facturas BORRADOR o RECHAZADAS que no tengan numero.");
        }
        validarParaArca(factura);
        emitirEnArca(factura);
        return mapearAReporteDTO(facturaRepository.guardar(factura));
    }

    // ---------------------------------------------------------------- privados

    private FacturaVenta buscar(Long id) {
        return facturaRepository.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la factura con id " + id));
    }

    /** Carga cabecera y renglones desde el DTO y recalcula importes (precio con IVA incluido). */
    private void aplicarDatos(FacturaVenta factura, FacturaRequestDTO dto) {
        PuntoVenta puntoVenta = em.find(PuntoVenta.class, dto.getPuntoVentaId());
        if (puntoVenta == null) {
            throw new RecursoNoEncontradoException("No existe el punto de venta con id " + dto.getPuntoVentaId());
        }
        Cliente cliente = null;
        if (dto.getClienteId() != null) {
            cliente = em.find(Cliente.class, dto.getClienteId());
            if (cliente == null) {
                throw new RecursoNoEncontradoException("No existe el cliente con id " + dto.getClienteId());
            }
        }
        // La condicion IVA sale del cliente; sin cliente (o sin condicion cargada) = Consumidor Final
        CondicionIva condicionIva = cliente != null ? cliente.getCondicionIva() : null;
        if (condicionIva == null) {
            condicionIva = facturaRepository.buscarCondicionConsumidorFinal().orElseThrow(
                    () -> new RecursoNoEncontradoException("No existe la condicion IVA Consumidor Final"));
        }
        TipoMoneda tipoMoneda = null;
        if (dto.getTipoMonedaId() != null) {
            tipoMoneda = em.find(TipoMoneda.class, dto.getTipoMonedaId());
            if (tipoMoneda == null) {
                throw new RecursoNoEncontradoException("No existe el tipo de moneda con id " + dto.getTipoMonedaId());
            }
        }

        if (dto.getListaPrecioId() == null) {
            throw new ReglaNegocioException("Debe elegir una lista de precios (mayorista, minorista, etc.).");
        }
        ListaPrecio listaPrecio = em.find(ListaPrecio.class, dto.getListaPrecioId());
        if (listaPrecio == null || listaPrecio.getFechaBaja() != null) {
            throw new RecursoNoEncontradoException("No existe la lista de precios con id " + dto.getListaPrecioId());
        }

        int tipo = TipoComprobanteResolver.resolver(configuracion.isEmisorResponsableInscripto(), condicionIva.getCodigoAfip());

        factura.setFechaEmision(dto.getFechaEmision() != null ? dto.getFechaEmision() : new Date());
        factura.setPuntoVenta(puntoVenta);
        factura.setCondicionIva(condicionIva);
        factura.setCliente(cliente);
        factura.setTipoMoneda(tipoMoneda);
        factura.setObservaciones(dto.getObservaciones());
        factura.setTipoComprobante(tipo);
        factura.setResultadoAfip(null);
        factura.setMotivoRechazo(null);
        if (ReglasFactura.RECHAZADA.equals(factura.getEstado())) {
            factura.setEstado(ReglasFactura.BORRADOR);
        }

        List<FacturaVentaDetalle> nuevos = new ArrayList<>();
        if (dto.getDetalles() != null) {
            for (FacturaDetalleRequestDTO det : dto.getDetalles()) {
                ListaPrecioArticulo lpa = det.getListaPrecioArticuloId() == null ? null
                        : em.find(ListaPrecioArticulo.class, det.getListaPrecioArticuloId());
                if (lpa == null) {
                    throw new RecursoNoEncontradoException(
                            "No existe la lista de precio articulo con id " + det.getListaPrecioArticuloId());
                }
                if (!listaPrecio.getId().equals(lpa.getListaPrecio().getId())) {
                    throw new ReglaNegocioException("El articulo " + lpa.getArticulo().getDenominacion()
                            + " no pertenece a la lista de precios " + listaPrecio.getDenominacion()
                            + ": todos los items deben ser de la misma lista.");
                }
                if (lpa.getFechaBaja() != null) {
                    throw new ReglaNegocioException("El articulo " + lpa.getArticulo().getDenominacion()
                            + " fue dado de baja de la lista " + listaPrecio.getDenominacion() + ".");
                }
                FacturaVentaDetalle d = new FacturaVentaDetalle();
                d.setFactura(factura);
                d.setListaPrecioArticulo(lpa);
                d.setDescripcion(det.getDescripcion());
                d.setCantidad(det.getCantidad());
                // El precio sale siempre de la lista: no se confia en el que mande el cliente
                d.setPrecioUnitario(lpa.getPrecioVenta());
                d.setPorcentajeBonificacion(det.getPorcentajeBonificacion());
                nuevos.add(d);
            }
        }
        if (factura.getDetalles() == null) {
            factura.setDetalles(new ArrayList<>());
        }
        factura.getDetalles().clear();
        factura.getDetalles().addAll(nuevos);
        recalcularImportes(factura, tipo);
    }

    /** Recalcula neto/IVA/subtotal de cada renglon y los totales segun el tipo de comprobante. */
    private void recalcularImportes(FacturaVenta factura, int tipo) {
        boolean discrimina = TipoComprobanteResolver.discriminaIva(tipo);
        BigDecimal total = BigDecimal.ZERO;
        for (FacturaVentaDetalle d : factura.getDetalles()) {
            CalculadoraImportes.Importes imp = CalculadoraImportes.calcular(d.getCantidad(),
                    d.getPrecioUnitario(), d.getPorcentajeBonificacion(), discrimina);
            d.setImporteNeto(imp.neto());
            d.setImporteIva(imp.iva());
            d.setImporteSubtotal(imp.subtotal());
            total = total.add(BigDecimal.valueOf(imp.subtotal()));
        }
        factura.setTipoComprobante(tipo);
        factura.setImporteTotal(total.doubleValue());
        factura.setImporteSaldo(total.doubleValue());
    }

    /** ARCA acepta fecha de comprobante de hasta 5 dias antes o despues de hoy (productos) y solo pesos. */
    private void validarParaArca(FacturaVenta factura) {
        if (factura.getFechaEmision() != null) {
            java.time.LocalDate emision = java.time.LocalDate
                    .parse(new SimpleDateFormat("yyyy-MM-dd").format(factura.getFechaEmision()));
            long dias = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), emision);
            if (Math.abs(dias) > 5) {
                throw new ReglaNegocioException("ARCA solo acepta facturas con fecha de hasta 5 dias antes o "
                        + "despues de hoy. Edite el borrador y corrija la fecha de emision antes de enviarlo.");
            }
        }
        TipoMoneda m = factura.getTipoMoneda();
        if (m != null && !esPesos(m)) {
            throw new ReglaNegocioException("Solo se pueden enviar a ARCA facturas en pesos. "
                    + "Edite el borrador y elija moneda Pesos (o sin especificar).");
        }
    }

    private static boolean esPesos(TipoMoneda m) {
        String codigo = m.getCodigoAfip() == null ? "" : m.getCodigoAfip().trim().toUpperCase();
        String nombre = m.getDenominacion() == null ? "" : m.getDenominacion().toLowerCase();
        return codigo.isEmpty() || codigo.equals("PES") || codigo.equals("ARS") || codigo.equals("1")
                || codigo.equals("$") || nombre.contains("peso");
    }

    private void emitirEnArca(FacturaVenta factura) {
        try {
            // Se recalcula con la configuracion de emisor vigente: lo enviado siempre es consistente
            int cbteTipo = TipoComprobanteResolver.resolver(configuracion.isEmisorResponsableInscripto(),
                    factura.getCondicionIva().getCodigoAfip());
            recalcularImportes(factura, cbteTipo);

            // El total es la suma de los subtotales (precio con IVA); el neto y el IVA se separan sobre el
            // total (como en el ejemplo de ARCA) para que IVA = total - neto cierre siempre al centavo.
            BigDecimal total = BigDecimal.ZERO;
            for (FacturaVentaDetalle d : factura.getDetalles()) {
                total = total.add(BigDecimal.valueOf(d.getImporteSubtotal()));
            }
            total = total.setScale(2, RoundingMode.HALF_UP);
            BigDecimal neto = total;
            BigDecimal iva = BigDecimal.ZERO.setScale(2);
            if (TipoComprobanteResolver.discriminaIva(cbteTipo)) {
                neto = total.divide(CalculadoraImportes.ALICUOTA, 2, RoundingMode.HALF_UP);
                iva = total.subtract(neto);
            }

            Cliente cliente = factura.getCliente();
            int docTipo = 99; // Consumidor final
            long docNro = 0;
            if (cliente != null && cliente.getCuitCuil() != null) {
                String digitos = cliente.getCuitCuil().replaceAll("\\D", "");
                if (digitos.length() == 11) {
                    docTipo = 80; // CUIT
                    docNro = Long.parseLong(digitos);
                }
            }

            int ptoVta = factura.getPuntoVenta().getNumero();
            long nro = wsfeClient.ultimoAutorizado(ptoVta, cbteTipo) + 1;
            String fecha = new SimpleDateFormat("yyyyMMdd").format(factura.getFechaEmision());

            WsfeClient.Resultado r = wsfeClient.solicitarCae(new WsfeClient.Solicitud(
                    ptoVta, cbteTipo, nro, docTipo, docNro,
                    factura.getCondicionIva().getCodigoAfip(), fecha, total, neto, iva));

            factura.setResultadoAfip(r.resultado());
            if ("A".equals(r.resultado())) {
                factura.setNumero(nro);
                factura.setCae(r.cae());
                factura.setCaeFechaVencimiento(r.caeVto());
                factura.setMotivoRechazo(null);
                factura.setEstado(ReglasFactura.INFORMADA);
            } else {
                factura.setMotivoRechazo(r.observaciones());
                factura.setEstado(ReglasFactura.RECHAZADA);
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo informar a ARCA: " + e.getMessage(), e);
        }
    }

    private static double redondear(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private FacturaReporteDTO mapearAReporteDTO(FacturaVenta factura) {
        String cliente = factura.getCliente() != null ? factura.getCliente().getDenominacion() : "Consumidor Final";
        String condicion = factura.getCondicionIva() != null ? factura.getCondicionIva().getDenominacion() : "";
        String pv = factura.getPuntoVenta() != null ? factura.getPuntoVenta().getDescripcion() : "";
        long items = factura.getDetalles() != null ? factura.getDetalles().size() : 0;
        FacturaReporteDTO dto = new FacturaReporteDTO(factura.getId(), factura.getNumero(),
                factura.getFechaEmision(), cliente, condicion, pv, factura.getImporteTotal(), items,
                factura.getEstado(), factura.getTipoComprobante(), factura.getMotivoRechazo());
        if (factura.getDetalles() != null && !factura.getDetalles().isEmpty()) {
            ListaPrecioArticulo lpa = factura.getDetalles().get(0).getListaPrecioArticulo();
            dto.setArticulo(lpa != null && lpa.getArticulo() != null ? lpa.getArticulo().getDenominacion() : null);
            dto.setItemsAdicionales(factura.getDetalles().size() - 1);
            dto.setCantidadTotal(factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getCantidad).sum());
        }
        return dto;
    }
}
