document.getElementById('toggle-clave').addEventListener('click', function () {
    var input = document.getElementById('clave');
    var open = document.getElementById('icon-eye-open');
    var closed = document.getElementById('icon-eye-closed');
    var isPassword = input.type === 'password';
    input.type = isPassword ? 'text' : 'password';
    open.style.display = isPassword ? 'none' : 'block';
    closed.style.display = isPassword ? 'block' : 'none';
});
