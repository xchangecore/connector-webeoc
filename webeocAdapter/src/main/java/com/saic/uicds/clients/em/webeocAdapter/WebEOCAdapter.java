package com.saic.uicds.clients.em.webeocAdapter;

import java.io.FileNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.saic.uicds.clients.em.async.UicdsCore;

/**
 * Main class for the UICDS WebEOC adapter. This adapter synchronizes incidents created on a UICDS
 * core with the UICDS Sig Event Board and the entries created on the UICDS Sig Event Board with the
 * UICDS core. UICDS Incidents created by the WebEOC adapter are identified on the core by having an
 * Incident Event element that has a Category Description value of "WEBEOC". On startup the adapter
 * will use the data from the UICDS Sig Event Board to update only those incidents. If the incident
 * was not created by "WEBEOC" then the adapter will use the UICDS incident document to update the
 * WebEOC UICDS Sig Event Board entry on startup.
 * 
 * @author roger
 * 
 */
public class WebEOCAdapter {

    private final static String APP_CONTEXT_FILE = "webeoc-context.xml";

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private UicdsCore uicdsCore;

    private WebEOCEventProducer webEOCEventProducer;

    private long sleepDurationInSeconds;

    Runtime runtime;

    public UicdsCore getUicdsCore() {

        return uicdsCore;
    }

    public void setUicdsCore(UicdsCore uicdsCore) {

        this.uicdsCore = uicdsCore;
    }

    public WebEOCEventProducer getWebEOCEventProducer() {

        return webEOCEventProducer;
    }

    public void setWebEOCEventProducer(WebEOCEventProducer webEOCEventProducer) {

        this.webEOCEventProducer = webEOCEventProducer;
    }

    /**
     * @return the sleepDurationInSeconds
     */
    public long getSleepDurationInSeconds() {

        return sleepDurationInSeconds;
    }

    /**
     * @param sleepDurationInSeconds the sleepDurationInSeconds to set
     */
    public void setSleepDurationInSeconds(long sleepDurationInSeconds) {

        this.sleepDurationInSeconds = sleepDurationInSeconds;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        // default the protocol if not specified
        if (args.length == 1) {
            usage();
            return;
        }

        ApplicationContext context = getApplicationContext();

        WebEOCAdapter adapter = (WebEOCAdapter) context.getBean("webEOCAdapter");

        adapter.initialize(context);

        adapter.run();

    }

    public void initialize(ApplicationContext context) {

        // Create connection to UICDS core
        if (!setupCoreConnection(context)) {
            logger.error("Error setting up the UICDS core connection");
            System.exit(1);
        }

        runtime = java.lang.Runtime.getRuntime();
    }

    private Boolean setupCoreConnection(ApplicationContext context) {

        // Set the site local identifier for this application
        uicdsCore.setLocalID(this.getClass().getName());
        logger.info("localId=" + this.getClass().getName());

        return uicdsCore.initialize();
    }

    private void run() {

        long sleepDurationInMilliseconds = sleepDurationInSeconds * 1000;
        
        
        

        // Process core notifications and poll WebEOC boards forever
        while (true) {
            // webeoc.pollBoards
            logger.debug("Polling boards");
            webEOCEventProducer.pollBoards();

            // core.processNotifications
            logger.debug("Processing Notifications");
            uicdsCore.processNotifications();

            showMemoryStats();

            try {
                Thread.sleep(sleepDurationInMilliseconds);
            } catch (InterruptedException e) {
                logger.error("Exception caught while sleeping: " + e.getMessage());
            }
        }
    }

    private void showMemoryStats() {

        StringBuffer sb = new StringBuffer();
        sb.append(runtime.freeMemory());
        sb.append(":");
        sb.append(runtime.totalMemory());
        sb.append(":");
        sb.append(runtime.maxMemory());
        logger.debug(sb.toString());
    }

    private static ApplicationContext getApplicationContext() {

        ApplicationContext context = null;
        try {
            context = new FileSystemXmlApplicationContext("./" + APP_CONTEXT_FILE);
            System.out.println("Using local application context file: " + APP_CONTEXT_FILE);
        } catch (BeansException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                System.out.println("Local application context file not found so using file from jar: contexts/"
                    + APP_CONTEXT_FILE);
            } else {
                System.out.println("Error reading local file context: " + e.getCause().getMessage());
            }
        }

        if (context == null) {
            context = new ClassPathXmlApplicationContext(new String[] { "contexts/"
                + APP_CONTEXT_FILE });
        }

        return context;
    }

    private static void usage() {

        System.out.println("");
        System.out.println("This is the UICDS WebEOC Adapter.");
        System.out.println("Execution of this client depends on a functioning UICDS server. The default is http://localhost/uicds/core/ws/services");
        System.out.println("To verify that a UICDS server is accessible, use a browser to navigate to http://localhost/uicds/core/ws/services/ProfileService.wsdl\"");
        System.out.println("");
        System.out.println("Usage: java -jar WebEOCAdapter.jar");
        System.out.println("");
        System.out.println("Parameters for the WebEOCAdapter can be configued in Spring context file");
        System.out.println("in the current directory named: " + APP_CONTEXT_FILE);
    }
}
