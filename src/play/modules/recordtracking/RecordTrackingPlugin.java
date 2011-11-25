/**
 * Author: OMAROMAN
 * Date: 10/28/11
 * Time: 1:23 PM
 */

package play.modules.recordtracking;

import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses;
import play.db.jpa.GenericModel;
import play.db.jpa.JPABase;
import play.exceptions.ConfigurationException;
import play.modules.recordtracking.interfaces.Trackable;

import java.io.File;

public class RecordTrackingPlugin extends PlayPlugin {

    @Override
	public void enhance(ApplicationClasses.ApplicationClass appClass) throws Exception {
		new RecordTrackingEnhancer().enhanceThisClass(appClass);
	}

    @Override
    public void onConfigurationRead() {
        File f = new File(RecordTrackingProps.DEFAULT_XML_CONF_LOGGER_PATH);
        if ( f.exists() ) {
            // So far so good
        } else {
            f = new File(RecordTrackingProps.DEFAULT_PROPS_CONF_LOGGER_PATH);
            if ( f.exists() ) {
                // So far so good
            } else {
                String error = String.format("There's no %s or %s or logger config file", RecordTrackingProps.DEFAULT_XML_CONF_LOGGER_PATH, RecordTrackingProps.DEFAULT_PROPS_CONF_LOGGER_PATH);
                throw new ConfigurationException(error);
            }
        }

//        RecordTrackingProps.loggerConf = Play.configuration.getProperty(RecordTrackingProps.CONFIG_PREFIX + "loggerConf", RecordTrackingProps.DEFAULT_XML_CONF_LOGGER_PATH); // "recordtracking.xml"
        RecordTrackingProps.logMasked = Boolean.parseBoolean(Play.configuration.getProperty(RecordTrackingProps.CONFIG_PREFIX + "logMasked", "false"));
        RecordTrackingProps.sessionKey = Play.configuration.getProperty(RecordTrackingProps.CONFIG_PREFIX + "sessionKey", "username"); //maybe email
        RecordTrackingLogger.getInstance();
    }

//    @Override
    public void onEvent_Tmp(String message, Object context) {
        // "JPASupport.objectPersisted"
        // "JPASupport.objectDeleted"

        if ("JPASupport.objectPreDeleted".equals(message)) {
            play.Logger.debug("\nEVENT: JPASupport.objectPreDeleted");
            if (context instanceof Trackable) {
                ((Trackable)context)._fill_track_data();
            }
        }

        if ("JPASupport.objectDeleted".equals(message)) {
            play.Logger.debug("\nEVENT: JPASupport.objectDeleted");
            if (context instanceof Trackable) {
                play.Logger.info(((Trackable)context).formatRecordTracking("OBJECT DELETED"));
            }
        }

    }

}
