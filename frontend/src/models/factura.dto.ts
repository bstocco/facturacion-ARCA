export interface FacturaDetalleRequestDTO {
    listaPrecioArticuloId: number;
    descripcion: string;
    cantidad: number;
    porcentajeBonificacion: number;
}

// Solo para el estado en memoria del formulario: etiqueta visible y precio de la lista (el backend no lo recibe).
export interface DetalleFormulario extends FacturaDetalleRequestDTO {
    etiqueta: string;
    precioUnitario: number;
}

export interface FacturaRequestDTO {
    fechaEmision: string;
    clienteId?: number;
    puntoVentaId: number;
    tipoMonedaId?: number;
    listaPrecioId: number;
    observaciones?: string;
    detalles: FacturaDetalleRequestDTO[];
}

export interface FacturaReporteDTO {
    id: number;
    numeroFactura: number | null;
    fechaEmision: string;
    clienteDenominacion: string;
    condicionIva: string;
    puntoVentaDescripcion: string;
    importeTotal: number;
    cantidadItems: number;
    estado: string;
    tipoComprobante: number | null;
    motivoRechazo: string | null;
    articulo: string | null;
    itemsAdicionales: number;
    cantidadTotal: number;
}

export interface FacturaItemResponseDTO {
    listaPrecioArticuloId: number;
    etiqueta: string;
    descripcion: string;
    cantidad: number;
    precioUnitario: number;
    porcentajeBonificacion: number;
    importeNeto: number;
    importeIva: number;
    importeSubtotal: number;
}

export interface FacturaDetalleResponseDTO {
    id: number;
    numeroFactura: number | null;
    fechaEmision: string;
    clienteId: number | null;
    clienteEtiqueta: string | null;
    condicionIva: string | null;
    puntoVentaId: number | null;
    puntoVentaDescripcion: string | null;
    cae: string | null;
    motivoRechazo: string | null;
    tipoMonedaId: number | null;
    estado: string;
    observaciones: string | null;
    tipoComprobante: number | null;
    importeNeto: number;
    importeIva: number;
    importeTotal: number;
    listaPrecioId: number | null;
    listaPrecio: string | null;
    detalles: FacturaItemResponseDTO[];
}

export interface FacturaFiltrosDTO {
    fechaDesde?: string;
    fechaHasta?: string;
    estado?: string;
    montoMinimo?: string;
    clienteId?: string;
    articuloId?: string;
    numero?: string;
    informada?: string;
    pagina?: number;
    tamanio?: number;
}

export interface PaginaDTO<T> {
    contenido: T[];
    pagina: number;
    tamanio: number;
    totalElementos: number;
    totalPaginas: number;
}

export interface OpcionDTO {
    id: number;
    etiqueta: string;
}

export interface ClienteExploradorDTO {
    id: number;
    denominacion: string;
    cuitCuil: string;
    condicionIva: string | null;
}

export interface ArticuloExploradorDTO {
    listaPrecioArticuloId: number;
    articuloId: number;
    codigo: string;
    denominacion: string;
    marca: string | null;
    rubro: string | null;
    listaPrecio: string;
    precioVenta: number;
}

export interface ComprobanteTipoDTO {
    tipo: number;
    letra: string;
    discriminaIva: boolean;
    emisorResponsableInscripto: boolean;
    condicionIva: string;
}

export interface OpcionesArticuloDTO {
    codigos: string[];
    denominaciones: string[];
    marcas: string[];
    rubros: string[];
}
