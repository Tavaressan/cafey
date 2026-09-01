package br.com.cafey.controller

import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.exception.ResourceNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class TestDto(@field:NotBlank(message = "must not be blank") val name: String?)

@RestController
class TestController {
    @GetMapping("/test/not-found")
    fun notFound(): String {
        throw ResourceNotFoundException("Test resource not found")
    }

    @GetMapping("/test/unauthorized")
    fun unauthorized(): String {
        throw BadCredentialsException("Test unauthorized")
    }

    @PostMapping("/test/validation")
    fun validation(@Valid @RequestBody dto: TestDto): String {
        return "OK"
    }
    
    @GetMapping("/test/internal")
    fun internal(): String {
        throw RuntimeException("Test internal error")
    }
}
