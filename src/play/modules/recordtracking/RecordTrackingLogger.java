/**
 * Author: OMAROMAN
 * Date: 11/10/11
 * Time: 3:54 PM
 */
package play.modules.recordtracking;

import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.DOMConfigurator;
import play.exceptions.ConfigurationException;

import javax.xml.parsers.FactoryConfigurationError;
import java.io.File;
import java.io.FileNotFoundException;

public class RecordTrackingLogger {

    private org.apache.log4j.Logger logger;
    private final String LOGGER_NAME = "recordtracking";

    // For Singleton pattern
	private volatile static RecordTrackingLogger uniqueInstance;

    /**
	 * Private Constructor for Singleton. Initializes properties and collections
	 */
	private RecordTrackingLogger() {

        File f = new File(RecordTrackingProps.DEFAULT_XML_CONF_LOGGER_PATH);
        if ( f.exists() ) {
            DOMConfigurator.configure(RecordTrackingProps.DEFAULT_XML_CONF_LOGGER_PATH);
        } else {
            f = new File(RecordTrackingProps.DEFAULT_PROPS_CONF_LOGGER_PATH);
            if ( f.exists() ) {
                PropertyConfigurator.configure(RecordTrackingProps.DEFAULT_PROPS_CONF_LOGGER_PATH);
            } else {
                // Shall never rich this point, since it's checked at RecordTrackingPlugin.onConfigurationRead()
                String error = String.format("There's no %s or %s or logger config file", RecordTrackingProps.DEFAULT_XML_CONF_LOGGER_PATH, RecordTrackingProps.DEFAULT_PROPS_CONF_LOGGER_PATH);
                throw new ConfigurationException(error);
            }
        }

//        String error = String.format("There's no %s or %s or logger [%s] not configured properly", RecordTrackingProps.DEFAULT_XML_CONF_LOGGER_PATH, RecordTrackingProps.DEFAULT_PROPS_CONF_LOGGER_PATH);
        logger = org.apache.log4j.Logger.getLogger(LOGGER_NAME);
    }

    /**
	 * Method for get a unique instance of this class (Singleton Pattern)
	 * @return - a unique instance of Logger
	 */
	public static RecordTrackingLogger getInstance() {
        if (uniqueInstance == null) {
			synchronized (RecordTrackingLogger.class) {
				if (uniqueInstance == null) {
					uniqueInstance = new RecordTrackingLogger();
				}
			}
		}
		return uniqueInstance;
	}


    public org.apache.log4j.Logger getLogger() {
        return logger;
    }

 }

