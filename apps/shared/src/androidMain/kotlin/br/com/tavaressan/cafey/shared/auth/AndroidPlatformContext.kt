package br.com.tavaressan.cafey.shared.auth

import android.content.Context

/**
 * Guarda o `Context` de aplicação para as classes `actual` do Android que precisam dele
 * (`TokenStorage`) sem exigir um framework de injeção de dependência inteiro para um projeto
 * deste porte. `MainActivity.onCreate` chama [init] antes de qualquer uso da camada `shared`.
 */
object AndroidPlatformContext {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun require(): Context = checkNotNull(appContext) {
        "AndroidPlatformContext.init(context) precisa ser chamado antes de usar a camada shared."
    }
}
