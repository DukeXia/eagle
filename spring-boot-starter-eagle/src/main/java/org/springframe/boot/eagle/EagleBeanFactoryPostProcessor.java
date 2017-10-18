/*
 * Copyright 2017 eagle.jfaster.org.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package org.springframe.boot.eagle;

import com.google.common.base.Strings;
import eagle.jfaster.org.bean.*;
import eagle.jfaster.org.config.ProtocolConfig;
import eagle.jfaster.org.config.RegistryConfig;
import eagle.jfaster.org.config.annotation.Refer;
import eagle.jfaster.org.config.annotation.Service;
import eagle.jfaster.org.exception.EagleFrameException;
import eagle.jfaster.org.util.CollectionUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.bind.PropertiesConfigurationFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.StringUtils;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

import static eagle.jfaster.org.util.ParserUtil.multiRef;

/**
 * 根据配置和注解注入eagle相关类
 */
public class EagleBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private volatile EagleConfig eagleConfig;

    private final BeanNameGenerator beanNameGenerator = new DefaultBeanNameGenerator();

    private Environment environment = new StandardEnvironment();

    private DefaultConversionService conversionService = new DefaultConversionService();

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            if(eagleConfig == null){ //根据yml或者properties文件解析成EagleConfig
                eagleConfig = getEagleConfig((DefaultListableBeanFactory) beanFactory);
            }
            //根据配置注入registry
            registerRegistry((DefaultListableBeanFactory) beanFactory);

            //根据配置注入protocol
            registryProtocol((DefaultListableBeanFactory) beanFactory);

            //根据配置baseService
            registryBaseService((DefaultListableBeanFactory) beanFactory);

            //根据配置注入baseRefer
            registryBaseRefer((DefaultListableBeanFactory) beanFactory);

            //根据包注入service或者refer
            registerReferOrService((DefaultListableBeanFactory) beanFactory);
        } catch (Exception e) {
            throw new EagleFrameException(e);
        }
    }

    /**
     * 注入Registry
     * @param beanFactory bean工厂
     * @throws Exception
     */
    private void registerRegistry(DefaultListableBeanFactory beanFactory) throws Exception {
        if(CollectionUtil.isEmpty(eagleConfig.getRegistry())){
            throw new EagleFrameException("eagle.registry not config");
        }
        for (RegistryConfig config : eagleConfig.getRegistry()){
            String id = config.getId();
            id = Strings.isNullOrEmpty(id) ? config.getName() : config.getId();
            BeanDefinitionBuilder beanBuilder = generateBuilder(config,RegistryBean.class);
            beanFactory.registerBeanDefinition(generateId(id,beanBuilder,beanFactory), beanBuilder.getBeanDefinition());
        }

    }

    /**
     * 注入Protocol
     * @param beanFactory bean工厂
     * @throws Exception
     */
    private void registryProtocol(DefaultListableBeanFactory beanFactory) throws Exception {
        if(CollectionUtil.isEmpty(eagleConfig.getProtocol())){
            throw new EagleFrameException("eagle.protocol not config");
        }
        for (ProtocolConfig config : eagleConfig.getProtocol()){
            String id = config.getId();
            id = Strings.isNullOrEmpty(id) ? config.getName() : config.getId();
            BeanDefinitionBuilder beanBuilder = generateBuilder(config,ProtocolBean.class);
            beanFactory.registerBeanDefinition(generateId(id,beanBuilder,beanFactory), beanBuilder.getBeanDefinition());
        }
    }

    /**
     * 注入BaseService
     * @param beanFactory bean工厂
     * @throws Exception
     */
    private void registryBaseService(DefaultListableBeanFactory beanFactory) throws Exception{
        if(CollectionUtil.isEmpty(eagleConfig.getBaseService())){
            return;
        }
        for (BootBaseServiceConfig config : eagleConfig.getBaseService()){
            String id = config.getId();
            BeanDefinitionBuilder beanBuilder = generateBuilder(config,BaseServiceBean.class);
            multiRef("registries",config.getRegistry(),beanBuilder);
            beanFactory.registerBeanDefinition(generateId(id,beanBuilder,beanFactory),beanBuilder.getBeanDefinition());
        }

    }

    /**
     * 注入BaseRefer
     * @param beanFactory bean工厂
     * @throws Exception
     */
    private void registryBaseRefer(DefaultListableBeanFactory beanFactory) throws Exception{
        if(CollectionUtil.isEmpty(eagleConfig.getBaseRefer())){
            return;
        }
        for (BootBaseReferConfig config : eagleConfig.getBaseRefer()){
            String id = config.getId();
            BeanDefinitionBuilder beanBuilder = generateBuilder(config,BaseReferBean.class);
            multiRef("registries",config.getRegistry(),beanBuilder);
            multiRef("protocols",config.getProtocol(),beanBuilder);
            beanFactory.registerBeanDefinition(generateId(id,beanBuilder,beanFactory),beanBuilder.getBeanDefinition());
        }

    }

    /**
     *
     * 注入Refer或Service
     *
     * @param beanFactory bean工厂
     * @throws Exception
     */
    private void registerReferOrService(DefaultListableBeanFactory beanFactory) throws Exception {
        String[] basePackages = StringUtils.tokenizeToStringArray(eagleConfig.getBasePackage(), ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);
        for(String basePackage : basePackages){
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + basePackage.replaceAll("\\.", "/") + "/**/*.class";
            Resource[] rs = resourcePatternResolver.getResources(packageSearchPath);
            for (Resource r : rs) {
                MetadataReader reader = metadataReaderFactory.getMetadataReader(r);
                AnnotationMetadata annotationMD = reader.getAnnotationMetadata();
                if (annotationMD.hasAnnotation(Service.class.getName())) {
                    ClassMetadata clazzMD = reader.getClassMetadata();
                    Class<?> clz = Class.forName(clazzMD.getClassName());
                    if(clz.isInterface()){
                        throw new EagleFrameException("registried service should not be a interface");
                    }
                    Service serviceAnnotation = clz.getAnnotation(Service.class);
                    GenericBeanDefinition bf = new GenericBeanDefinition();
                    bf.setBeanClass(clz);
                    bf.setLazyInit(false);
                    String serviceId = Strings.isNullOrEmpty(serviceAnnotation.id()) ? beanNameGenerator.generateBeanName(bf,beanFactory) : serviceAnnotation.id();
                    //注册实现类
                    beanFactory.registerBeanDefinition(serviceId,bf);

                    Class<?>[] interfaces = clz.getInterfaces();
                    for(Class<?> interfaceName : interfaces){
                        //注册ServiceBean
                        BeanDefinitionBuilder service = BeanDefinitionBuilder.rootBeanDefinition(ServiceBean.class);
                        service.setLazyInit(false);
                        service.addPropertyValue("interface",interfaceName);
                        service.addPropertyReference("ref",serviceId);
                        if(Strings.isNullOrEmpty(serviceAnnotation.export())){
                            throw new EagleFrameException("export is not config");
                        }
                        service.addPropertyValue("export",serviceAnnotation.export());
                        if(!Strings.isNullOrEmpty(serviceAnnotation.registry())){
                            multiRef("registries",serviceAnnotation.registry(),service);
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.protocol())){
                            multiRef("protocols",serviceAnnotation.protocol(),service);
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.statsLog())){
                            service.addPropertyValue("statsLog",serviceAnnotation.statsLog());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.actives())){
                            service.addPropertyValue("actives",serviceAnnotation.actives());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.activesWait())){
                            service.addPropertyValue("activesWait",serviceAnnotation.activesWait());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.application())){
                            service.addPropertyValue("application",serviceAnnotation.application());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.group())){
                            service.addPropertyValue("group",serviceAnnotation.group());
                        }

                        if(!Strings.isNullOrEmpty(serviceAnnotation.baseService())){
                            service.addPropertyReference("baseService",serviceAnnotation.baseService());
                        }

                        if(!Strings.isNullOrEmpty(serviceAnnotation.filter())){
                            service.addPropertyValue("filter",serviceAnnotation.filter());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.host())){
                            service.addPropertyValue("host",serviceAnnotation.host());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.module())){
                            service.addPropertyValue("module",serviceAnnotation.module());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.mock())){
                            service.addPropertyValue("mock",serviceAnnotation.mock());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.retries())){
                            service.addPropertyValue("retries",serviceAnnotation.retries());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.register())){
                            service.addPropertyValue("register",serviceAnnotation.register());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.weight())){
                            service.addPropertyValue("weight",serviceAnnotation.weight());
                        }
                        if(!Strings.isNullOrEmpty(serviceAnnotation.version())){
                            service.addPropertyValue("version",serviceAnnotation.version());
                        }
                        beanFactory.registerBeanDefinition(beanNameGenerator.generateBeanName(service.getBeanDefinition(),beanFactory),service.getBeanDefinition());

                    }
                }else if(annotationMD.hasAnnotation(Refer.class.getName())){
                    ClassMetadata clazzMD = reader.getClassMetadata();
                    Class<?> clz = Class.forName(clazzMD.getClassName());
                    if(!clz.isInterface()){
                        throw new EagleFrameException("registried Refer should be a interface");
                    }
                    Refer referAnnotation = clz.getAnnotation(Refer.class);
                    BeanDefinitionBuilder refer = BeanDefinitionBuilder.rootBeanDefinition(ReferBean.class);
                    refer.setLazyInit(false);
                    if(!Strings.isNullOrEmpty(referAnnotation.registry())){
                        multiRef("registries",referAnnotation.registry(),refer);
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.protocol())){
                        multiRef("protocols",referAnnotation.protocol(),refer);
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.statsLog())){
                        refer.addPropertyValue("statsLog",referAnnotation.statsLog());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.actives())){
                        refer.addPropertyValue("actives",referAnnotation.actives());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.activesWait())){
                        refer.addPropertyValue("activesWait",referAnnotation.activesWait());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.application())){
                        refer.addPropertyValue("application",referAnnotation.application());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.group())){
                        refer.addPropertyValue("group",referAnnotation.group());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.baseRefer())){
                        refer.addPropertyReference("baseRefer",referAnnotation.baseRefer());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.filter())){
                        refer.addPropertyValue("filter",referAnnotation.filter());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.host())){
                        refer.addPropertyValue("host",referAnnotation.host());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.module())){
                        refer.addPropertyValue("module",referAnnotation.module());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.mock())){
                        refer.addPropertyValue("mock",referAnnotation.mock());
                        register(referAnnotation.mock(),"failMock",refer,beanNameGenerator,beanFactory);
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.retries())){
                        refer.addPropertyValue("retries",referAnnotation.retries());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.register())){
                        refer.addPropertyValue("register",referAnnotation.register());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.version())){
                        refer.addPropertyValue("version",referAnnotation.version());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.compress())){
                        refer.addPropertyValue("compress",referAnnotation.compress());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.connectTimeout())){
                        refer.addPropertyValue("connectTimeout",referAnnotation.connectTimeout());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.haStrategy())){
                        refer.addPropertyValue("haStrategy",referAnnotation.haStrategy());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.check())){
                        refer.addPropertyValue("check",referAnnotation.check());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.idleTime())){
                        refer.addPropertyValue("idleTime",referAnnotation.idleTime());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.subscribe())){
                        refer.addPropertyValue("subscribe",referAnnotation.subscribe());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.maxClientConnection())){
                        refer.addPropertyValue("maxClientConnection",referAnnotation.maxClientConnection());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.minClientConnection())){
                        refer.addPropertyValue("minClientConnection",referAnnotation.minClientConnection());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.maxLifetime())){
                        refer.addPropertyValue("maxLifetime",referAnnotation.maxLifetime());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.minClientConnection())){
                        refer.addPropertyValue("minClientConnection",referAnnotation.minClientConnection());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.loadbalance())){
                        refer.addPropertyValue("loadbalance",referAnnotation.loadbalance());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.maxInvokeError())){
                        refer.addPropertyValue("maxInvokeError",referAnnotation.maxInvokeError());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.minCompressSize())){
                        refer.addPropertyValue("minCompressSize",referAnnotation.minCompressSize());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.requestTimeout())){
                        refer.addPropertyValue("requestTimeout",referAnnotation.requestTimeout());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.proxy())){
                        refer.addPropertyValue("proxy",referAnnotation.proxy());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.callbackThread())){
                        refer.addPropertyValue("callbackThread",referAnnotation.callbackThread());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.callbackQueueSize())){
                        refer.addPropertyValue("callbackQueueSize",referAnnotation.callbackQueueSize());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.callbackWaitTime())){
                        refer.addPropertyValue("callbackWaitTime",referAnnotation.callbackWaitTime());
                    }
                    if(!Strings.isNullOrEmpty(referAnnotation.callback())){
                        refer.addPropertyValue("callback",referAnnotation.callback());
                        register(referAnnotation.callback(),"invokeCallback",refer,beanNameGenerator,beanFactory);
                    }
                    refer.addPropertyValue("interface",clz);
                    String referId = Strings.isNullOrEmpty(referAnnotation.id()) ? beanNameGenerator.generateBeanName(refer.getBeanDefinition(),beanFactory) : referAnnotation.id();
                    beanFactory.registerBeanDefinition(referId,refer.getBeanDefinition());
                }
            }
        }
    }

    private BeanDefinitionBuilder generateBuilder(Object config, Class<?> clz) throws Exception {
        BeanDefinitionBuilder beanBuilder = BeanDefinitionBuilder.rootBeanDefinition(clz);
        BeanInfo beanInfo = Introspector.getBeanInfo(config.getClass());
        PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        for (PropertyDescriptor property : propertyDescriptors ){
            String name = property.getName();
            if("class".equals(name)){
                continue;
            }
            if(clz == BaseServiceBean.class || clz == BaseReferBean.class){
                if("registry".equals(name) || "protocol".equals(name)){
                    continue;
                }
            }
            Method readMethod = property.getReadMethod();
            Object value = readMethod.invoke(config);
            if(value != null){
                beanBuilder.addPropertyValue(name,value);
            }
        }
        return beanBuilder;
    }

    private String generateId(String id,BeanDefinitionBuilder beanBuilder,DefaultListableBeanFactory beanFactory){
        if(!Strings.isNullOrEmpty(id)){
            return id;
        }
        return beanNameGenerator.generateBeanName(beanBuilder.getBeanDefinition(),beanFactory);
    }

    private void register(String beanClassName, String propertyName, BeanDefinitionBuilder beanBuilder, BeanNameGenerator beanNameGenerator, DefaultListableBeanFactory beanFactory){
        if(!Strings.isNullOrEmpty(beanClassName)){
            BeanDefinitionBuilder injectBuilder = BeanDefinitionBuilder.rootBeanDefinition(beanClassName);
            String injectId = beanNameGenerator.generateBeanName(injectBuilder.getBeanDefinition(),beanFactory);
            beanFactory.registerBeanDefinition(injectId,injectBuilder.getBeanDefinition());
            beanBuilder.addPropertyReference(propertyName, injectId);
        }
    }

    private EagleConfig getEagleConfig(DefaultListableBeanFactory beanFactory){
        EagleConfig bean = new EagleConfig();
        Object target = bean;
        PropertiesConfigurationFactory<Object> factory = new PropertiesConfigurationFactory<Object>(target);
        factory.setPropertySources(deducePropertySources(beanFactory));
        //factory.setValidator(determineValidator(bean));
        factory.setConversionService(conversionService);
        factory.setIgnoreInvalidFields(false);
        factory.setIgnoreUnknownFields(true);
        factory.setIgnoreNestedProperties(false);
        factory.setTargetName("eagle");
        try {
            factory.bindPropertiesToTarget();
        }
        catch (Exception ex) {
            throw new EagleFrameException(ex);
        }
        return bean;
    }


    private PropertySources deducePropertySources(DefaultListableBeanFactory beanFactory) {
        PropertySourcesPlaceholderConfigurer configurer = getSinglePropertySourcesPlaceholderConfigurer(beanFactory);
        if (configurer != null) {
            return new FlatPropertySources(configurer.getAppliedPropertySources());
        }
        if (this.environment instanceof ConfigurableEnvironment) {
            MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment)
                    .getPropertySources();
            return new FlatPropertySources(propertySources);
        }
        return new MutablePropertySources();
    }

    private PropertySourcesPlaceholderConfigurer getSinglePropertySourcesPlaceholderConfigurer(DefaultListableBeanFactory beanFactory) {
        if (beanFactory instanceof ListableBeanFactory) {
            ListableBeanFactory listableBeanFactory = (ListableBeanFactory) beanFactory;
            Map<String, PropertySourcesPlaceholderConfigurer> beans = listableBeanFactory
                    .getBeansOfType(PropertySourcesPlaceholderConfigurer.class, false,
                            false);
            if (beans.size() == 1) {
                return beans.values().iterator().next();
            }
        }
        return null;
    }

    static class FlatPropertySources implements PropertySources {

        private PropertySources propertySources;

        FlatPropertySources(PropertySources propertySources) {
            this.propertySources = propertySources;
        }

        @Override
        public Iterator<PropertySource<?>> iterator() {
            MutablePropertySources result = getFlattened();
            return result.iterator();
        }

        @Override
        public boolean contains(String name) {
            return get(name) != null;
        }

        @Override
        public PropertySource<?> get(String name) {
            return getFlattened().get(name);
        }

        private MutablePropertySources getFlattened() {
            MutablePropertySources result = new MutablePropertySources();
            for (PropertySource<?> propertySource : this.propertySources) {
                flattenPropertySources(propertySource, result);
            }
            return result;
        }

        private void flattenPropertySources(PropertySource<?> propertySource, MutablePropertySources result) {
            Object source = propertySource.getSource();
            if (source instanceof ConfigurableEnvironment) {
                ConfigurableEnvironment environment = (ConfigurableEnvironment) source;
                for (PropertySource<?> childSource : environment.getPropertySources()) {
                    flattenPropertySources(childSource, result);
                }
            }
            else {
                result.addLast(propertySource);
            }
        }
    }

}
