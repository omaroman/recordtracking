/**
 * Author: OMAROMAN
 * Date: 11/10/11
 * Time: 12:12 PM
 */

package controllers;

import play.Logger;
import play.Play;

public class Security extends Secure.Security {

    public static boolean check(String profile) {
//        return profile.equals("admin") && session.get("username").equals("admin");
        return true;
    }

    public static boolean authenticate(String username, String password) {
//        return Play.configuration.getProperty("application.admin").equals(username) && Play.configuration.getProperty("application.adminpwd").equals(password);
        return true;
    }

//    public static void onAuthenticated() {
//        try {
//            Logger.debug("AUTHENTICATED");
//        } catch (Throwable throwable) {
//            throwable.printStackTrace();
//        }
//    }
//
//    public static void onDisconnected() {
//        flash.success("secure.signout");
//    }
}
