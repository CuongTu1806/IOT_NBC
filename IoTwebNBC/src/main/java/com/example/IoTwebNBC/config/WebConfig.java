package com.example.IoTwebNBC.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.ContentNegotiatingViewResolver;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(@NonNull ContentNegotiationConfigurer configurer) {
        // allow ?format=json and respect Accept header
        configurer.favorParameter(true)
                .parameterName("format")
                .ignoreAcceptHeader(false)
                .defaultContentType(MediaType.TEXT_HTML)
                .mediaType("json", MediaType.APPLICATION_JSON)
                .mediaType("html", MediaType.TEXT_HTML);
    }

    @Bean
    public ViewResolver contentNegotiatingViewResolver(ContentNegotiationManager manager,
                                                       ObjectProvider<List<ViewResolver>> resolversProvider,
                                                       ObjectProvider<ObjectMapper> mapperProvider) {
        ContentNegotiatingViewResolver resolver = new ContentNegotiatingViewResolver();
        resolver.setContentNegotiationManager(manager);

        MappingJackson2JsonView jsonView = new MappingJackson2JsonView();
        // use provided ObjectMapper if available (Spring Boot auto-configured)
        mapperProvider.ifAvailable(jsonView::setObjectMapper);
        jsonView.setPrettyPrint(true);

        resolver.setDefaultViews(List.of((View) jsonView));

        // let Spring inject other resolvers (Thymeleaf view resolver)
        List<ViewResolver> resolvers = resolversProvider.getIfAvailable();
        if (resolvers != null) resolver.setViewResolvers(resolvers);

        resolver.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return resolver;
    }
}
