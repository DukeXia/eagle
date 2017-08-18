package eagle.jfaster.org.listener;

import eagle.jfaster.org.config.common.MergeConfig;
import eagle.jfaster.org.CoordinatorRegistryCenter;
import eagle.jfaster.org.logging.InternalLogger;
import eagle.jfaster.org.logging.InternalLoggerFactory;
import eagle.jfaster.org.registry.ServiceChangeListener;
import eagle.jfaster.org.util.PathUtil;
import lombok.RequiredArgsConstructor;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

/**
 * 监听注册的service变化
 *
 * Created by fangyanpeng1 on 2017/8/4.
 */
@RequiredArgsConstructor
public class ServiceListener extends AbstractChildrenDataListener {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(ServiceListener.class);

    //zk地址配置
    private final MergeConfig registryConfig;

    //节点变化通知
    private final ServiceChangeListener changeListener;

    //注册中心
    private final CoordinatorRegistryCenter registryCenter;

    //refer监听地址
    private final String servicePath;

    @Override
    protected void dataChanged(String path, PathChildrenCacheEvent.Type eventType, String data) {
        if(eventType == PathChildrenCacheEvent.Type.CHILD_ADDED || eventType == PathChildrenCacheEvent.Type.CHILD_REMOVED || eventType == PathChildrenCacheEvent.Type.CHILD_UPDATED){
            try {
                PathUtil.rebalance(registryCenter,registryConfig,changeListener,servicePath);
            } catch (Exception e) {
                logger.error("Zookeeper service listener failed ",e);
            }
        }
    }
}
