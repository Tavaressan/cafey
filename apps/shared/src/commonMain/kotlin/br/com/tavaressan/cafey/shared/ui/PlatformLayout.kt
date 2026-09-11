package br.com.tavaressan.cafey.shared.ui

import androidx.compose.ui.unit.Dp

/**
 * Largura máxima do conteúdo, por plataforma. As telas foram desenhadas na largura mobile, então em
 * viewports largas (Web e Desktop) o conteúdo é centralizado com esse limite em vez de esticar —
 * mesma regra do `.shell` do protótipo HTML (`docs/docs_interface/prototype/assets/cafey.css`).
 *
 * No Android o valor é [Dp.Unspecified] (sem limite): o aparelho já é a viewport mobile, e limitar a
 * 390dp criaria faixas laterais em telefones mais largos que isso.
 */
expect val maxContentWidth: Dp
