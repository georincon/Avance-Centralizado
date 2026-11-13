// Código propio de este fork. Si la persona navega desde un celular, muestra el botón «Abrir en mi wallet»
// (el enlace de la oferta, que abre la wallet instalada en el mismo teléfono). En escritorio no se ve:
// ahí se escanea el QR con otro dispositivo.
(function () {
    if (!/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) return;
    document.querySelectorAll('.solo-movil').forEach(function (e) { e.hidden = false; });
})();
