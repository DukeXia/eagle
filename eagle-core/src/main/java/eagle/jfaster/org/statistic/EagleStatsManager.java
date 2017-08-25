package eagle.jfaster.org.statistic;



import com.google.common.base.Strings;
import eagle.jfaster.org.logging.InternalLogger;
import eagle.jfaster.org.logging.InternalLoggerFactory;
import eagle.jfaster.org.util.UtilityUtil;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static eagle.jfaster.org.constant.EagleConstants.STATISTIC_PEROID;

/**
 *
 * 各个接口统计管理器
 *
 * Created by fangyanpeng on 2017/8/22.
 */
public class EagleStatsManager {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EagleStatsManager.class);

    private static final HashMap<String, StatsItemSet> statsTable = new HashMap<>();

    private static final List<StatisticCallback> statsList = new LinkedList<>();

    private static final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new UtilityUtil.DefaultThreadFactory("EagleServiceStatsThread",true));

    static {
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                // memory
                logMemoryStatistic();
                // callbacks
                logStatisticCallback();
            }
        }, STATISTIC_PEROID, STATISTIC_PEROID, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                if (!scheduledExecutor.isShutdown()) {
                    scheduledExecutor.shutdownNow();
                }
            }
        });

    }

    public static StatsItem getStatsItem(final String statsName, final String statsKey) {
        try {
            return statsTable.get(statsName).getStatsItem(statsKey);
        } catch (Exception e) {

        }
        return null;
    }

    public synchronized static void registerStatsItem(String key, InternalLogger log){
        if(!statsTable.containsKey(key)){
            statsTable.put(key,new StatsItemSet(key,scheduledExecutor,log));
        }else {
            logger.warn(String.format("key:%s has already exist",key));
        }
    }

    public static void incInvoke(final String key, final String methodDesc, final long excTime) {
        statsTable.get(key).addValue(methodDesc, excTime, 1);
    }

    public synchronized static void registerStatsCallback(StatisticCallback callback){
        if(!statsList.contains(callback)){
            statsList.add(callback);
        }else {
            logger.warn(String.format("key:%s has already exist",callback));
        }
    }

    public static void logMemoryStatistic() {
        try {
            Runtime runtime = Runtime.getRuntime();
            double freeMemory = (double) runtime.freeMemory() / (1024 * 1024);
            double maxMemory = (double) runtime.maxMemory() / (1024 * 1024);
            double totalMemory = (double) runtime.totalMemory() / (1024 * 1024);
            double usedMemory = totalMemory - freeMemory;
            double percentFree = ((maxMemory - usedMemory) / maxMemory) * 100.0;
            double percentUsed = 100 - percentFree;
            DecimalFormat mbFormat = new DecimalFormat("#0.00");
            DecimalFormat percentFormat = new DecimalFormat("#0.0");
            StringBuilder sb = new StringBuilder();
            sb.append(mbFormat.format(usedMemory)).append("MB of ").append(mbFormat.format(maxMemory)).append(" MB (").append(percentFormat.format(percentUsed)).append("%) used");
            logger.info(sb.toString());
        } catch (Exception e) {
            logger.error("EagleStatsManager logMemoryStatistic Error: " + e.getMessage(), e);
        }
    }

    public static void logStatisticCallback() {
        for (StatisticCallback callback : statsList) {
            try {
                String msg = callback.statistic();

                if (!Strings.isNullOrEmpty(msg)) {
                    logger.info(String.format("[eagle-statisticCallback] %s", msg));
                }
            } catch (Exception e) {
                logger.error("EagleStatsManager logStatisticCallback Error: " + e.getMessage(), e);
            }
        }
    }


}
