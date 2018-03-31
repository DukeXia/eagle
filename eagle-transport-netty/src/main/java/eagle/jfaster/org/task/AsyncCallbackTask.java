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

package eagle.jfaster.org.task;

import java.util.List;

import eagle.jfaster.org.interceptor.ExecutionInterceptor;
import eagle.jfaster.org.logging.InternalLogger;
import eagle.jfaster.org.logging.InternalLoggerFactory;
import eagle.jfaster.org.rpc.ResponseFuture;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Created by fangyanpeng on 2017/8/22.
 */
@RequiredArgsConstructor
public class AsyncCallbackTask implements Runnable {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(AsyncCallbackTask.class);

    @Getter
    private final ResponseFuture responseFuture;

    private final List<ExecutionInterceptor> interceptors;

    @Override
    public void run() {
        try {
            responseFuture.executeCallback(interceptors);
        } catch (Throwable e) {
            logger.info("execute callback in executor exception, and callback throw", e);
        }
    }
}