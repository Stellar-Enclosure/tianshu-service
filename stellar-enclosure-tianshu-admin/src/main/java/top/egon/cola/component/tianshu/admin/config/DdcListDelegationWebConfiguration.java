package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;

import java.io.IOException;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class DdcListDelegationWebConfiguration {
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    @Bean("ddcListDelegationWebConfigurer")
    public WebMvcConfigurer ddcListDelegationWebConfigurer() {
        return new WebMvcConfigurer() {
            @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.addFirst(new RequestConverter());
            }
        };
    }

    static final class RequestConverter extends AbstractHttpMessageConverter<ListDelegationRequest> {
        private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(2048).maxNumberLength(20).build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        RequestConverter() { super(MediaType.APPLICATION_JSON); }
        @Override protected boolean supports(Class<?> type) { return type == ListDelegationRequest.class; }
        @Override public boolean canWrite(Class<?> type, MediaType mediaType) { return false; }
        @Override protected ListDelegationRequest readInternal(Class<? extends ListDelegationRequest> type, HttpInputMessage input) throws IOException {
            if (input.getHeaders().getContentLength() > MAX_BODY_BYTES) throw new BodyTooLargeException();
            byte[] body = input.getBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) throw new BodyTooLargeException();
            return mapper.readValue(body, ListDelegationRequest.class);
        }
        @Override protected void writeInternal(ListDelegationRequest value, HttpOutputMessage output) { throw new UnsupportedOperationException(); }
    }
    public static final class BodyTooLargeException extends RuntimeException { }
}
