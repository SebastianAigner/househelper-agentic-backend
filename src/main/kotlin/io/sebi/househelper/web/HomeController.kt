package io.sebi.househelper.web

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HomeController {

    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    fun home(): String = ClassPathResource("static/index.html").inputStream.bufferedReader().use { it.readText() }

    @GetMapping("/ui", produces = [MediaType.TEXT_HTML_VALUE])
    fun ui(): String = ClassPathResource("static/ui/index.html").inputStream.bufferedReader().use { it.readText() }
}
