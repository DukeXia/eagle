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

package eagle.jfaster.org.bean;

import eagle.jfaster.org.config.SpiConfig;
import eagle.jfaster.org.spi.SpiClassLoader;
import org.springframework.beans.factory.InitializingBean;

/**
 * Created by fangyanpeng1 on 2017/8/13.
 */
public class SpiBean<T> extends SpiConfig<T> implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        SpiClassLoader.getClassLoader(getInterface()).addExtensionClass(getSpiClass());
    }
}
