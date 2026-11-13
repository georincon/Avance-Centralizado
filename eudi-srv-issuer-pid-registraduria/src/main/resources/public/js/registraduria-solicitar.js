// Código propio de este fork (emulador de la Registraduría). Obliga a elegir al menos un formato del PID
// (mDoc o SD-JWT VC) antes de enviar el formulario; el servidor lo vuelve a comprobar.
(function () {
    var boxes = Array.prototype.slice.call(document.querySelectorAll('input[name="formats"]'));
    if (boxes.length === 0) return;
    function check() {
        var any = boxes.some(function (b) { return b.checked; });
        boxes[0].setCustomValidity(any ? '' : 'Elige al menos un formato: mDoc o SD-JWT VC.');
    }
    boxes.forEach(function (b) { b.addEventListener('change', check); });
    check();
})();

// Selfie con la cámara del navegador (getUserMedia); si no hay cámara o permiso queda el selector de archivo.
(function () {
    var start = document.getElementById('selfie-start');
    if (!start) return;
    var video = document.getElementById('selfie-video');
    var canvas = document.getElementById('selfie-canvas');
    var preview = document.getElementById('selfie-preview');
    var shot = document.getElementById('selfie-shot');
    var retry = document.getElementById('selfie-retry');
    var file = document.getElementById('selfie-file');
    var stream = null;
    function stop() { if (stream) { stream.getTracks().forEach(function (t) { t.stop(); }); stream = null; } }
    function open() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { start.disabled = true; return; }
        navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false }).then(function (s) {
            stream = s; video.srcObject = s; video.hidden = false; preview.hidden = true;
            shot.hidden = false; retry.hidden = true; start.hidden = true;
        }).catch(function () { start.textContent = 'No se pudo abrir la cámara'; start.disabled = true; });
    }
    start.addEventListener('click', open);
    retry.addEventListener('click', function () { file.value = ''; preview.hidden = true; retry.hidden = true; open(); });
    shot.addEventListener('click', function () {
        canvas.width = video.videoWidth; canvas.height = video.videoHeight;
        canvas.getContext('2d').drawImage(video, 0, 0);
        canvas.toBlob(function (blob) {
            var f = new File([blob], 'selfie.jpg', { type: 'image/jpeg' });
            var dt = new DataTransfer(); dt.items.add(f); file.files = dt.files;
            preview.src = canvas.toDataURL('image/jpeg', 0.85); preview.hidden = false;
            video.hidden = true; shot.hidden = true; retry.hidden = false; stop();
        }, 'image/jpeg', 0.92);
    });

    // Foto elegida con el selector de archivo (o la cámara del teléfono): se muestra la vista previa.
    // Se usa un data: URL, porque la política de seguridad de la página (img-src) no admite blob:.
    file.addEventListener('change', function () {
        var f = file.files && file.files[0];
        if (!f) { preview.hidden = true; return; }
        var lector = new FileReader();
        lector.onload = function () { preview.src = lector.result; preview.hidden = false; video.hidden = true; shot.hidden = true; retry.hidden = false; stop(); };
        lector.readAsDataURL(f);
    });
})();
