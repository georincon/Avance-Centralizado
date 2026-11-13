// Código propio de este fork. En un celular, muestra el botón «Compartir con mi wallet» antes del QR: entrega la oferta de credencial
// directo a la wallet instalada en el mismo teléfono. En Android se usa un enlace `intent://` con respaldo: si ninguna app atiende la
// oferta, se abre la página de descarga de la wallet del ecosistema. En escritorio no se ve: ahí se escanea el QR con otro dispositivo.
(function () {
    var ua = navigator.userAgent;
    if (!/Android|iPhone|iPad|iPod/i.test(ua)) return;
    document.querySelectorAll('.solo-movil').forEach(function (e) { e.hidden = false; });
    var titulo = document.getElementById('titulo-qr');
    if (titulo) titulo.textContent = 'O escanéalo desde otro dispositivo';

    var boton = document.getElementById('abrir-wallet');
    var oferta = boton && boton.getAttribute('data-offer');
    if (!boton || !oferta || !/Android/i.test(ua)) return;
    var m = /^([a-z][a-z0-9+.-]*):\/\/(.*)$/i.exec(oferta);
    if (!m) return;
    var descarga = location.origin + '/login';
    boton.href = 'intent://' + m[2] + '#Intent;scheme=' + m[1] + ';S.browser_fallback_url=' + encodeURIComponent(descarga) + ';end';
})();
