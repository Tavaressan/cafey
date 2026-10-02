package br.com.tavaressan.cafey.mail

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockitoExtension::class)
class ProdEmailSenderServiceTest {

    @Mock
    private lateinit var mailSender: JavaMailSender

    private val properties = EmailProperties(
        from = "Cafey <nao-responda@cafey.example>",
        resetPasswordUrl = "https://cafey.example/redefinir-senha.html"
    )

    @Test
    fun `envia o e-mail de recuperacao com remetente, destinatario e link com o token`() {
        val service = ProdEmailSenderService(mailSender, properties)

        service.enviarEmailRecuperacaoSenha("maria@example.com", "abc123token")

        val captor = ArgumentCaptor.forClass(SimpleMailMessage::class.java)
        // capture() devolve null; o fallback evita NPE no parâmetro não anulável do Kotlin.
        verify(mailSender).send(captor.capture() ?: SimpleMailMessage())
        val message = captor.value
        assertEquals("Cafey <nao-responda@cafey.example>", message.from)
        assertEquals(listOf("maria@example.com"), message.to?.toList())
        assertNotNull(message.subject)
        assertTrue(
            message.text!!.contains("https://cafey.example/redefinir-senha.html?token=abc123token"),
            "o corpo deve conter o link de redefinição com o token"
        )
    }

    @Test
    fun `propaga a falha de envio para que o AuthService a registre em log`() {
        val service = ProdEmailSenderService(mailSender, properties)
        doThrow(MailSendException("SMTP indisponível")).`when`(mailSender).send(any(SimpleMailMessage::class.java))

        assertThrows<MailSendException> {
            service.enviarEmailRecuperacaoSenha("maria@example.com", "abc123token")
        }
    }
}
