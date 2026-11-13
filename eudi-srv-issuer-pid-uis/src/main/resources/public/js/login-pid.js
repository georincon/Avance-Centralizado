// Consulta cada 2 s si la wallet ya presentó el PID; al verificarse, abre la sesión y sigue con los programas.
(function () {
    var box = document.getElementById('pid-estado');
    if (!box) return;
    var url = box.getAttribute('data-estado-url');
    var timer = setInterval(function () {
        fetch(url, { headers: { Accept: 'application/json' } }).then(function (r) { return r.json(); }).then(function (d) {
            if (d.estado === 'ok') { clearInterval(timer); window.location = d.siguiente; }
            else if (d.estado === 'error') {
                clearInterval(timer);
                box.className = 'alert alert-danger';
                box.textContent = 'No se pudo verificar: ' + (d.motivo || 'inténtalo de nuevo') + '.';
            }
        }).catch(function () { /* se reintenta en el siguiente ciclo */ });
    }, 2000);
})();
