package org.de013.notificationservice.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "spring.jackson2.default-property-inclusion=non_null")
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {HttpJsonConfig.class, HttpJsonConfigTest.Controller.class})
@ImportAutoConfiguration(Jackson2AutoConfiguration.class)
@SuppressWarnings("removal")
class HttpJsonConfigTest {
    @RestController
    static class Controller {
        @GetMapping("/json-check") Fixture fixture() { return new Fixture("value", null); }
    }

    record Fixture(@JsonProperty("wire_name") String name, String omitted) { }

    @Autowired MockMvc mvc;

    @Test void mvcUsesConfiguredJackson2Mapper() throws Exception {
        mvc.perform(get("/json-check")).andExpect(status().isOk())
                .andExpect(content().json("{\"wire_name\":\"value\"}", true));
    }
}
