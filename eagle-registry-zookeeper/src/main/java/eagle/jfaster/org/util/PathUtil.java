package eagle.jfaster.org.util;

import eagle.jfaster.org.CoordinatorRegistryCenter;
import eagle.jfaster.org.config.common.MergeConfig;
import eagle.jfaster.org.registry.ServiceChangeListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fangyanpeng1 on 2017/8/4.
 */
public class PathUtil {

    private static final String FULL_PATH = "%s/%s";

    public static String getHostByPath(String path){
        int pos = path.lastIndexOf("/");
        if(pos < 0 || pos == path.length() -1){
            return null;
        }
        return path.substring(pos+1);
    }

    public static String getFullPath(String servicePath,String host){
        return String.format(FULL_PATH,servicePath,host);
    }

    public static void rebalance(CoordinatorRegistryCenter registryCenter,MergeConfig registryConfig,ServiceChangeListener changeListener,String servicePath){
        List<String> hosts = registryCenter.getChildrenKeys(servicePath);
        if(hosts == null || hosts.isEmpty()){
            changeListener.serviceChange(registryConfig,null);
        }else {
            List<MergeConfig> configs = new ArrayList<>(hosts.size());
            for(String host : hosts){
                String serviceConfig = registryCenter.getDirectly(getFullPath(servicePath,host));
                configs.add(MergeConfig.decode(serviceConfig));
            }
            changeListener.serviceChange(registryConfig,configs);
        }
    }

}
