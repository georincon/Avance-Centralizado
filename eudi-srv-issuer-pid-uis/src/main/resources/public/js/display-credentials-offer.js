document.getElementById('copy-offer-uri').addEventListener('click', function () {
    var button = this;
    navigator.clipboard
        .writeText(document.querySelector('.credentials-offer-uri').textContent)
        .then(function () {
            button.textContent = 'Copiado!';
        });
});
