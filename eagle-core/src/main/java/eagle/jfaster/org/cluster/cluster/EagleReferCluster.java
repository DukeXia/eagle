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

package eagle.jfaster.org.cluster.cluster;

import eagle.jfaster.org.cluster.HaStrategy;
import eagle.jfaster.org.cluster.LoadBalance;
import eagle.jfaster.org.cluster.ReferCluster;
import eagle.jfaster.org.config.common.MergeConfig;
import eagle.jfaster.org.exception.MockException;
import eagle.jfaster.org.logging.InternalLogger;
import eagle.jfaster.org.logging.InternalLoggerFactory;
import eagle.jfaster.org.rpc.Mock;
import eagle.jfaster.org.rpc.Refer;
import eagle.jfaster.org.rpc.Request;
import eagle.jfaster.org.spi.SpiInfo;
import eagle.jfaster.org.util.ExceptionUtil;
import eagle.jfaster.org.util.ReferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by fangyanpeng1 on 2017/8/4.
 */
@SpiInfo(name = "eagle")
public class EagleReferCluster<T> implements ReferCluster<T> {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(EagleReferCluster.class);

    private LoadBalance<T> loadBalance;

    private HaStrategy<T> haStrategy;

    private volatile MergeConfig config;

    private List<Refer<T>> refers;

    private AtomicBoolean stat = new AtomicBoolean(false);

    private Mock mock;

    @Override
    public void init() {
        mock = config.getMock();
        stat.set(true);
    }

    @Override
    public void setConfig(MergeConfig config) {
        this.config = config;
    }

    @Override
    public void setLoadBalance(LoadBalance<T> loadBalance) {
        this.loadBalance = loadBalance;
    }

    @Override
    public void setHaStrategy(HaStrategy<T> haStrategy) {
        this.haStrategy= haStrategy;
    }

    @Override
    public synchronized void refresh(List<Refer<T>> refers) {
        loadBalance.refresh(refers);
        List<Refer<T>> oldRefers = this.refers;
        this.refers = refers;
        haStrategy.setConfig(getConfig());
        if (oldRefers == null || oldRefers.isEmpty()) {
            return;
        }
        List<Refer<T>> delayDestroyReferers = new ArrayList<Refer<T>>();
        for (Refer<T> refer : oldRefers) {
            if (refers.contains(refer)) {
                continue;
            }

            delayDestroyReferers.add(refer);
        }
        if (!delayDestroyReferers.isEmpty()) {
            ReferUtil.delayDestroy(delayDestroyReferers);
        }
    }

    @Override
    public List<Refer<T>> getRefers() {
        return refers;
    }

    @Override
    public LoadBalance<T> getLoadBalance() {
        return loadBalance;
    }

    @Override
    public Class<T> getInterface() {
        if(refers == null || refers.isEmpty()){
            return null;
        }
        return refers.get(0).getType();
    }

    @Override
    public Object call(Request request) {
        try {
            return haStrategy.call(request,loadBalance);
        } catch (Throwable e) {
            return dealCallFail(request,e);
        }
    }

    @Override
    public void destroy() {
        if(stat.compareAndSet(true,false)){
            if(refers == null){
                return;
            }
            for(Refer<T> refer : refers){
                refer.close(true);
            }
        }
    }


    @Override
    public boolean isAvailable() {
        return stat.get();
    }

    @Override
    public MergeConfig getConfig() {
        return config;
    }

    private Object dealCallFail(Request request,Throwable e)  {
        if(mock != null ){
            try {
                return mock.getMockValue(request.getInterfaceName(),request.getMethodName(),request.getParameters(),e);
            } catch (Throwable e1) {
                logger.error("Execute mock fail",e1);
                throw new MockException(e);
            }
        }
        throw ExceptionUtil.handleException(e);
    }
}
