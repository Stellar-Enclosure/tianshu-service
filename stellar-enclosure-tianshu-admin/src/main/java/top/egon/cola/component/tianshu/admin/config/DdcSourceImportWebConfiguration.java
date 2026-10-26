package top.egon.cola.component.tianshu.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nSourceImportJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest;

import java.io.IOException;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class DdcSourceImportWebConfiguration {
    @Bean("ddcSourceImportWebConfigurer")
    public WebMvcConfigurer ddcSourceImportWebConfigurer(DdcI18nSourceImportJson json) {
        return new WebMvcConfigurer() {
            @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.addFirst(new SourceImportConverter(json));
            }
        };
    }

    static final class SourceImportConverter extends AbstractHttpMessageConverter<SourceImportRequest> {
        private final DdcI18nSourceImportJson json;

        SourceImportConverter(DdcI18nSourceImportJson json) {
            super(MediaType.APPLICATION_JSON);
            this.json = json;
        }

        @Override protected boolean supports(Class<?> type) { return type == SourceImportRequest.class; }
        @Override public boolean canWrite(Class<?> type, MediaType mediaType) { return false; }

        @Override protected SourceImportRequest readInternal(Class<? extends SourceImportRequest> type, HttpInputMessage input) throws IOException {
            if (input.getHeaders().getContentLength() > DdcI18nSourceImportJson.MAX_BODY_BYTES) {
                throw new DdcI18nSourceImportJson.BodyTooLargeException();
            }
            return json.read(input.getBody().readNBytes(DdcI18nSourceImportJson.MAX_BODY_BYTES + 1));
        }

        @Override protected void writeInternal(SourceImportRequest value, HttpOutputMessage output) {
            throw new UnsupportedOperationException("The source import converter only reads requests");
        }
    }
}
