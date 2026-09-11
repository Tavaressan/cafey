package br.com.tavaressan.cafey.shared.network

/**
 * URL padrão do backend em desenvolvimento local, por plataforma — o emulador Android não enxerga
 * `localhost` do host como o próprio `localhost`; precisa do alias `10.0.2.2` (documentado pelo
 * próprio Android Studio). Desktop e Web rodam no mesmo host do backend em dev, então `localhost`
 * funciona direto. Produção deve sobrescrever via configuração de build, não editando isto aqui.
 */
expect val platformDefaultBaseUrl: String
