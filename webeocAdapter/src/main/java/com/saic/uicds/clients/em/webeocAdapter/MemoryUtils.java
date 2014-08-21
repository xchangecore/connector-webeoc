package com.saic.uicds.clients.em.webeocAdapter;

/* 
 * Adapted from http://www.javaworld.com/javaworld/javatips/jw-javatip130.html
 * and http://blogs.sun.com/sundararajan/entry/programmatically_dumping_heap_from_java
 */

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import com.sun.management.HotSpotDiagnosticMXBean;

public class MemoryUtils {

    private static final Runtime s_runtime = Runtime.getRuntime ();

    // This is the name of the HotSpot Diagnostic MBean
    private static final String HOTSPOT_BEAN_NAME =
         "com.sun.management:type=HotSpotDiagnostic";

    // field to store the hotspot diagnostic MBean 
    private static volatile HotSpotDiagnosticMXBean hotspotMBean;

    public static void runGC () throws Exception
    {
        // It helps to call Runtime.gc()
        // using several method calls:
        for (int r = 0; r < 4; ++ r) _runGC ();
    }
    private static void _runGC () throws Exception
    {
        long usedMem1 = usedMemory (), usedMem2 = Long.MAX_VALUE;
        for (int i = 0; (usedMem1 < usedMem2) && (i < 500); ++ i)
        {
            s_runtime.runFinalization ();
            s_runtime.gc ();
            Thread.yield();
            
            usedMem2 = usedMem1;
            usedMem1 = usedMemory ();
        }
    }
    public static long usedMemory ()
    {
        return s_runtime.totalMemory () - s_runtime.freeMemory ();
    }
    
    public static long maxMemory() {
    	return s_runtime.maxMemory();
    }
    
    /**
     * Call this method from your application whenever you 
     * want to dump the heap snapshot into a file.
     *
     * @param fileName name of the heap dump file
     * @param live flag that tells whether to dump
     *             only the live objects
     */
    static void dumpHeap(String fileName, boolean live) {
        // initialize hotspot diagnostic MBean
        initHotspotMBean();
        try {
            hotspotMBean.dumpHeap(fileName, live);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    // initialize the hotspot diagnostic MBean field
    private static void initHotspotMBean() {
        if (hotspotMBean == null) {
            synchronized (MemoryUtils.class) {
                if (hotspotMBean == null) {
                    hotspotMBean = getHotspotMBean();
                }
            }
        }
    }

    // get the hotspot diagnostic MBean from the
    // platform MBean server
    private static HotSpotDiagnosticMXBean getHotspotMBean() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            HotSpotDiagnosticMXBean bean = 
                ManagementFactory.newPlatformMXBeanProxy(server,
                HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
            return bean;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

}
