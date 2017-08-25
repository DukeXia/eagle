package eagle.jfaster.org.service.impl;

import eagle.jfaster.org.CoordinatorRegistryCenter;
import eagle.jfaster.org.config.ConfigEnum;
import eagle.jfaster.org.config.common.MergeConfig;
import eagle.jfaster.org.pojo.ClientServiceInfo;
import eagle.jfaster.org.pojo.ServerServiceInfo;
import eagle.jfaster.org.pojo.ServiceBriefInfo;
import eagle.jfaster.org.service.InterfaceApiService;
import eagle.jfaster.org.util.CollectionUtil;
import eagle.jfaster.org.util.Logs;
import lombok.RequiredArgsConstructor;
import org.assertj.core.util.Strings;

import java.util.ArrayList;
import java.util.List;

import static eagle.jfaster.org.factory.ZookeeperRegistryCenterManage.*;
import static eagle.jfaster.org.util.ServiceConfigUtil.getServiceInfo;
import static eagle.jfaster.org.util.ServiceConfigUtil.setServiceInfo;

/**
 * Created by fangyanpeng on 2017/8/24.
 */
@RequiredArgsConstructor
public class InterfaceApiServiceImpl implements InterfaceApiService {

    private final CoordinatorRegistryCenter regCenter;

    @Override
    public int getServicesTotalCount() {
        return regCenter.getChildrenKeys("/").size();
    }

    @Override
    public List<ServiceBriefInfo> getClientBriefInfos() {
        return getBriefInfos(REF,REF_CHILDREN);
    }


    @Override
    public ClientServiceInfo getClientConfig(String serviceName, String protocol, String host) {
        String clientConfigJson = regCenter.getDirectly(String.format(REF,serviceName,protocol,host));
        if(Strings.isNullOrEmpty(clientConfigJson)){
            return null;
        }
        MergeConfig config = MergeConfig.decode(clientConfigJson);
        ClientServiceInfo serviceInfo = new ClientServiceInfo();
        getServiceInfo(ClientServiceInfo.class,config,serviceInfo);
        return serviceInfo;
    }

    @Override
    public boolean deleteClientConfig(String serviceName, String protocol) {
        try {
            regCenter.remove(String.format(REF_CHILDREN,serviceName,protocol));
            return true;
        } catch (Exception e) {
            Logs.error("deleteClientConfig",e);
            return false;
        }
    }

    @Override
    public boolean updateClientConfig(ClientServiceInfo serviceInfo) {
        try {
            MergeConfig config = new MergeConfig();
            setServiceInfo(ClientServiceInfo.class,config,serviceInfo);
            regCenter.update(String.format(REF,config.getInterfaceName(),config.getProtocol(),config.hostPort()),config.encode());
            return true;
        } catch (Exception e) {
            Logs.error("updateClientConfig",e);
            return false;
        }
    }

    @Override
    public List<ServiceBriefInfo> getServerBriefInfos() {
        return getBriefInfos(SERVICE,SERVICE_CHILDREN);
    }

    @Override
    public ServerServiceInfo getServerConfig(String serviceName, String protocol, String host) {
        String serverConfigJson = regCenter.getDirectly(String.format(SERVICE,serviceName,protocol,host));
        if(Strings.isNullOrEmpty(serverConfigJson)){
            return null;
        }
        MergeConfig config = MergeConfig.decode(serverConfigJson);
        ServerServiceInfo serviceInfo = new ServerServiceInfo();
        getServiceInfo(ServerServiceInfo.class,config,serviceInfo);
        return serviceInfo;
    }

    @Override
    public boolean deleteServerConfig(String serviceName, String protocol) {
        try {
            regCenter.remove(String.format(SERVICE_CHILDREN,serviceName,protocol));
            return true;
        } catch (Exception e) {
            Logs.error("deleteServerConfig",e);
            return false;
        }
    }

    @Override
    public boolean updateServerConfig(ServerServiceInfo serviceInfo) {
        try {
            MergeConfig config = new MergeConfig();
            setServiceInfo(ServerServiceInfo.class,config,serviceInfo);
            regCenter.update(String.format(SERVICE,config.getInterfaceName(),config.getProtocol(),config.hostPort()),config.encode());
            return true;
        } catch (Exception e) {
            Logs.error("updateServerConfig",e);
            return false;
        }
    }


    private List<ServiceBriefInfo> getBriefInfos(String pathFormat,String chilPahtFormat){
        List<String> interfaceNames = regCenter.getChildrenKeys("/");
        List<ServiceBriefInfo> briefInfos = new ArrayList<>();
        for(String interfaceName : interfaceNames){
            List<String> protocols = regCenter.getChildrenKeys("/"+interfaceName);
            for(String protocol : protocols){
                List<String> hosts = regCenter.getChildrenKeys(String.format(chilPahtFormat,interfaceName,protocol));
                if(CollectionUtil.isEmpty(hosts) && regCenter.isExisted(String.format(chilPahtFormat,interfaceName,protocol))){
                    ServiceBriefInfo briefInfo = new ServiceBriefInfo();
                    briefInfo.setProtocol(protocol);
                    briefInfo.setServiceName(interfaceName);
                    briefInfo.setStatus(ServiceBriefInfo.ServiceStatus.CRASHED);
                    briefInfos.add(briefInfo);
                }else {
                    for(String host : hosts){
                        String configJson = regCenter.getDirectly(String.format(pathFormat,interfaceName,protocol,host));
                        if(Strings.isNullOrEmpty(configJson)){
                            continue;
                        }
                        MergeConfig config = MergeConfig.decode(configJson);
                        ServiceBriefInfo briefInfo = new ServiceBriefInfo();
                        briefInfo.setGroup(config.getExt(ConfigEnum.group.getName(),ConfigEnum.group.getValue()));
                        briefInfo.setHost(config.getHost());
                        briefInfo.setProcess(config.getPort());
                        briefInfo.setProtocol(config.getProtocol());
                        briefInfo.setServiceName(config.getInterfaceName());
                        briefInfo.setStatus(ServiceBriefInfo.ServiceStatus.OK);
                        briefInfo.setVersion(config.getVersion());
                        briefInfos.add(briefInfo);
                    }
                }
            }
        }
        return briefInfos;
    }

}
