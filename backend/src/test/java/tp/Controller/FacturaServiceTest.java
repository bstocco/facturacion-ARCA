package tp.Controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tp.ClasesArca.WsfeClient;
import tp.DTO.FacturaDetalleRequestDTO;
import tp.DTO.FacturaReporteDTO;
import tp.DTO.FacturaRequestDTO;
import tp.Entidad.*;
import tp.Exceptions.ReglaNegocioException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacturaServiceTest {

    @Mock
    FacturaRepository repo;
    @Mock
    EntityManager em;
    @Mock
    WsfeClient wsfe;
    @Mock
    tp.Controller.ConfiguracionService config;
    @InjectMocks
    FacturaService service;

    PuntoVenta pv = new PuntoVenta();
    CondicionIva ci = new CondicionIva();
    ListaPrecio minorista = new ListaPrecio();
    ListaPrecioArticulo lpa = new ListaPrecioArticulo();

    @BeforeEach
    void preparar() {
        pv.setNumero(1);
        pv.setDescripcion("Casa central");
        ci.setCodigoAfip(5);
        ci.setDenominacion("Consumidor Final");
        minorista.setId(3L);
        minorista.setDenominacion("Minorista");
        Articulo articulo = new Articulo();
        articulo.setDenominacion("Yerba");
        lpa.setArticulo(articulo);
        lpa.setListaPrecio(minorista);
        lpa.setPrecioVenta(121.0);
        when(em.find(ListaPrecio.class, 3L)).thenReturn(minorista);
        when(em.find(PuntoVenta.class, 1L)).thenReturn(pv);
        when(repo.buscarCondicionConsumidorFinal()).thenReturn(Optional.of(ci));
        when(config.isEmisorResponsableInscripto()).thenReturn(true);
        when(em.find(Usuario.class, 1L)).thenReturn(new Usuario());
        when(em.find(ListaPrecioArticulo.class, 7L)).thenReturn(lpa);
        when(repo.guardar(any(FacturaVenta.class))).thenAnswer(i -> i.getArgument(0));
        ReflectionTestUtils.setField(service, "arcaHabilitado", true);
    }

    // El precio ya no viaja en el request: sale de la lista (lpa = 121)
    private FacturaRequestDTO request(double cantidad, double precioIgnorado) {
        FacturaRequestDTO dto = new FacturaRequestDTO();
        dto.setFechaEmision(new Date());
        dto.setPuntoVentaId(1L);
        dto.setListaPrecioId(3L);
        dto.setDetalles(List.of(new FacturaDetalleRequestDTO(7L, "Item", cantidad, 0)));
        return dto;
    }

    private FacturaVenta facturaExistente(Long numero, String estado) {
        FacturaVenta f = new FacturaVenta();
        f.setNumero(numero);
        f.setEstado(estado);
        f.setPuntoVenta(pv);
        f.setCondicionIva(ci);
        f.setFechaEmision(new Date());
        f.setTipoComprobante(6);
        f.setDetalles(new ArrayList<>());
        FacturaVentaDetalle d = new FacturaVentaDetalle();
        d.setCantidad(1);
        d.setPrecioUnitario(121.0);
        d.setImporteNeto(100.0);
        d.setImporteIva(21.0);
        d.setImporteSubtotal(121.0);
        d.setFactura(f);
        f.getDetalles().add(d);
        f.setImporteTotal(121.0);
        when(repo.buscarPorId(5L)).thenReturn(Optional.of(f));
        return f;
    }

    // ---- crear ----

    @Test
    void crearGuardaBorradorSinNumeroNiLlamadaAArca() throws Exception {
        FacturaReporteDTO r = service.crear(request(2, 121.0));

        assertEquals("BORRADOR", r.getEstado());
        assertNull(r.getNumeroFactura());
        verifyNoInteractions(wsfe);
    }

    @Test
    void crearTomaElPrecioComoFinalConIvaIncluido() {
        FacturaReporteDTO r = service.crear(request(2, 121.0));
        assertEquals(242.0, r.getImporteTotal(), 0.001); // sin sumar otro 21%
    }

    @Test
    void crearDeduceElTipoDeComprobante() {
        assertEquals(6, service.crear(request(1, 121.0)).getTipoComprobante()); // RI -> consumidor final = B
        when(config.isEmisorResponsableInscripto()).thenReturn(false);
        assertEquals(11, service.crear(request(1, 121.0)).getTipoComprobante()); // monotributo = C
    }

    @Test
    void crearConListaDePrecioInexistenteFalla() {
        FacturaRequestDTO dto = request(1, 10);
        dto.getDetalles().get(0).setListaPrecioArticuloId(99L);
        assertThrows(tp.Exceptions.RecursoNoEncontradoException.class, () -> service.crear(dto));
    }

    // ---- listas de precios ----

    @Test
    void crearSinListaDePreciosFalla() {
        FacturaRequestDTO dto = request(1, 0);
        dto.setListaPrecioId(null);
        assertThrows(ReglaNegocioException.class, () -> service.crear(dto));
    }

    @Test
    void crearConArticuloDeOtraListaFalla() {
        ListaPrecio mayorista = new ListaPrecio();
        mayorista.setId(4L);
        mayorista.setDenominacion("Mayorista");
        when(em.find(ListaPrecio.class, 4L)).thenReturn(mayorista);
        FacturaRequestDTO dto = request(1, 0);
        dto.setListaPrecioId(4L); // el articulo 7 es de la minorista
        assertThrows(ReglaNegocioException.class, () -> service.crear(dto));
        verify(repo, never()).guardar(any());
    }

    @Test
    void crearConListaDadaDeBajaFalla() {
        minorista.setFechaBaja(new Date());
        assertThrows(tp.Exceptions.RecursoNoEncontradoException.class, () -> service.crear(request(1, 0)));
    }

    @Test
    void elPrecioSaleDeLaListaAunqueElClienteMandeOtro() {
        lpa.setPrecioVenta(50.0);
        FacturaReporteDTO r = service.crear(request(2, 0));
        assertEquals(100.0, r.getImporteTotal(), 0.001);
    }

    // ---- editar ----

    @Test
    void editarFacturaConNumeroEsRechazado() {
        facturaExistente(10L, "INFORMADA");
        assertThrows(ReglaNegocioException.class, () -> service.actualizar(5L, request(1, 121.0)));
        verify(repo, never()).guardar(any());
    }

    @Test
    void editarBorradorRecalculaImportes() {
        facturaExistente(null, "BORRADOR");
        FacturaReporteDTO r = service.actualizar(5L, request(3, 121.0));
        assertEquals(363.0, r.getImporteTotal(), 0.001);
    }

    // ---- eliminar ----

    @Test
    void eliminarFacturaConNumeroEsRechazado() {
        facturaExistente(10L, "INFORMADA");
        assertThrows(ReglaNegocioException.class, () -> service.eliminar(5L));
        verify(repo, never()).eliminar(any());
    }

    @Test
    void eliminarBorradorLoMarcaEliminadaSinBorrarlo() {
        FacturaVenta f = facturaExistente(null, "BORRADOR");
        service.eliminar(5L);
        verify(repo, never()).eliminar(any());
        assertEquals("ELIMINADA", f.getEstado());
        assertNotNull(f.getFechaBaja());
        verify(repo).guardar(f);
    }

    @Test
    void eliminarUnaFacturaYaEliminadaEsRechazado() {
        facturaExistente(null, "ELIMINADA");
        assertThrows(ReglaNegocioException.class, () -> service.eliminar(5L));
    }

    // ---- anular ----

    @Test
    void anularFacturaSinNumeroEsRechazado() {
        facturaExistente(null, "BORRADOR");
        assertThrows(ReglaNegocioException.class, () -> service.anular(5L));
    }

    @Test
    void anularFacturaInformadaLaMarcaAnulada() {
        FacturaVenta f = facturaExistente(10L, "INFORMADA");
        FacturaReporteDTO r = service.anular(5L);
        assertEquals("ANULADA", r.getEstado());
        assertNotNull(f.getFechaAnulacion());
    }

    // ---- informar a ARCA ----

    @Test
    void informarAprobadaAsignaNumeroCaeYEstadoInformada() throws Exception {
        FacturaVenta f = facturaExistente(null, "BORRADOR");
        when(wsfe.ultimoAutorizado(anyInt(), anyInt())).thenReturn(40L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeClient.Resultado("A", "7412", new Date(), null));

        FacturaReporteDTO r = service.informar(5L);

        assertEquals(41L, r.getNumeroFactura());
        assertEquals("INFORMADA", r.getEstado());
        assertEquals("7412", f.getCae());
    }

    @Test
    void informarRechazadaNoAsignaNumeroYQuedaRechazada() throws Exception {
        FacturaVenta f = facturaExistente(null, "BORRADOR");
        when(wsfe.ultimoAutorizado(anyInt(), anyInt())).thenReturn(40L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeClient.Resultado("R", null, null, "10015: cuit invalido"));

        FacturaReporteDTO r = service.informar(5L);

        assertNull(r.getNumeroFactura());
        assertEquals("RECHAZADA", r.getEstado());
        assertEquals("10015: cuit invalido", f.getMotivoRechazo());
    }

    @Test
    void informarFacturaYaInformadaEsRechazado() {
        facturaExistente(10L, "INFORMADA");
        assertThrows(ReglaNegocioException.class, () -> service.informar(5L));
    }

    @Test
    void informarConArcaDeshabilitadoEsRechazado() {
        facturaExistente(null, "BORRADOR");
        ReflectionTestUtils.setField(service, "arcaHabilitado", false);
        assertThrows(ReglaNegocioException.class, () -> service.informar(5L));
    }

    @Test
    void informarEnviaNetoEIvaDiscriminadosEnFacturaB() throws Exception {
        facturaExistente(null, "BORRADOR");
        when(wsfe.ultimoAutorizado(anyInt(), anyInt())).thenReturn(0L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeClient.Resultado("A", "1", new Date(), null));

        service.informar(5L);

        var captor = org.mockito.ArgumentCaptor.forClass(WsfeClient.Solicitud.class);
        verify(wsfe).solicitarCae(captor.capture());
        assertEquals(6, captor.getValue().cbteTipo());
        assertEquals(0, captor.getValue().neto().compareTo(new java.math.BigDecimal("100.00")));
        assertEquals(0, captor.getValue().iva().compareTo(new java.math.BigDecimal("21.00")));
        assertEquals(0, captor.getValue().total().compareTo(new java.math.BigDecimal("121.00")));
    }

    @Test
    void informarConFechaFueraDeLaVentanaDeArcaEsRechazadoSinLlamarAArca() {
        FacturaVenta f = facturaExistente(null, "BORRADOR");
        f.setFechaEmision(new Date(System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000));

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> service.informar(5L));

        assertTrue(e.getMessage().contains("5 dias"));
        verifyNoInteractions(wsfe);
        assertEquals("BORRADOR", f.getEstado());
    }

    @Test
    void informarConMonedaDistintaDePesosEsRechazado() {
        FacturaVenta f = facturaExistente(null, "BORRADOR");
        TipoMoneda dolar = new TipoMoneda();
        dolar.setCodigoAfip("DOL");
        dolar.setDenominacion("Dolar");
        f.setTipoMoneda(dolar);

        assertThrows(ReglaNegocioException.class, () -> service.informar(5L));
        verifyNoInteractions(wsfe);
    }

    @Test
    void informarSeparaNetoEIvaSobreElTotalYCierraAlCentavo() throws Exception {
        FacturaVenta f = facturaExistente(null, "BORRADOR");
        // 3 renglones de 0,01 de diferencia por redondeo si se redondeara renglon por renglon
        f.getDetalles().clear();
        for (int i = 0; i < 3; i++) {
            FacturaVentaDetalle d = new FacturaVentaDetalle();
            d.setCantidad(1);
            d.setPrecioUnitario(10.05);
            d.setFactura(f);
            f.getDetalles().add(d);
        }
        when(wsfe.ultimoAutorizado(anyInt(), anyInt())).thenReturn(0L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeClient.Resultado("A", "1", new Date(), null));

        service.informar(5L);

        var captor = org.mockito.ArgumentCaptor.forClass(WsfeClient.Solicitud.class);
        verify(wsfe).solicitarCae(captor.capture());
        WsfeClient.Solicitud s = captor.getValue();
        assertEquals(0, s.total().compareTo(new java.math.BigDecimal("30.15")));
        assertEquals(0, s.neto().add(s.iva()).compareTo(s.total()));
    }

    // ---- condicion IVA tomada del cliente ----

    @Test
    void crearTomaLaCondicionIvaDelCliente() {
        CondicionIva ri = new CondicionIva();
        ri.setCodigoAfip(1);
        ri.setDenominacion("Responsable Inscripto");
        Cliente cliente = new Cliente();
        cliente.setCondicionIva(ri);
        cliente.setDenominacion("Ana");
        when(em.find(Cliente.class, 4L)).thenReturn(cliente);

        FacturaRequestDTO dto = request(1, 121.0);
        dto.setClienteId(4L);
        FacturaReporteDTO r = service.crear(dto);

        assertEquals("Responsable Inscripto", r.getCondicionIva());
        assertEquals(1, r.getTipoComprobante()); // emisor RI -> receptor RI = A
    }

    @Test
    void crearSinClienteUsaConsumidorFinal() {
        FacturaReporteDTO r = service.crear(request(1, 121.0));
        assertEquals("Consumidor Final", r.getCondicionIva());
        assertEquals(6, r.getTipoComprobante());
    }

    @Test
    void crearConClienteSinCondicionIvaUsaConsumidorFinal() {
        Cliente cliente = new Cliente();
        cliente.setDenominacion("Sin condicion");
        when(em.find(Cliente.class, 4L)).thenReturn(cliente);
        FacturaRequestDTO dto = request(1, 121.0);
        dto.setClienteId(4L);
        assertEquals("Consumidor Final", service.crear(dto).getCondicionIva());
    }

    // ---- informar recalcula con la configuracion vigente ----

    @Test
    void informarRecalculaElTipoConLaConfiguracionVigente() throws Exception {
        FacturaVenta f = facturaExistente(null, "BORRADOR");
        f.setTipoComprobante(11); // se creo cuando el emisor era monotributista
        FacturaVentaDetalle d = f.getDetalles().get(0);
        d.setCantidad(1);
        d.setPrecioUnitario(121.0);
        d.setImporteNeto(121.0);
        d.setImporteIva(0.0);
        d.setImporteSubtotal(121.0);
        when(wsfe.ultimoAutorizado(anyInt(), anyInt())).thenReturn(0L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeClient.Resultado("A", "1", new Date(), null));

        service.informar(5L); // ahora el emisor es RI

        var captor = org.mockito.ArgumentCaptor.forClass(WsfeClient.Solicitud.class);
        verify(wsfe).solicitarCae(captor.capture());
        assertEquals(6, captor.getValue().cbteTipo());
        assertEquals(0, captor.getValue().neto().compareTo(new java.math.BigDecimal("100.00")));
        assertEquals(0, captor.getValue().iva().compareTo(new java.math.BigDecimal("21.00")));
        assertEquals(6, f.getTipoComprobante());
    }

    // ---- PDF ----

    @Test
    void generarPdfDevuelveUnPdfDeLaFactura() {
        facturaExistente(10L, "INFORMADA");
        ReflectionTestUtils.setField(service, "cuitEmisor", "20473735106");
        byte[] pdf = service.generarPdf(5L);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void generarPdfDeUnaFacturaEliminadaEsRechazado() {
        facturaExistente(null, "ELIMINADA");
        assertThrows(ReglaNegocioException.class, () -> service.generarPdf(5L));
    }
}
