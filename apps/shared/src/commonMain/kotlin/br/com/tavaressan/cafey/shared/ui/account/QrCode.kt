package br.com.tavaressan.cafey.shared.ui.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import qrcode.raw.ErrorCorrectionLevel
import qrcode.raw.QRCodeProcessor

/**
 * QR code desenhado num Canvas, sempre preto no branco (com a margem de 4 módulos exigida pela norma),
 * mesmo no tema escuro: leitores de QR falham com cores invertidas.
 */
@Composable
fun QrCode(conteudo: String, descricao: String, modifier: Modifier = Modifier, tamanho: Dp = 200.dp) {
    // Matriz [linha][coluna] de módulos; `dark` = módulo preto.
    val matriz = remember(conteudo) {
        QRCodeProcessor(conteudo, ErrorCorrectionLevel.MEDIUM).encode().map { linha -> linha.map { it.dark } }
    }
    Canvas(modifier.size(tamanho).semantics { contentDescription = descricao }) {
        val margem = 4
        val modulos = matriz.size + margem * 2
        val lado = size.minDimension / modulos
        drawRect(Color.White)
        for (y in matriz.indices) {
            for (x in matriz[y].indices) {
                if (matriz[y][x]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset((x + margem) * lado, (y + margem) * lado),
                        // +0,5 px evita frestas entre módulos vizinhos por arredondamento.
                        size = Size(lado + 0.5f, lado + 0.5f),
                    )
                }
            }
        }
    }
}
