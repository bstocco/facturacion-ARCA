import {
    ArticuloExploradorDTO,
    ClienteExploradorDTO,
    ComprobanteTipoDTO,
    DetalleFormulario,
    FacturaFiltrosDTO,
    FacturaReporteDTO,
    FacturaRequestDTO,
    OpcionDTO,
    OpcionesArticuloDTO
} from './models/factura.dto.js';
import { FacturaService } from './FacturaService.js';
import { CatalogoService } from './Catalogoservice.js';
import { abrirExplorador, renderizarPaginacion } from './explorador.js';

declare const bootstrap: any;

const facturaService = new FacturaService();
const catalogoService = new CatalogoService();

const TAMANIO_PAGINA = 10;

const el = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;

const tbody = el<HTMLTableSectionElement>('facturas-tbody');
const filtrosForm = el<HTMLFormElement>('filtros-form');
const btnLimpiar = el<HTMLButtonElement>('btn-limpiar');
const paginacionEl = el<HTMLElement>('facturas-paginacion');

const facturaForm = el<HTMLFormElement>('factura-form');
const tituloFormulario = el<HTMLElement>('titulo-formulario');
const fechaEmisionInput = el<HTMLInputElement>('fecha-emision');
const puntoVentaInput = el<HTMLSelectElement>('punto-venta-id');
const tipoMonedaInput = el<HTMLSelectElement>('tipo-moneda-id');
const listaPrecioInput = el<HTMLSelectElement>('lista-precio-id');
const observacionesInput = el<HTMLTextAreaElement>('observaciones');
const clienteTexto = el<HTMLInputElement>('cliente-texto');
const tipoComprobanteInput = el<HTMLInputElement>('tipo-comprobante');
const switchEmisor = el<HTMLInputElement>('switch-emisor');
const switchEmisorLabel = el<HTMLElement>('switch-emisor-label');
const switchConsumidorFinal = el<HTMLInputElement>('switch-consumidor-final');
const btnCliente = el<HTMLButtonElement>('btn-cliente');
const btnClienteX = el<HTMLButtonElement>('btn-cliente-x');
const btnGuardar = el<HTMLButtonElement>('btn-guardar');
const btnCancelarEdicion = el<HTMLButtonElement>('btn-cancelar-edicion');

const detallesTbody = el<HTMLTableSectionElement>('detalles-tbody');
const importeTotalCell = el<HTMLTableCellElement>('importe-total');
const importeNetoCell = el<HTMLTableCellElement>('importe-neto');
const importeIvaCell = el<HTMLTableCellElement>('importe-iva');
const filaNeto = el<HTMLTableRowElement>('fila-neto');
const filaIva = el<HTMLTableRowElement>('fila-iva');
const mensajeEstado = el<HTMLDivElement>('mensaje-estado');

// ---- estado en memoria ----
let detalles: DetalleFormulario[] = [];
let clienteId: number | null = null;
let comprobante: ComprobanteTipoDTO | null = null;
let facturaEditandoId: number | null = null;
// Lista vigente, para poder volver atrás si el usuario cancela el cambio de lista
let listaPrecioActual = '';
let paginaActual = 0;
// Filtros de explorador (cliente / artículo) del listado
let filtroClienteId: number | null = null;
let filtroArticuloId: number | null = null;

function mostrarMensaje(texto: string, esError: boolean = false): void {
    mensajeEstado.textContent = texto;
    mensajeEstado.className = esError ? 'alert alert-danger' : 'alert alert-success';
}

function ocultarMensaje(): void {
    mensajeEstado.className = 'alert d-none';
    mensajeEstado.textContent = '';
}

// Toma el yyyy-MM-dd tal cual viene, sin pasar por Date, para evitar corrimientos de zona horaria
function formatearFecha(fechaStr: string | undefined): string {
    if (!fechaStr) return '-';
    const partes = fechaStr.substring(0, 10).split('-');
    if (partes.length !== 3) return fechaStr;
    return `${partes[2]}/${partes[1]}/${partes[0]}`;
}

// Fecha de hoy en hora local (toISOString usa UTC y después de las 21 h daría la de mañana)
function fechaLocalHoy(): string {
    const h = new Date();
    const dos = (n: number) => String(n).padStart(2, '0');
    return `${h.getFullYear()}-${dos(h.getMonth() + 1)}-${dos(h.getDate())}`;
}

function formatearMoneda(valor: number | undefined | null): string {
    if (valor === undefined || valor === null) return '0,00';
    return valor.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// El CUIT se guarda con dígitos; los guiones son solo visuales (XX-XXXXXXXX-X)
function formatearCuit(cuit: string | null | undefined): string {
    const d = (cuit ?? '').replace(/\D/g, '');
    return d.length === 11 ? `${d.substring(0, 2)}-${d.substring(2, 10)}-${d.substring(10)}` : (cuit ?? '');
}

function letraDe(tipo: number | null | undefined): string {
    switch (tipo) {
        case 1: return 'A';
        case 6: return 'B';
        case 11: return 'C';
        default: return '-';
    }
}

function llenarSelect(select: HTMLSelectElement, opciones: OpcionDTO[], textoVacio: string): void {
    select.innerHTML = '';
    const vacia = document.createElement('option');
    vacia.value = '';
    vacia.textContent = textoVacio;
    select.appendChild(vacia);
    opciones.forEach(opcion => {
        const o = document.createElement('option');
        o.value = String(opcion.id);
        o.textContent = opcion.etiqueta;
        select.appendChild(o);
    });
}

async function cargarCatalogos(): Promise<void> {
    try {
        const [puntosVenta, tiposMoneda, listasPrecio] = await Promise.all([
            catalogoService.obtenerPuntosVenta(),
            catalogoService.obtenerTiposMoneda(),
            catalogoService.obtenerListasPrecio()
        ]);
        llenarSelect(puntoVentaInput, puntosVenta, 'Seleccionar...');
        llenarSelect(tipoMonedaInput, tiposMoneda, 'Sin especificar');
        llenarSelect(listaPrecioInput, listasPrecio, 'Seleccionar lista...');
    } catch (error) {
        mostrarMensaje('No se pudieron cargar los listados: ' + (error as Error).message, true);
        console.error(error);
    }
}

// ---------------------------------------------------------------- exploradores

function explorarClientes(alSeleccionar: (c: ClienteExploradorDTO) => void): void {
    abrirExplorador<ClienteExploradorDTO>({
        titulo: 'Explorador de clientes',
        filtros: [
            { id: 'numero', etiqueta: 'Nº de cliente', tipo: 'numerico' },
            { id: 'denominacion', etiqueta: 'Nombre / Razón social' },
            { id: 'cuit', etiqueta: 'CUIT / CUIL' },
            { id: 'condicionIva', etiqueta: 'Condición IVA' }
        ],
        columnas: [
            { etiqueta: 'Nº', valor: c => String(c.id) },
            { etiqueta: 'Denominación', valor: c => c.denominacion },
            { etiqueta: 'CUIT/CUIL', valor: c => formatearCuit(c.cuitCuil) },
            { etiqueta: 'Condición IVA', valor: c => c.condicionIva ?? '-' }
        ],
        buscar: (f, p, t) => catalogoService.buscarClientes(f, p, t),
        alSeleccionar
    });
}

let opcionesArticulos: OpcionesArticuloDTO | null = null;

// listaPrecioId: al cargar un ítem solo se ofrecen artículos de la lista elegida; sin lista (filtro del listado) se ven todas
async function explorarArticulos(alSeleccionar: (a: ArticuloExploradorDTO) => void, listaPrecioId: string = ''): Promise<void> {
    try {
        // Siempre se recargan: así las sugerencias reflejan el catálogo actual
        opcionesArticulos = await catalogoService.obtenerOpcionesArticulos();
    } catch (error) {
        mostrarMensaje('No se pudieron cargar las opciones de artículos: ' + (error as Error).message, true);
        return;
    }
    abrirExplorador<ArticuloExploradorDTO>({
        titulo: listaPrecioId
            ? `Artículos de la lista ${listaPrecioInput.selectedOptions[0]?.textContent ?? ''}`
            : 'Explorador de artículos (todas las listas)',
        filtros: [
            { id: 'codigo', etiqueta: 'Código', tipo: 'combo', opciones: opcionesArticulos.codigos },
            { id: 'denominacion', etiqueta: 'Denominación', tipo: 'combo', opciones: opcionesArticulos.denominaciones },
            { id: 'marca', etiqueta: 'Marca', tipo: 'combo', opciones: opcionesArticulos.marcas },
            { id: 'rubro', etiqueta: 'Rubro', tipo: 'combo', opciones: opcionesArticulos.rubros },
            {
                id: 'orden', etiqueta: 'Orden por precio', tipo: 'select',
                opcionesSelect: [
                    { valor: 'asc', etiqueta: 'Ascendente (menor precio primero)' },
                    { valor: 'desc', etiqueta: 'Descendente (mayor precio primero)' }
                ]
            }
        ],
        columnas: [
            { etiqueta: 'Código', valor: a => a.codigo },
            { etiqueta: 'Denominación', valor: a => a.denominacion },
            { etiqueta: 'Marca', valor: a => a.marca ?? '-' },
            { etiqueta: 'Rubro', valor: a => a.rubro ?? '-' },
            { etiqueta: 'Lista', valor: a => a.listaPrecio },
            { etiqueta: 'Precio (IVA incl.)', valor: a => formatearMoneda(a.precioVenta), alineacion: 'text-end' }
        ],
        buscar: (f, p, t) => catalogoService.buscarArticulos({ ...f, listaPrecioId }, p, t),
        alSeleccionar
    });
}

const etiquetaArticulo = (a: ArticuloExploradorDTO) => `${a.codigo} - ${a.denominacion} (${a.listaPrecio})`;
const etiquetaCliente = (c: ClienteExploradorDTO) => `${c.denominacion} (${formatearCuit(c.cuitCuil)})`;

// ---------------------------------------------------------------- importes

// El precio unitario YA incluye IVA (misma fórmula que CalculadoraImportes en el backend).
function calcularSubtotal(d: DetalleFormulario): number {
    const bruto = (d.cantidad || 0) * (d.precioUnitario || 0);
    return Math.round(bruto * (1 - (d.porcentajeBonificacion || 0) / 100) * 100) / 100;
}

function actualizarImporteTotal(): void {
    const total = detalles.reduce((t, d) => t + calcularSubtotal(d), 0);
    importeTotalCell.textContent = formatearMoneda(total);

    // Solo la Factura A muestra el IVA discriminado
    const discrimina = comprobante?.letra === 'A';
    filaNeto.classList.toggle('d-none', !discrimina);
    filaIva.classList.toggle('d-none', !discrimina);
    if (discrimina) {
        let neto = 0;
        detalles.forEach(d => { neto += Math.round(calcularSubtotal(d) / 1.21 * 100) / 100; });
        importeNetoCell.textContent = formatearMoneda(neto);
        importeIvaCell.textContent = formatearMoneda(total - neto);
    }
}

function pintarSwitchEmisor(responsableInscripto: boolean): void {
    switchEmisor.checked = responsableInscripto;
    switchEmisorLabel.textContent = responsableInscripto ? 'Emisor: Responsable Inscripto' : 'Emisor: Monotributista';
}

// El tipo (A/B/C) depende del cliente elegido y del switch de emisor
async function actualizarTipoComprobante(): Promise<void> {
    try {
        comprobante = await catalogoService.obtenerTipoComprobante(clienteId);
        pintarSwitchEmisor(comprobante.emisorResponsableInscripto);
        tipoComprobanteInput.value = `Factura ${comprobante.letra}` +
            (comprobante.letra === 'A' ? ' (IVA discriminado)' : '') +
            ` · Cliente: ${comprobante.condicionIva}`;
    } catch (e) {
        comprobante = null;
        tipoComprobanteInput.value = 'No se pudo determinar';
    }
    actualizarImporteTotal();
}

async function cambiarEmisor(responsableInscripto: boolean): Promise<void> {
    const texto = responsableInscripto ? 'Responsable Inscripto (facturas A/B)' : 'Monotributista (facturas C)';
    if (!confirm(`¿Cambiar el emisor a ${texto}?\nAfecta el tipo de comprobante de las facturas que se envíen a ARCA.`)) {
        pintarSwitchEmisor(!responsableInscripto);
        return;
    }
    try {
        await catalogoService.cambiarEmisor(responsableInscripto);
        await actualizarTipoComprobante();
        mostrarMensaje(`Emisor cambiado a ${texto}.`);
    } catch (error) {
        pintarSwitchEmisor(!responsableInscripto);
        mostrarMensaje('No se pudo cambiar el emisor: ' + (error as Error).message, true);
    }
}

// ---------------------------------------------------------------- detalle

function crearBoton(texto: string, clases: string, onClick: () => void): HTMLButtonElement {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.textContent = texto;
    boton.className = `btn btn-sm ${clases}`;
    boton.addEventListener('click', onClick);
    return boton;
}

function crearInputNumber(valor: number, step: string, min: string, onChange: (v: number) => void): HTMLInputElement {
    const input = document.createElement('input');
    input.type = 'number';
    input.className = 'form-control';
    input.step = step;
    input.min = min;
    input.value = valor.toString();
    input.required = true;
    input.addEventListener('input', () => {
        const n = parseFloat(input.value);
        onChange(isNaN(n) ? 0 : n);
        actualizarImporteTotal();
    });
    return input;
}

function crearFilaDetalle(detalle: DetalleFormulario, indice: number): HTMLTableRowElement {
    const fila = document.createElement('tr');

    const celdaSubtotal = document.createElement('td');
    celdaSubtotal.className = 'text-end align-middle';
    const refrescarSubtotal = () => { celdaSubtotal.textContent = formatearMoneda(calcularSubtotal(detalles[indice])); };
    refrescarSubtotal();

    // Artículo: solo lectura + explorador
    const celdaArticulo = document.createElement('td');
    const grupo = document.createElement('div');
    grupo.className = 'input-group';
    const inputArt = document.createElement('input');
    inputArt.type = 'text';
    inputArt.className = 'form-control';
    inputArt.readOnly = true;
    inputArt.placeholder = 'Seleccione un artículo';
    inputArt.value = detalle.etiqueta;
    inputArt.required = true;
    grupo.appendChild(inputArt);
    grupo.appendChild(crearBoton('\u{1F50D} Buscar…', 'btn-outline-secondary', () => {
        if (!listaPrecioInput.value) {
            mostrarMensaje('Primero elegí la lista de precios (mayorista, minorista, etc.).', true);
            listaPrecioInput.focus();
            return;
        }
        explorarArticulos(art => {
            const d = detalles[indice];
            d.listaPrecioArticuloId = art.listaPrecioArticuloId;
            d.etiqueta = etiquetaArticulo(art);
            d.descripcion = art.denominacion;
            d.precioUnitario = art.precioVenta;
            renderizarDetalles();
            actualizarImporteTotal();
        }, listaPrecioInput.value);
    }));
    celdaArticulo.appendChild(grupo);

    const celdaDescripcion = document.createElement('td');
    const inputDescripcion = document.createElement('input');
    inputDescripcion.type = 'text';
    inputDescripcion.className = 'form-control';
    inputDescripcion.placeholder = 'Descripción del ítem';
    inputDescripcion.value = detalle.descripcion;
    inputDescripcion.required = true;
    inputDescripcion.addEventListener('input', () => { detalles[indice].descripcion = inputDescripcion.value; });
    celdaDescripcion.appendChild(inputDescripcion);

    const celdaCantidad = document.createElement('td');
    celdaCantidad.appendChild(crearInputNumber(detalle.cantidad, '1', '1', v => { detalles[indice].cantidad = v; refrescarSubtotal(); }));
    // El precio lo fija la lista de precios: no se edita
    const celdaPrecio = document.createElement('td');
    celdaPrecio.className = 'text-end align-middle';
    celdaPrecio.textContent = formatearMoneda(detalle.precioUnitario);
    const celdaBonif = document.createElement('td');
    celdaBonif.appendChild(crearInputNumber(detalle.porcentajeBonificacion, '0.01', '0', v => { detalles[indice].porcentajeBonificacion = v; refrescarSubtotal(); }));

    const celdaAcciones = document.createElement('td');
    celdaAcciones.className = 'text-center align-middle';
    celdaAcciones.appendChild(crearBoton('Quitar', 'btn-outline-danger', () => {
        detalles.splice(indice, 1);
        renderizarDetalles();
        actualizarImporteTotal();
    }));

    [celdaArticulo, celdaDescripcion, celdaCantidad, celdaPrecio, celdaBonif, celdaSubtotal, celdaAcciones]
        .forEach(c => fila.appendChild(c));
    return fila;
}

function renderizarDetalles(): void {
    detallesTbody.innerHTML = '';
    detalles.forEach((d, i) => detallesTbody.appendChild(crearFilaDetalle(d, i)));
}

function crearDetalleVacio(): DetalleFormulario {
    return { listaPrecioArticuloId: 0, etiqueta: '', descripcion: '', cantidad: 1, precioUnitario: 0, porcentajeBonificacion: 0 };
}

// ---------------------------------------------------------------- formulario

function reiniciarFormularioAlta(): void {
    facturaEditandoId = null;
    facturaForm.reset();
    clienteId = null;
    clienteTexto.value = '';
    aplicarConsumidorFinal(false);
    tituloFormulario.textContent = 'Nueva Factura de Venta';
    btnGuardar.textContent = 'Guardar Factura';
    btnCancelarEdicion.classList.add('d-none');
    fechaEmisionInput.value = fechaLocalHoy();
    listaPrecioActual = listaPrecioInput.value;
    detalles = [crearDetalleVacio()];
    renderizarDetalles();
    void actualizarTipoComprobante();
}

async function editarFactura(id: number): Promise<void> {
    try {
        const f = await facturaService.obtenerFactura(id);
        facturaEditandoId = f.id;
        tituloFormulario.textContent = `Editando factura (borrador #${f.id})`;
        btnGuardar.textContent = 'Guardar cambios';
        btnCancelarEdicion.classList.remove('d-none');
        fechaEmisionInput.value = f.fechaEmision.substring(0, 10);
        puntoVentaInput.value = f.puntoVentaId ? String(f.puntoVentaId) : '';
        tipoMonedaInput.value = f.tipoMonedaId ? String(f.tipoMonedaId) : '';
        listaPrecioInput.value = f.listaPrecioId ? String(f.listaPrecioId) : '';
        listaPrecioActual = listaPrecioInput.value;
        observacionesInput.value = f.observaciones ?? '';
        clienteId = f.clienteId;
        clienteTexto.value = f.clienteEtiqueta ?? '';
        aplicarConsumidorFinal(f.clienteId === null);
        detalles = f.detalles.map(d => ({
            listaPrecioArticuloId: d.listaPrecioArticuloId,
            etiqueta: d.etiqueta,
            descripcion: d.descripcion,
            cantidad: d.cantidad,
            precioUnitario: d.precioUnitario,
            porcentajeBonificacion: d.porcentajeBonificacion
        }));
        renderizarDetalles();
        await actualizarTipoComprobante();
        facturaForm.scrollIntoView({ behavior: 'smooth' });
    } catch (error) {
        mostrarMensaje('No se pudo cargar la factura: ' + (error as Error).message, true);
    }
}

// Consumidor Final: sin cliente, el selector queda deshabilitado (gris)
function aplicarConsumidorFinal(activo: boolean): void {
    switchConsumidorFinal.checked = activo;
    if (activo) {
        clienteId = null;
        clienteTexto.value = '';
    }
    clienteTexto.disabled = activo;
    btnCliente.disabled = activo;
    btnClienteX.disabled = activo;
    clienteTexto.placeholder = activo ? 'Consumidor Final' : 'Consumidor Final (sin cliente)';
}

// ---------------------------------------------------------------- listado

function escapar(texto: string | null | undefined): string {
    return (texto ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function claseEstado(estado: string): string {
    switch (estado) {
        case 'INFORMADA': return 'bg-success';
        case 'BORRADOR': return 'bg-secondary';
        case 'RECHAZADA': return 'bg-danger';
        case 'ANULADA': return 'bg-dark';
        case 'ELIMINADA': return 'bg-eliminada';
        default: return 'bg-info';
    }
}

async function ejecutar(accion: () => Promise<unknown>, exito: string, errorPrefijo: string): Promise<void> {
    try {
        await accion();
        mostrarMensaje(exito);
    } catch (error) {
        mostrarMensaje(`${errorPrefijo}: ${(error as Error).message}`, true);
        console.error(error);
    }
    await cargarFacturas(paginaActual);
}

function textoArticulo(f: FacturaReporteDTO): string {
    if (!f.articulo) return '-';
    return f.itemsAdicionales > 0 ? `${f.articulo} (+${f.itemsAdicionales} más)` : f.articulo;
}

function formatearCantidad(valor: number | null | undefined): string {
    return (valor ?? 0).toLocaleString('es-AR', { maximumFractionDigits: 2 });
}

async function verDetalle(id: number): Promise<void> {
    const cuerpo = el<HTMLElement>('detalle-cuerpo');
    cuerpo.innerHTML = 'Cargando...';
    bootstrap.Modal.getOrCreateInstance(el('modal-detalle')).show();
    try {
        const f = await facturaService.obtenerFactura(id);
        const numero = f.numeroFactura !== null ? `Nº ${f.numeroFactura}` : `borrador #${f.id}`;
        el<HTMLElement>('detalle-titulo').textContent = `Factura ${letraDe(f.tipoComprobante)} · ${numero}`;
        const filas = f.detalles.map(d => `
            <tr>
                <td>${escapar(d.etiqueta)}</td>
                <td>${escapar(d.descripcion)}</td>
                <td class="text-end">${formatearCantidad(d.cantidad)}</td>
                <td class="text-end">${formatearMoneda(d.precioUnitario)}</td>
                <td class="text-end">${formatearMoneda(d.porcentajeBonificacion)}%</td>
                <td class="text-end">${formatearMoneda(d.importeSubtotal)}</td>
            </tr>`).join('');
        const discrimina = f.tipoComprobante === 1;
        cuerpo.innerHTML = `
            <dl class="row mb-3">
                <dt class="col-sm-3">Estado</dt><dd class="col-sm-9"><span class="badge ${claseEstado(f.estado)}">${f.estado}</span></dd>
                <dt class="col-sm-3">Fecha de emisión</dt><dd class="col-sm-9">${formatearFecha(f.fechaEmision)}</dd>
                <dt class="col-sm-3">Cliente</dt><dd class="col-sm-9">${escapar(f.clienteEtiqueta) || 'Consumidor Final'}</dd>
                <dt class="col-sm-3">Condición IVA</dt><dd class="col-sm-9">${escapar(f.condicionIva) || '-'}</dd>
                <dt class="col-sm-3">Lista de precios</dt><dd class="col-sm-9">${escapar(f.listaPrecio) || '-'}</dd>
                <dt class="col-sm-3">Punto de venta</dt><dd class="col-sm-9">${escapar(f.puntoVentaDescripcion) || '-'}</dd>
                ${f.cae ? `<dt class="col-sm-3">CAE</dt><dd class="col-sm-9">${escapar(f.cae)}</dd>` : ''}
                ${f.motivoRechazo ? `<dt class="col-sm-3">Motivo de rechazo</dt><dd class="col-sm-9 text-danger">${escapar(f.motivoRechazo)}</dd>` : ''}
                <dt class="col-sm-3">Observaciones</dt><dd class="col-sm-9" style="white-space: pre-wrap;">${escapar(f.observaciones) || '-'}</dd>
            </dl>
            <div class="table-responsive">
                <table class="table table-sm table-striped">
                    <thead class="table-light"><tr>
                        <th>Artículo (lista)</th><th>Descripción</th><th class="text-end">Cantidad</th>
                        <th class="text-end">Precio unit. (IVA incl.)</th><th class="text-end">Bonif.</th><th class="text-end">Subtotal</th>
                    </tr></thead>
                    <tbody>${filas}</tbody>
                    <tfoot>
                        ${discrimina ? `<tr><td colspan="5" class="text-end">Importe Neto Gravado</td><td class="text-end">${formatearMoneda(f.importeNeto)}</td></tr>
                        <tr><td colspan="5" class="text-end">IVA 21%</td><td class="text-end">${formatearMoneda(f.importeIva)}</td></tr>` : ''}
                        <tr class="fw-bold"><td colspan="5" class="text-end">Importe Total</td><td class="text-end">${formatearMoneda(f.importeTotal)}</td></tr>
                    </tfoot>
                </table>
            </div>`;
    } catch (error) {
        cuerpo.innerHTML = `<div class="text-danger">No se pudo cargar el detalle: ${escapar((error as Error).message)}</div>`;
    }
}

function renderizarFacturas(facturas: FacturaReporteDTO[]): void {
    tbody.innerHTML = '';

    if (facturas.length === 0) {
        tbody.innerHTML = '<tr><td colspan="12" class="text-center">No se encontraron facturas.</td></tr>';
        return;
    }

    facturas.forEach(factura => {
        const tieneNumero = factura.numeroFactura !== null && factura.numeroFactura !== undefined;
        const fila = document.createElement('tr');
        const eliminada = factura.estado === 'ELIMINADA';
        if (eliminada) fila.className = 'fila-eliminada';

        // El motivo de rechazo se ve al pasar el mouse sobre el cartel RECHAZADA
        const tooltip = factura.estado === 'RECHAZADA'
            ? ` data-bs-toggle="tooltip" data-bs-title="${escapar(factura.motivoRechazo || 'Sin motivo informado por ARCA')}"`
            : '';
        fila.innerHTML = `
            <td>${tieneNumero ? factura.numeroFactura : '-'}</td>
            <td>${letraDe(factura.tipoComprobante)}</td>
            <td>${formatearFecha(factura.fechaEmision)}</td>
            <td>${escapar(factura.clienteDenominacion) || '-'}</td>
            <td>${escapar(factura.condicionIva) || '-'}</td>
            <td>${escapar(textoArticulo(factura))}</td>
            <td class="text-end">${formatearCantidad(factura.cantidadTotal)}</td>
            <td>${escapar(factura.puntoVentaDescripcion) || '-'}</td>
            <td class="text-end">${formatearMoneda(factura.importeTotal)}</td>
            <td class="text-center"><span class="badge ${claseEstado(factura.estado)}"${tooltip}>${factura.estado}</span></td>
            <td class="text-center"></td>
            <td class="text-center"></td>
        `;
        const badgeTooltip = fila.querySelector('[data-bs-toggle="tooltip"]');
        if (badgeTooltip) new bootstrap.Tooltip(badgeTooltip);

        const grupo = document.createElement('div');
        grupo.className = 'btn-group';
        const anulada = factura.estado === 'ANULADA';
        const etiqueta = tieneNumero ? `Nº ${factura.numeroFactura}` : `borrador #${factura.id}`;

        if (!tieneNumero && !anulada && !eliminada) {
            // Sin número = no informada a ARCA: se puede editar, informar y eliminar
            grupo.appendChild(crearBoton('Editar', 'btn-outline-primary', () => { void editarFactura(factura.id); }));
            grupo.appendChild(crearBoton('Enviar a ARCA', 'btn-outline-success', () => {
                if (!confirm(`¿Enviar a ARCA la factura ${etiqueta}? Se le asignará número y CAE.`)) return;
                void ejecutar(() => facturaService.informarFactura(factura.id),
                    `Factura ${etiqueta} enviada a ARCA (revise el estado resultante).`, 'Error al informar a ARCA');
            }));
            grupo.appendChild(crearBoton('Eliminar', 'btn-outline-danger', () => {
                if (!confirm(`¿Eliminar la factura ${etiqueta}? Dejará de mostrarse en el listado.`)) return;
                void ejecutar(() => facturaService.eliminarFactura(factura.id),
                    `Factura ${etiqueta} eliminada.`, 'Error al eliminar la factura');
            }));
        }
        if (tieneNumero && !anulada && !eliminada) {
            // Con número no se puede editar ni eliminar, solo anular
            grupo.appendChild(crearBoton('Anular', 'btn-outline-warning', () => {
                if (!confirm(`¿Anular la factura ${etiqueta}? Esta acción no se puede deshacer.`)) return;
                void ejecutar(() => facturaService.anularFactura(factura.id),
                    `Factura ${etiqueta} anulada.`, 'Error al anular la factura');
            }));
        }
        fila.cells[10].appendChild(grupo);
        fila.cells[11].appendChild(crearBoton('Ver', 'btn-outline-secondary', () => { void verDetalle(factura.id); }));
        if (!eliminada) {
            const btnPdf = crearBoton('PDF', 'btn-outline-danger ms-1', () => {
                window.open(facturaService.urlPdf(factura.id), '_blank');
            });
            btnPdf.title = tieneNumero ? 'Generar PDF con CAE' : 'PDF del borrador (sin CAE)';
            fila.cells[11].appendChild(btnPdf);
        }
        tbody.appendChild(fila);
    });
}

function leerFiltros(): FacturaFiltrosDTO {
    return {
        fechaDesde: el<HTMLInputElement>('filtro-fecha-desde').value,
        fechaHasta: el<HTMLInputElement>('filtro-fecha-hasta').value,
        estado: el<HTMLSelectElement>('filtro-estado').value,
        montoMinimo: el<HTMLInputElement>('filtro-monto-minimo').value,
        clienteId: filtroClienteId !== null ? String(filtroClienteId) : '',
        articuloId: filtroArticuloId !== null ? String(filtroArticuloId) : '',
        numero: el<HTMLInputElement>('filtro-numero').value.replace(/\D/g, ''),
        informada: el<HTMLSelectElement>('filtro-informada').value
    };
}

async function cargarFacturas(pagina: number = 0): Promise<void> {
    const scrollY = window.scrollY; // al recargar la lista no se vuelve al inicio
    try {
        const res = await facturaService.obtenerFacturas({ ...leerFiltros(), pagina, tamanio: TAMANIO_PAGINA });
        // Si quedó vacía la última página (p. ej. tras eliminar), retrocede
        if (res.contenido.length === 0 && res.pagina > 0 && res.totalPaginas > 0) {
            return cargarFacturas(res.totalPaginas - 1);
        }
        paginaActual = res.pagina;
        renderizarFacturas(res.contenido);
        renderizarPaginacion(paginacionEl, res, p => { void cargarFacturas(p); });
        window.scrollTo(0, scrollY);
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="12" class="text-center text-danger">Error al cargar facturas: ${(error as Error).message}</td></tr>`;
        paginacionEl.innerHTML = '';
        console.error(error);
    }
}

// ---------------------------------------------------------------- eventos

filtrosForm.addEventListener('submit', (e) => { e.preventDefault(); ocultarMensaje(); void cargarFacturas(0); });

btnLimpiar.addEventListener('click', () => {
    filtrosForm.reset();
    filtroClienteId = null;
    filtroArticuloId = null;
    el<HTMLInputElement>('filtro-cliente-texto').value = '';
    el<HTMLInputElement>('filtro-articulo-texto').value = '';
    ocultarMensaje();
    void cargarFacturas(0);
});

el('btn-filtro-cliente').addEventListener('click', () => explorarClientes(c => {
    filtroClienteId = c.id;
    el<HTMLInputElement>('filtro-cliente-texto').value = etiquetaCliente(c);
}));
el('btn-filtro-cliente-x').addEventListener('click', () => {
    filtroClienteId = null;
    el<HTMLInputElement>('filtro-cliente-texto').value = '';
});
el('btn-filtro-articulo').addEventListener('click', () => explorarArticulos(a => {
    filtroArticuloId = a.articuloId;
    el<HTMLInputElement>('filtro-articulo-texto').value = etiquetaArticulo(a);
}));
el('btn-filtro-articulo-x').addEventListener('click', () => {
    filtroArticuloId = null;
    el<HTMLInputElement>('filtro-articulo-texto').value = '';
});

switchConsumidorFinal.addEventListener('change', () => {
    aplicarConsumidorFinal(switchConsumidorFinal.checked);
    void actualizarTipoComprobante();
});
btnCliente.addEventListener('click', () => explorarClientes(c => {
    clienteId = c.id;
    clienteTexto.value = etiquetaCliente(c);
    void actualizarTipoComprobante();
}));
el('btn-cliente-x').addEventListener('click', () => {
    clienteId = null;
    clienteTexto.value = '';
    void actualizarTipoComprobante();
});

// Cambiar de lista invalida los ítems cargados (sus artículos y precios son de la lista anterior)
listaPrecioInput.addEventListener('change', () => {
    const hayItems = detalles.some(d => d.listaPrecioArticuloId);
    if (hayItems && !confirm('Al cambiar la lista de precios se quitan los ítems cargados. ¿Continuar?')) {
        listaPrecioInput.value = listaPrecioActual;
        return;
    }
    listaPrecioActual = listaPrecioInput.value;
    if (hayItems) {
        detalles = [crearDetalleVacio()];
        renderizarDetalles();
        actualizarImporteTotal();
    }
});

el('btn-exportar-pdf').addEventListener('click', () => {
    window.open(facturaService.urlExportar('pdf', leerFiltros()), '_blank');
});
el('btn-exportar-excel').addEventListener('click', () => {
    window.location.href = facturaService.urlExportar('excel', leerFiltros());
});

switchEmisor.addEventListener('change', () => { void cambiarEmisor(switchEmisor.checked); });
el('btn-actualizar').addEventListener('click', () => { void cargarFacturas(paginaActual); });
btnCancelarEdicion.addEventListener('click', () => { ocultarMensaje(); reiniciarFormularioAlta(); });

el('btn-agregar-detalle').addEventListener('click', () => {
    detalles.push(crearDetalleVacio());
    renderizarDetalles();
    actualizarImporteTotal();
});

facturaForm.addEventListener('submit', async (evento) => {
    evento.preventDefault();

    if (detalles.length === 0) {
        mostrarMensaje('Debe agregar al menos un detalle a la factura.', true);
        return;
    }
    if (detalles.some(d => !d.listaPrecioArticuloId)) {
        mostrarMensaje('Cada ítem debe tener un artículo seleccionado (explorador).', true);
        return;
    }

    const factura: FacturaRequestDTO = {
        fechaEmision: fechaEmisionInput.value,
        puntoVentaId: parseInt(puntoVentaInput.value, 10),
        listaPrecioId: parseInt(listaPrecioInput.value, 10),
        detalles: detalles.map(d => ({
            listaPrecioArticuloId: d.listaPrecioArticuloId,
            descripcion: d.descripcion.trim(),
            cantidad: d.cantidad,
            porcentajeBonificacion: d.porcentajeBonificacion
        }))
    };
    if (clienteId !== null) factura.clienteId = clienteId;
    if (tipoMonedaInput.value.trim() !== '') factura.tipoMonedaId = parseInt(tipoMonedaInput.value, 10);
    if (observacionesInput.value.trim() !== '') factura.observaciones = observacionesInput.value.trim();

    try {
        if (facturaEditandoId !== null) {
            await facturaService.editarFactura(facturaEditandoId, factura);
            mostrarMensaje('Factura actualizada. Sigue como borrador hasta enviarla a ARCA.');
        } else {
            await facturaService.crearFactura(factura);
            mostrarMensaje('Factura guardada como borrador. Use "Enviar a ARCA" para informarla.');
        }
        reiniciarFormularioAlta();
        await cargarFacturas(0);
    } catch (error) {
        mostrarMensaje('Error al guardar la factura: ' + (error as Error).message, true);
        console.error(error);
    }
});

// Inicialización de la vista
(async () => {
    await cargarCatalogos();
    reiniciarFormularioAlta();
    await cargarFacturas(0);
})();
