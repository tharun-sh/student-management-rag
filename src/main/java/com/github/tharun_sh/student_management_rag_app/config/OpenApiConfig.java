package com.github.tharun_sh.student_management_rag_app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Student Management RAG API")
                .description("CRUD for students and courses, plus a RAG chat endpoint powered by Gemini.")
                .version("v1"));
    }
}
