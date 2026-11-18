package top.egon.cola.component.tianshu.service.refresh;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.context.properties.bind.BindMethod;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindHandlerAdvisor;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import top.egon.cola.component.tianshu.annotation.DdcRefreshable;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 发现并重新绑定允许动态刷新的 Spring Boot 配置属性 Bean。
 * Discovers and rebinds Spring Boot configuration-properties Beans that allow dynamic refresh.
 *
 * <p>仅使用 JavaBean 绑定且带有 {@link DdcRefreshable} 的配置属性 Bean 会被纳入刷新。
 * 被删除且没有其他属性源回退值的键不会触发重绑定，以避免把缺失值写入可变 Bean。</p>
 *
 * <p>Only configuration-properties Beans annotated with {@link DdcRefreshable} and using JavaBean binding
 * participate in refresh. A removed key without a fallback value from another property source prevents rebinding,
 * avoiding writes of missing values into mutable Beans.</p>
 */
@Slf4j
public class DdcConfigurationPropertiesRebinder
        implements SmartInitializingSingleton {

    /**
     * 用于发现配置属性 Bean 的应用上下文。 Application context used to discover configuration-properties Beans.
     */
    private final ApplicationContext applicationContext;

    /**
     * 执行配置属性重新绑定的后处理器。 Post-processor that performs configuration-properties rebinding.
     */
    private final ConfigurationPropertiesBindingPostProcessor bindingPostProcessor;

    /**
     * 用于检查删除键是否存在回退值的环境。 Environment used to check fallback values for removed keys.
     */
    private final Environment environment;

    /**
     * 初始化完成后缓存的可刷新 Bean 不可变列表。 Immutable list of refreshable Beans cached after initialization.
     */
    private volatile List<RefreshableBean> refreshableBeans = List.of();

    /**
     * 创建配置属性重新绑定器。
     * Creates a configuration-properties rebinder.
     *
     * @param applicationContext   Spring 应用上下文; Spring application context
     * @param bindingPostProcessor 配置属性绑定后处理器; configuration-properties binding post-processor
     * @param environment          Spring 环境; Spring environment
     */
    public DdcConfigurationPropertiesRebinder(
            ApplicationContext applicationContext,
            ConfigurationPropertiesBindingPostProcessor bindingPostProcessor,
            Environment environment) {
        this.applicationContext = applicationContext;
        this.bindingPostProcessor = bindingPostProcessor;
        this.environment = environment;
    }

    /**
     * 在所有单例初始化后发现、校验并缓存可刷新的配置属性 Bean。
     * Discovers, validates, and caches refreshable configuration-properties Beans after singleton initialization.
     *
     * @throws IllegalStateException 可刷新 Bean 使用不可变绑定或没有可写属性时抛出;
     *                               thrown when a refreshable Bean uses immutable binding or exposes no writable property
     */
    @Override
    public void afterSingletonsInstantiated() {
        List<RefreshableBean> candidates = new ArrayList<>();
        for (Map.Entry<String, ConfigurationPropertiesBean> entry
                : ConfigurationPropertiesBean.getAll(applicationContext).entrySet()) {
            ConfigurationPropertiesBean bean = entry.getValue();
            Class<?> beanType = ClassUtils.getUserClass(bean.getInstance());
            if (!AnnotatedElementUtils.hasAnnotation(
                    beanType,
                    DdcRefreshable.class
            )) {
                continue;
            }
            validate(entry.getKey(), bean, beanType);
            candidates.add(new RefreshableBean(
                    entry.getKey(),
                    bean.getInstance(),
                    ConfigurationPropertyName.of(
                            bean.getAnnotation().prefix()
                    ), bean
            ));
        }
        refreshableBeans = List.copyOf(candidates);
    }

    /**
     * 重新绑定受本次有效配置变化影响且没有无回退删除项的 Bean。
     * Rebinds Beans affected by effective changes and having no removed property without a fallback.
     *
     * @param changedKeys 发生有效变化的配置键; effectively changed configuration keys
     * @param removedKeys 已删除的配置键; removed configuration keys
     * @return 通过配置属性 Bean 重绑定完成刷新的键; keys refreshed through configuration-properties rebinding
     */
    public Set<String> rebind(Set<String> changedKeys,
                              Set<String> removedKeys) {
        Set<String> refreshedKeys = new LinkedHashSet<>();
        List<BeanChange> attempted = new ArrayList<>();
        try {
        for (RefreshableBean bean : refreshableBeans) {
            Set<String> matchingKeys = matchingKeys(
                    bean.prefix(),
                    changedKeys
            );
            if (matchingKeys.isEmpty()
                    || hasRemovalWithoutFallback(
                    bean.prefix(),
                    removedKeys
            )) {
                continue;
            }
            attempted.add(new BeanChange(bean.instance(), copyBean(bean.instance()), null));
            bindingPostProcessor.postProcessBeforeInitialization(
                    bean.instance(),
                    bean.name()
            );
            refreshedKeys.addAll(matchingKeys);
        }
        return Set.copyOf(refreshedKeys);
        } catch (RuntimeException failure) {
            restore(attempted, failure);
            throw failure;
        }
    }

    /** Prefix ownership is deliberately wider than capability, so explicit null cannot leak to an ordinary consumer. */
    public boolean consumesKey(String key) {
        return refreshableBeans.stream().anyMatch(bean -> !matchingKeys(bean.prefix(), Set.of(key)).isEmpty());
    }

    public boolean supportsKey(String key) {
        return refreshableBeans.stream().anyMatch(bean -> propertyPath(bean, key) != null);
    }

    /** Bind and validate isolated copies. No live Bean or live property source is changed during preparation. */
    public PreparedRebind prepare(Environment candidate, Set<String> changedKeys, Set<String> removedKeys) {
        List<BeanChange> changes = new ArrayList<>();
        Set<String> refreshed = new LinkedHashSet<>();
        var conversion = new ApplicationConversionService();
        ApplicationConversionService.addBeans(conversion, applicationContext);
        var binder = new Binder(ConfigurationPropertySources.get(candidate),
                new PropertySourcesPlaceholdersResolver(candidate), conversion);
        LocalValidatorFactoryBean ownedValidator = null;
        var jakartaValidator = applicationContext.getBeanProvider(jakarta.validation.Validator.class).getIfAvailable();
        if (jakartaValidator == null) {
            ownedValidator = new LocalValidatorFactoryBean();
            ownedValidator.setApplicationContext(applicationContext);
            ownedValidator.afterPropertiesSet();
            jakartaValidator = ownedValidator;
        }
        try {
            for (var bean : refreshableBeans) {
                var matching = matchingKeys(bean.prefix(), changedKeys);
                if (matching.isEmpty()) continue;
                for (var key : matching) {
                    if (propertyPath(bean, key) == null) throw new IllegalStateException("CONFIG_HOT_CONSUMER_UNKNOWN");
                    if (removedKeys.contains(key) && candidate.getProperty(key) == null) {
                        throw new IllegalStateException("CONFIG_HOT_REMOVAL_REQUIRES_RESTART");
                    }
                }
                Object previous = copyBean(bean.instance());
                Object prepared = copyBean(bean.instance());
                BindHandler handler = BindHandler.DEFAULT;
                if (!bean.descriptor().getAnnotation().ignoreUnknownFields()) handler = new NoUnboundElementsBindHandler(handler);
                var validators = new ArrayList<org.springframework.validation.Validator>();
                validators.add(new SpringValidatorAdapter(jakartaValidator));
                if (applicationContext.containsBean("configurationPropertiesValidator")) {
                    validators.add(applicationContext.getBean("configurationPropertiesValidator", org.springframework.validation.Validator.class));
                }
                handler = new ValidationBindHandler(handler, validators.toArray(org.springframework.validation.Validator[]::new));
                for (var advisor : applicationContext.getBeanProvider(ConfigurationPropertiesBindHandlerAdvisor.class).orderedStream().toList()) {
                    handler = advisor.apply(handler);
                }
                binder.bind(bean.prefix(), Bindable.ofInstance(prepared)
                        .withAnnotations(bean.descriptor().asBindTarget().getAnnotations()), handler);
                var validated = AnnotatedElementUtils.findMergedAnnotation(ClassUtils.getUserClass(bean.instance()),
                        org.springframework.validation.annotation.Validated.class);
                if (validated == null) validated = bean.descriptor().asBindTarget()
                        .getAnnotation(org.springframework.validation.annotation.Validated.class);
                var groups = validated == null ? new Class<?>[0] : validated.value();
                var violations = jakartaValidator.validate(prepared, groups);
                if (!violations.isEmpty()) throw new jakarta.validation.ConstraintViolationException(violations);
                if (applicationContext.containsBean("configurationPropertiesValidator")) {
                    var custom = applicationContext.getBean("configurationPropertiesValidator", org.springframework.validation.Validator.class);
                    if (custom.supports(prepared.getClass())) {
                        var errors = new org.springframework.validation.BeanPropertyBindingResult(prepared, bean.name());
                        if (custom instanceof org.springframework.validation.SmartValidator smart) smart.validate(prepared, errors, (Object[]) groups);
                        else custom.validate(prepared, errors);
                        if (errors.hasErrors()) throw new IllegalArgumentException("CONFIG_HOT_BEAN_VALIDATION_FAILED");
                    }
                }
                changes.add(new BeanChange(bean.instance(), previous, prepared));
                refreshed.addAll(matching);
            }
            return new PreparedRebind(changes, refreshed);
        } finally {
            if (ownedValidator != null) ownedValidator.close();
        }
    }

    public static final class PreparedRebind {
        private final List<BeanChange> changes;
        private final Set<String> refreshed;
        private int attempted;

        private PreparedRebind(List<BeanChange> changes, Set<String> refreshed) {
            this.changes = List.copyOf(changes);
            this.refreshed = Set.copyOf(refreshed);
        }
        public Set<String> refreshedKeys() { return refreshed; }
        public void commit() {
            for (var change : changes) {
                attempted++;
                BeanUtils.copyProperties(change.prepared(), change.instance());
            }
        }
        public void rollback() {
            var failure = new IllegalStateException("CONFIG_HOT_ROLLBACK_FAILED");
            restore(changes.subList(0, attempted), failure);
            attempted = 0;
            if (failure.getSuppressed().length != 0) throw failure;
        }
    }

    private static void restore(List<BeanChange> changes, RuntimeException failure) {
        for (int index = changes.size() - 1; index >= 0; index--) {
            var change = changes.get(index);
            try { BeanUtils.copyProperties(change.previous(), change.instance()); }
            catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
        }
    }

    private String propertyPath(RefreshableBean bean, String key) {
        if (matchingKeys(bean.prefix(), Set.of(key)).isEmpty()) return null;
        String prefix = bean.prefix().toString();
        String path = prefix.isEmpty() ? key : key.length() > prefix.length() ? key.substring(prefix.length() + 1) : "";
        if (path.isBlank()) return null;
        Class<?> type = ClassUtils.getUserClass(bean.instance());
        StringBuilder resolved = new StringBuilder();
        String[] elements = path.split("\\.");
        for (int index = 0; index < elements.length; index++) {
            PropertyDescriptor found = null;
            for (var descriptor : BeanUtils.getPropertyDescriptors(type)) {
                if (relaxed(descriptor.getName()).equals(relaxed(elements[index]))) { found = descriptor; break; }
            }
            if (found == null || found.getReadMethod() == null || found.getWriteMethod() == null) return null;
            if (!resolved.isEmpty()) resolved.append('.');
            resolved.append(found.getName());
            type = found.getPropertyType();
        }
        return resolved.toString();
    }

    private static String relaxed(String value) { return value.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT); }

    /** Deeply detached JavaBean state; unsupported runtime resources are never silently shared into a HOT preview. */
    private static Object copyBean(Object value) { return copyValue(value, new IdentityHashMap<>(), 0); }

    private static Object copyValue(Object value, IdentityHashMap<Object, Object> copies, int depth) {
        if (value == null || immutable(value.getClass())) return value;
        if (depth > 32 || copies.containsKey(value)) throw new IllegalStateException("CONFIG_HOT_BEAN_STATE_UNSUPPORTED");
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<>();
            copies.put(value, result);
            map.forEach((key, element) -> result.put(copyValue(key, copies, depth + 1), copyValue(element, copies, depth + 1)));
            copies.remove(value);
            return result;
        }
        if (value instanceof java.util.Collection<?> collection) {
            java.util.Collection<Object> result = value instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();
            copies.put(value, result);
            for (var element : collection) result.add(copyValue(element, copies, depth + 1));
            copies.remove(value);
            return result;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            var result = java.lang.reflect.Array.newInstance(value.getClass().componentType(), length);
            copies.put(value, result);
            for (int index = 0; index < length; index++) java.lang.reflect.Array.set(result, index,
                    copyValue(java.lang.reflect.Array.get(value, index), copies, depth + 1));
            copies.remove(value);
            return result;
        }
        if (value.getClass().getName().startsWith("java.") || value.getClass().isRecord()) {
            throw new IllegalStateException("CONFIG_HOT_BEAN_STATE_UNSUPPORTED");
        }
        var result = BeanUtils.instantiateClass(ClassUtils.getUserClass(value));
        copies.put(value, result);
        for (var descriptor : BeanUtils.getPropertyDescriptors(ClassUtils.getUserClass(value))) {
            if (descriptor.getReadMethod() == null || "class".equals(descriptor.getName())) continue;
            ReflectionUtils.makeAccessible(descriptor.getReadMethod());
            Object property = ReflectionUtils.invokeMethod(descriptor.getReadMethod(), value);
            if (descriptor.getWriteMethod() == null) {
                Object initial = ReflectionUtils.invokeMethod(descriptor.getReadMethod(), result);
                if (!java.util.Objects.equals(property, initial) || property != null && !immutable(property.getClass())) {
                    throw new IllegalStateException("CONFIG_HOT_BEAN_STATE_UNSUPPORTED");
                }
            } else {
                ReflectionUtils.makeAccessible(descriptor.getWriteMethod());
                ReflectionUtils.invokeMethod(descriptor.getWriteMethod(), result, copyValue(property, copies, depth + 1));
            }
        }
        copies.remove(value);
        return result;
    }

    private static boolean immutable(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type == String.class || type == Boolean.class || type == Character.class
                || type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == Float.class || type == Double.class || type == BigDecimal.class || type == BigInteger.class
                || type.getName().startsWith("java.time.") || type == java.net.URI.class || type == java.util.UUID.class;
    }

    private record BeanChange(Object instance, Object previous, Object prepared) { }

    /**
     * 校验候选 Bean 使用可变 JavaBean 绑定并至少公开一个写方法。
     * Validates that a candidate uses mutable JavaBean binding and exposes at least one write method.
     *
     * @param beanName Spring Bean 名称; Spring Bean name
     * @param bean     配置属性 Bean 描述; configuration-properties Bean descriptor
     * @param beanType 用户定义的 Bean 类型; user-defined Bean type
     * @throws IllegalStateException 候选 Bean 无法安全重绑定时抛出; thrown when the candidate cannot be rebound safely
     */
    private void validate(String beanName,
                          ConfigurationPropertiesBean bean,
                          Class<?> beanType) {
        if (beanType.isRecord()
                || bean.asBindTarget().getBindMethod()
                == BindMethod.VALUE_OBJECT) {
            throw new IllegalStateException(
                    "Tianshu refreshable @ConfigurationProperties bean '"
                            + beanName + "' must use JavaBean binding"
            );
        }
        boolean writable = false;
        for (PropertyDescriptor descriptor
                : BeanUtils.getPropertyDescriptors(beanType)) {
            if (descriptor.getWriteMethod() != null) {
                writable = true;
                break;
            }
        }
        if (!writable) {
            throw new IllegalStateException(
                    "Tianshu refreshable @ConfigurationProperties bean '"
                            + beanName + "' must expose writable properties"
            );
        }
    }

    /**
     * 判断指定前缀下是否存在删除后也无法从其他属性源解析的键。
     * Determines whether the prefix contains a removed key that cannot be resolved from another property source.
     *
     * @param prefix      配置属性前缀; configuration-properties prefix
     * @param removedKeys 已删除的配置键; removed configuration keys
     * @return 存在无回退删除键时为 {@code true}; {@code true} when a removed key has no fallback
     */
    private boolean hasRemovalWithoutFallback(
            ConfigurationPropertyName prefix,
            Set<String> removedKeys) {
        for (String key : matchingKeys(prefix, removedKeys)) {
            if (environment.getProperty(key) == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 筛选与配置属性前缀相同或位于该前缀之下的键。
     * Filters keys equal to or nested beneath the configuration-properties prefix.
     *
     * @param prefix 配置属性前缀; configuration-properties prefix
     * @param keys   待筛选的配置键; configuration keys to filter
     * @return 保持输入迭代顺序的匹配键集合; matching keys preserving input iteration order
     */
    private Set<String> matchingKeys(
            ConfigurationPropertyName prefix,
            Set<String> keys) {
        Set<String> matching = new LinkedHashSet<>();
        for (String key : keys) {
            ConfigurationPropertyName name =
                    ConfigurationPropertyName.adapt(key, '.');
            if (prefix.isEmpty()
                    || prefix.equals(name)
                    || prefix.isAncestorOf(name)) {
                matching.add(key);
            }
        }
        return matching;
    }

    /**
     * 缓存一个可刷新配置属性 Bean 的绑定信息。
     * Cached binding information for a refreshable configuration-properties Bean.
     *
     * @param name     Spring Bean 名称; Spring Bean name
     * @param instance Bean 实例; Bean instance
     * @param prefix   规范化后的配置属性前缀; normalized configuration-properties prefix
     */
    private record RefreshableBean(
            String name,
            Object instance,
            ConfigurationPropertyName prefix,
            ConfigurationPropertiesBean descriptor
    ) {
    }
}
