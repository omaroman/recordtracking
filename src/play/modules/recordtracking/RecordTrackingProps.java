/**
 * Author: OMAROMAN
 * Date: 11/10/11
 * Time: 4:09 PM
 */

package play.modules.recordtracking;

public class RecordTrackingProps {

    final static String CONFIG_PREFIX = "recordtracking.";
    final static String DEFAULT_XML_CONF_LOGGER_PATH = "conf/log4j.xml";
    final static String DEFAULT_PROPS_CONF_LOGGER_PATH = "conf/log4j.properties";

//    static String loggerConf = null;

    static String sessionKey = null;
    static boolean logMasked = false;


    public static String getSessionKey() {
        return sessionKey;
    }

    public boolean shouldLogMasked() {
        return logMasked;
    }
}
