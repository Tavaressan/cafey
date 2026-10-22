package br.com.tavaressan.cafey.exception

import br.com.tavaressan.cafey.controller.TestController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(TestController(), MissingResourceTestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `should handle NoResourceFoundException as a 404 ProblemDetail`() {
        mockMvc.perform(get("/test/missing-resource"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Not Found"))
    }

    @Test
    fun `should handle ResourceNotFoundException`() {
        mockMvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Resource Not Found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Test resource not found"))
    }

    @Test
    fun `should handle BadCredentialsException`() {
        mockMvc.perform(get("/test/unauthorized"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Test unauthorized"))
    }

    @Test
    fun `should handle MethodArgumentNotValidException`() {
        mockMvc.perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Validation failed: name: must not be blank"))
    }

    @Test
    fun `should handle general Exception`() {
        mockMvc.perform(get("/test/internal"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
    }

    // Issue #252 — o 500 genérico precisa deixar a exceção e o stack trace no log.
    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `should log general Exception with stack trace`(output: CapturedOutput) {
        mockMvc.perform(get("/test/internal"))
            .andExpect(status().isInternalServerError)

        assertThat(output.out)
            .contains("ERROR")
            .contains("Erro não tratado em uri=/test/internal")
            .contains("java.lang.RuntimeException: Test internal error")
            .contains("at br.com.tavaressan.cafey.controller.TestController.internal")
    }
}

@RestController
private class MissingResourceTestController {
    @GetMapping("/test/missing-resource")
    fun missingResource(): Nothing = throw NoResourceFoundException(
        HttpMethod.GET,
        "/test/missing-resource",
        "/test/missing-resource",
    )
}
